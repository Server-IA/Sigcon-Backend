package com.sigcon.backend.invoices.ap_reconciliation.domain.service;

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

    /**
     * Lista los pagos AP pendientes de conciliar con BNK.
     *
     * @return lista de pagos sin {@code bankMovementId}
     */
    public ResponseEntity<?> listUnreconciled() {
        List<ApPayment> unreconciled = new ArrayList<>();
        for (ApPayment p : paymentRepository.findAll()) {
            if (p.getDeletedAt() == null && p.getBankMovementId() == null) {
                unreconciled.add(p);
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

        payment.setBankMovementId(movementId);
        payment.setReconciledAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("AP-09: Pago {} conciliado con movimiento BNK {}", paymentId, movementId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Pago conciliado correctamente con movimiento bancario"),
                Optional.of(payment)));
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

        payment.setBankMovementId(null);
        payment.setReconciledAt(null);
        paymentRepository.save(payment);

        log.info("AP-09: Conciliacion de pago {} revertida", paymentId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Conciliacion revertida"), Optional.of(payment)));
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
