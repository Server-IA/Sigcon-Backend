package com.sigcon.backend.invoices.ap_reconciliation.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.invoices.ap_payments.domain.model.ApPayment;
import com.sigcon.backend.invoices.ap_payments.domain.repository.ApPaymentRepository;
import com.sigcon.backend.invoices.ap_reconciliation.application.ApReconciliationCandidateDTO;
import com.sigcon.backend.invoices.ap_reconciliation.application.ApReconciliationCandidateDTO.BankMovementCandidate;
import com.sigcon.backend.utils.SuccessRespondJson;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AP-09: Servicio de conciliacion entre pagos de cuentas por pagar (ApPayment)
 * y movimientos financieros bancarios (FinancialMovement).
 *
 * <p>Permite el <b>three-way match</b>:
 * {@code ApPayment ↔ FinancialMovement (BNK) ↔ (opcionalmente) GoodsReceipt}
 * donde el enlace OC→Recepcion→Factura ya existia en el modulo AP (HU-AP-20).
 *
 * <p>Caracteristicas:
 * <ul>
 *   <li>Listar pagos pendientes de conciliar (bank_movement_id IS NULL)</li>
 *   <li>Sugerir candidatos BNK por match de fecha (+/- 7 dias), monto (exacto o +/- 1%)
 *       y cuenta bancaria</li>
 *   <li>Enlazar manualmente un pago con un movimiento</li>
 *   <li>Deshacer enlace (unreconcile)</li>
 * </ul>
 *
 * <p>Ventana de busqueda de fecha: +/- {@code DATE_WINDOW_DAYS} dias (default 7).
 * Tolerancia de monto: 1% del monto del pago.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApReconciliationService {

    /** Ventana (en dias) para sugerir candidatos por cercania de fecha. */
    private static final long DATE_WINDOW_DAYS = 7L;

    /** Tolerancia relativa para match de monto (1%). */
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final ApPaymentRepository paymentRepository;
    private final FinancialMovementRepository movementRepository;
    private final AuditPublisher auditPublisher;

    /**
     * Lista los pagos AP pendientes de conciliar con BNK.
     *
     * @return lista de pagos sin {@code bankMovementId}
     */
    public ResponseEntity<?> listUnreconciled() {
        // QA 2026-05-05: Jackson serializaba las entidades ApPayment con sus
        // relaciones LAZY (Invoice, ThirdParty), generando errores
        // "Type definition error: ByteBuddyInterceptor". Mapear a un DTO plano
        // con solo los campos necesarios para el listado de conciliacion.
        List<java.util.Map<String, Object>> unreconciled = new ArrayList<>();
        for (ApPayment p : paymentRepository.findAll()) {
            if (p.getDeletedAt() == null && p.getBankMovementId() == null) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", p.getId());
                row.put("invoiceId", p.getInvoice() != null ? p.getInvoice().getId() : null);
                row.put("invoiceNumber", p.getInvoice() != null ? p.getInvoice().getResolutionInvoice() : null);
                row.put("supplierName", p.getInvoice() != null && p.getInvoice().getThirdParty() != null
                        ? p.getInvoice().getThirdParty().getBusinessName() : null);
                row.put("amount", p.getAmount());
                row.put("paymentDate", p.getPaymentDate());
                row.put("paymentReference", p.getPaymentReference());
                row.put("paymentMethod", p.getPaymentMethod());
                row.put("bankAccountId", p.getBankAccountId());
                row.put("notes", p.getNotes());
                unreconciled.add(row);
            }
        }
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Pagos AP pendientes de conciliar"), Optional.of(unreconciled)));
    }

    /**
     * Sugiere candidatos de movimientos financieros BNK que pueden corresponder
     * a un pago AP dado, segun proximidad de fecha, monto y cuenta bancaria.
     *
     * @param paymentId ID del pago AP
     * @return DTO con lista ordenada de candidatos por score descendente
     */
    public ResponseEntity<?> suggestCandidates(Long paymentId) {
        ApPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pago AP no encontrado: " + paymentId));

        if (payment.getBankMovementId() != null) {
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("El pago ya esta conciliado"),
                    Optional.of(buildCandidateDTO(payment, List.of(), "ALREADY_RECONCILED"))));
        }

        List<BankMovementCandidate> candidates = findCandidatesFor(payment);

        String status;
        if (candidates.isEmpty())                          status = "NO_CANDIDATES";
        else if (candidates.get(0).getMatchScore() >= 0.99) status = "MATCH_EXACT";
        else                                               status = "MATCH_APPROX";

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Candidatos de conciliacion"),
                Optional.of(buildCandidateDTO(payment, candidates, status))));
    }

    /**
     * Enlaza manualmente un pago AP con un movimiento financiero BNK.
     *
     * @param paymentId  ID del pago AP
     * @param movementId ID del movimiento BNK
     * @return respuesta con el pago actualizado
     */
    @Transactional
    public ResponseEntity<?> linkPaymentToMovement(Long paymentId, Long movementId) {
        ApPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pago AP no encontrado: " + paymentId));

        if (payment.getBankMovementId() != null) {
            throw new IllegalStateException(
                    "El pago ya esta conciliado con el movimiento " + payment.getBankMovementId());
        }

        FinancialMovement movement = movementRepository.findById(movementId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento BNK no encontrado: " + movementId));

        // Validacion defensiva: la cuenta bancaria debe coincidir si ambas estan configuradas.
        if (payment.getBankAccountId() != null
                && movement.getBankAccount() != null
                && movement.getBankAccount().getId() != null
                && !payment.getBankAccountId().equals(movement.getBankAccount().getId())) {
            throw new IllegalArgumentException(
                    "La cuenta bancaria del pago AP no coincide con la del movimiento BNK.");
        }

        // QA-BLOQUE-AY HU-AP-08 E3 (2026-05-05): bloquear conciliacion cuando
        // el monto del pago AP NO coincide con el monto del movimiento BNK
        // (tolerancia $0.01 por redondeo). Antes el sistema permitia vincular
        // valores totalmente distintos, rompiendo la conciliacion.
        java.math.BigDecimal paymentAbs  = payment.getAmount() != null
                ? payment.getAmount().abs() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal movementAbs = movement.getAmount() != null
                ? movement.getAmount().abs() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal delta       = paymentAbs.subtract(movementAbs).abs();
        if (delta.compareTo(new java.math.BigDecimal("0.01")) > 0) {
            throw new IllegalArgumentException(
                    "El monto registrado en AP no coincide con el extracto bancario. "
                    + "AP: $" + paymentAbs + " - Extracto: $" + movementAbs
                    + " (diferencia: $" + delta + ")");
        }

        payment.setBankMovementId(movementId);
        payment.setReconciledAt(LocalDateTime.now());
        paymentRepository.save(payment);

        auditPublisher.publishUpdate(AuditModule.AP, "ApPaymentReconciliation", paymentId,
                "Pago AP #" + paymentId + " conciliado con movimiento BNK #" + movementId);
        log.info("AP-09: Pago {} conciliado con movimiento BNK {}", paymentId, movementId);

        // QA-BLOQUE-AY (2026-05-05): payload minimo para evitar Jackson + Hibernate
        // ByteBuddyInterceptor al serializar la entidad managed con relaciones LAZY.
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("bankMovementId", payment.getBankMovementId());
        payload.put("reconciledAt", payment.getReconciledAt() != null ? payment.getReconciledAt().toString() : null);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Pago conciliado correctamente con movimiento bancario"),
                Optional.of(payload)));
    }

    /**
     * Deshace la conciliacion previa de un pago AP.
     *
     * @param paymentId ID del pago AP
     * @return respuesta con el pago desconciliado
     */
    @Transactional
    public ResponseEntity<?> unlinkPayment(Long paymentId) {
        ApPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Pago AP no encontrado: " + paymentId));

        if (payment.getBankMovementId() == null) {
            throw new IllegalStateException("El pago no esta conciliado");
        }

        // RF-08 (Notas Tecnicas CXP): capturar el bankMovementId ANTES de
        // nulificarlo para que la auditoria registre tanto el pago como el
        // movimiento bancario desvinculado.
        Long unlinkedMovementId = payment.getBankMovementId();

        payment.setBankMovementId(null);
        payment.setReconciledAt(null);
        paymentRepository.save(payment);

        auditPublisher.publishDelete(AuditModule.AP, "ApPaymentReconciliation", paymentId,
                "Conciliacion del pago AP #" + paymentId + " revertida (movimiento BNK #"
                        + unlinkedMovementId + " desvinculado)");
        log.info("AP-09: Conciliacion de pago {} revertida", paymentId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Conciliacion revertida"), Optional.of(payment)));
    }

    /**
     * HU-AP-08 (Bloque AS): conciliacion automatica masiva. Para cada pago AP
     * sin conciliar de una cuenta bancaria, busca candidatos en BNK con score
     * exacto (>=0.99: monto + fecha coinciden). Si hay UN unico candidato exacto
     * y aun no esta tomado por otro pago, lo concilia automaticamente. Si hay
     * cero o multiples (ambiguos), los deja para revision manual.
     *
     * @param bankAccountId ID de cuenta bancaria a procesar (opcional, null = todas las del tenant)
     * @return resumen: evaluated/matched/skipped/matches[]
     */
    @Transactional
    public ResponseEntity<?> autoReconcileBankAccount(Long bankAccountId) {
        java.util.List<ApPayment> unreconciled = paymentRepository
                .findByBankMovementIdIsNullAndDeletedAtIsNull();
        if (bankAccountId != null) {
            unreconciled = unreconciled.stream()
                    .filter(p -> bankAccountId.equals(p.getBankAccountId()))
                    .collect(java.util.stream.Collectors.toList());
        }

        java.util.Set<Long> takenMovementIds = new java.util.HashSet<>();
        int matched = 0;
        java.util.List<java.util.Map<String, Object>> matches = new ArrayList<>();
        java.util.List<java.util.Map<String, Object>> skipped = new ArrayList<>();

        for (ApPayment payment : unreconciled) {
            List<BankMovementCandidate> candidates = findCandidatesFor(payment).stream()
                    .filter(c -> !takenMovementIds.contains(c.getMovementId()))
                    .filter(c -> c.getMatchScore() >= 0.99)
                    .collect(java.util.stream.Collectors.toList());

            if (candidates.size() == 1) {
                BankMovementCandidate c = candidates.get(0);
                payment.setBankMovementId(c.getMovementId());
                payment.setReconciledAt(LocalDateTime.now());
                paymentRepository.save(payment);
                takenMovementIds.add(c.getMovementId());
                matched++;
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("paymentId", payment.getId());
                m.put("movementId", c.getMovementId());
                m.put("score", c.getMatchScore());
                matches.add(m);
                auditPublisher.publishUpdate(AuditModule.AP, "ApPaymentReconciliation", payment.getId(),
                        "AUTO MATCH HU-AP-08: pago AP " + payment.getId() + " <-> mov BNK " + c.getMovementId() + " (score " + c.getMatchScore() + ")");
            } else {
                java.util.Map<String, Object> sk = new java.util.HashMap<>();
                sk.put("paymentId", payment.getId());
                sk.put("reason", candidates.isEmpty() ? "NO_CANDIDATES" : "AMBIGUOUS");
                sk.put("candidatesCount", candidates.size());
                skipped.add(sk);
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("evaluated", unreconciled.size());
        result.put("matched", matched);
        result.put("skippedCount", skipped.size());
        result.put("matches", matches);
        result.put("skipped", skipped);

        log.info("HU-AP-08 auto-reconcile: evaluated={} matched={} skipped={}",
                unreconciled.size(), matched, skipped.size());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Conciliacion automatica ejecutada: " + matched + " emparejados, "
                        + skipped.size() + " requieren revision manual"),
                Optional.of(result)));
    }

    // ============ helpers privados ============

    /** Busca movimientos financieros candidatos y calcula score. */
    private List<BankMovementCandidate> findCandidatesFor(ApPayment payment) {
        List<BankMovementCandidate> result = new ArrayList<>();
        if (payment.getPaymentDate() == null || payment.getAmount() == null) return result;

        LocalDate paymentDate = payment.getPaymentDate();
        BigDecimal paymentAmount = payment.getAmount();

        // Fuente de movimientos: si tenemos bankAccountId filtrar por ella; sino todos.
        Iterable<FinancialMovement> pool = (payment.getBankAccountId() != null)
                ? movementRepository.findAllByBankAccountIdOrdered(payment.getBankAccountId())
                : movementRepository.findAll();

        // FinancialMovement ya filtra deleted_at via @Where en la entidad
        for (FinancialMovement m : pool) {
            if (m.getMovementDate() == null || m.getAmount() == null) continue;

            // Filtro 1: fecha dentro de ventana
            long daysDiff = Math.abs(ChronoUnit.DAYS.between(m.getMovementDate(), paymentDate));
            if (daysDiff > DATE_WINDOW_DAYS) continue;

            // Filtro 2: monto dentro de tolerancia
            BigDecimal amountDelta = paymentAmount.subtract(m.getAmount().abs()).abs();
            BigDecimal tolerance = paymentAmount.multiply(AMOUNT_TOLERANCE);
            if (amountDelta.compareTo(tolerance) > 0) continue;

            // Score: 1.0 si fecha exacta + monto exacto, degradando con diferencia
            double dateScore = 1.0 - (double) daysDiff / (double) DATE_WINDOW_DAYS;
            double amountScore = paymentAmount.signum() == 0
                    ? 1.0
                    : 1.0 - amountDelta.doubleValue() / paymentAmount.doubleValue();
            double score = (dateScore * 0.5) + (amountScore * 0.5);

            result.add(BankMovementCandidate.builder()
                    .movementId(m.getId())
                    .movementDate(m.getMovementDate())
                    .amount(m.getAmount())
                    .externalReference(m.getExternalReference())
                    .description(m.getDescription())
                    .matchScore(Math.round(score * 1000.0) / 1000.0)
                    .build());
        }

        result.sort(Comparator.comparingDouble(BankMovementCandidate::getMatchScore).reversed());
        return result;
    }

    private ApReconciliationCandidateDTO buildCandidateDTO(ApPayment payment,
                                                           List<BankMovementCandidate> candidates,
                                                           String status) {
        return ApReconciliationCandidateDTO.builder()
                .apPaymentId(payment.getId())
                .invoiceId(payment.getInvoice() != null ? payment.getInvoice().getId() : null)
                .invoiceNumber(payment.getInvoice() != null
                        ? payment.getInvoice().getResolutionInvoice() : null)
                .apAmount(payment.getAmount())
                .apPaymentDate(payment.getPaymentDate())
                .apBankAccountId(payment.getBankAccountId())
                .apReference(payment.getPaymentReference())
                .candidates(candidates)
                .matchStatus(status)
                .build();
    }
}
