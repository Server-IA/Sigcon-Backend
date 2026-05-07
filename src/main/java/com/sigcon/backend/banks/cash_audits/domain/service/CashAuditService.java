package com.sigcon.backend.banks.cash_audits.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.cash_audits.application.ApproveCashAuditRequest;
import com.sigcon.backend.banks.cash_audits.application.CashAuditDTO;
import com.sigcon.backend.banks.cash_audits.application.CreateCashAuditRequest;
import com.sigcon.backend.banks.cash_audits.application.DeleteCashAuditRequest;
import com.sigcon.backend.banks.cash_audits.application.VoidCashAuditRequest;
import com.sigcon.backend.banks.cash_audits.domain.model.CashAudit;
import com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus;
import com.sigcon.backend.banks.cash_audits.domain.repository.CashAuditRepository;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashStatus;
import com.sigcon.backend.banks.cash_management.domain.repository.CashRepository;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de negocio para arqueos de caja (BNK-RF-17 a BNK-RF-20).
 * Gestiona la creacion, aprobacion y cierre de arqueos, incluyendo
 * la generacion automatica de asientos contables de ajuste cuando
 * se detectan diferencias entre el saldo fisico y el del sistema.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CashAuditService {

    private final CashAuditRepository cashAuditRepository;
    private final CashRepository cashRepository;
    private final JournalEntryService journalEntryService;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;
    private final DataTableSpecificationBuilder<CashAudit> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Consulta paginada de arqueos de caja (DataTable pattern).
     *
     * @param request parametros de paginacion y filtros
     * @return respuesta paginada con DTOs de arqueos
     */
    public ResponseEntity<?> search(DataTableRequest request) {
        if (request == null) {
            request = new DataTableRequest();
        }
        int length = request.getLength() > 0 ? request.getLength() : 10;
        int page = request.getStart() / length;
        int size = length;

        Pageable pageable = PageRequest.of(page, size);
        Specification<CashAudit> spec = specBuilder.build(request);
        Page<CashAuditDTO> data = cashAuditRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Crea un nuevo arqueo de caja.
     * Calcula el saldo del sistema desde la caja y determina la diferencia.
     *
     * @param request datos del arqueo a registrar
     * @return respuesta con el arqueo creado
     */
    @Transactional
    public ResponseEntity<?> create(CreateCashAuditRequest request) {
        // 1. Buscar la caja
        Optional<Cash> optCash = cashRepository.findByIdAndDeletedAtIsNull(request.getCashId());
        if (optCash.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La caja no fue encontrada.")));
        }

        Cash cash = optCash.get();

        // 2. Validar que la caja este activa
        if (cash.getCashStatus() != CashStatus.ACTIVE) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La caja no esta activa.")));
        }

        // 3. Obtener saldo del sistema y calcular diferencia
        BigDecimal systemBalance = cash.getCurrentBalance() != null ? cash.getCurrentBalance() : BigDecimal.ZERO;
        BigDecimal difference = request.getPhysicalBalance().subtract(systemBalance);

        // 4. Construir y guardar el arqueo
        CashAudit audit = CashAudit.builder()
                .cash(cash)
                .auditDate(request.getAuditDate())
                .systemBalance(systemBalance)
                .physicalBalance(request.getPhysicalBalance())
                .difference(difference)
                .status(CashAuditStatus.BORRADOR)
                .notes(request.getNotes())
                .build();

        CashAudit saved = cashAuditRepository.save(audit);
        log.info("Arqueo de caja creado: id={}, caja={}, diferencia={}", saved.getId(), cash.getCashCode(), difference);

        auditPublisher.publishCreate(AuditModule.BNK, "CashAudit", saved.getId(),
                "Arqueo de caja registrado: caja=" + cash.getCashCode()
                        + " diferencia=" + difference);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Arqueo de caja registrado exitosamente."),
                        Optional.of(toDTO(saved))));
    }

    /**
     * Aprueba un arqueo de caja.
     * Si existe diferencia entre saldo fisico y del sistema, se genera un
     * asiento contable de ajuste via JournalEntryService.
     *
     * @param id      identificador del arqueo
     * @param request datos opcionales de aprobacion (notas del supervisor)
     * @return respuesta con el arqueo aprobado
     */
    @Transactional
    public ResponseEntity<?> approve(Long id, ApproveCashAuditRequest request) {
        // 1. Buscar el arqueo
        Optional<CashAudit> optAudit = cashAuditRepository.findById(id);
        if (optAudit.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El arqueo no fue encontrado.")));
        }

        CashAudit audit = optAudit.get();

        // 2. Validar estado BORRADOR (acepta ABIERTO legacy y EN_REVISION)
        CashAuditStatus current = audit.getStatus();
        if (current != CashAuditStatus.BORRADOR
                && current != CashAuditStatus.EN_REVISION
                && current != CashAuditStatus.ABIERTO) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("El arqueo debe estar en estado BORRADOR o EN_REVISION para ser aprobado.")));
        }

        // 3. Aprobar
        audit.setStatus(CashAuditStatus.APROBADO);
        audit.setApprovedAt(LocalDateTime.now());
        if (request != null && request.getNotes() != null) {
            audit.setNotes(request.getNotes());
        }

        // 4. Si hay diferencia, generar asiento contable de ajuste (partida doble).
        //    - Caja: cuenta de la caja auditada (fallback a CAJA_DEFAULT si no configurada)
        //    - Contrapartida: gasto no operacional por diferencia en cambio (sobrante = ingreso,
        //      faltante = gasto). Se usan DIF_CAMBIO_INGRESO/DIF_CAMBIO_GASTO como conceptos
        //      conservadores; en sistemas mas complejos se modelaria como "Otros ingresos/egresos".
        if (audit.getDifference().compareTo(BigDecimal.ZERO) != 0) {
            try {
                Long cajaAccountId = audit.getCash().getAccountingAccount() != null
                        ? audit.getCash().getAccountingAccount().getId()
                        : accountMappingService.resolveOrThrow(AccountingConcept.CAJA_DEFAULT);

                BigDecimal absDiff = audit.getDifference().abs();
                boolean isPositive = audit.getDifference().compareTo(BigDecimal.ZERO) > 0;

                // Contrapartida: sobrante -> ingreso (credito); faltante -> gasto (debito)
                Long contraAccountId = isPositive
                        ? accountMappingService.resolveOrThrow(AccountingConcept.DIF_CAMBIO_INGRESO)
                        : accountMappingService.resolveOrThrow(AccountingConcept.DIF_CAMBIO_GASTO);

                CreateJournalEntryLineRequest cajaLine = CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(cajaAccountId)
                        .debitAmount(isPositive ? absDiff : BigDecimal.ZERO)
                        .creditAmount(isPositive ? BigDecimal.ZERO : absDiff)
                        .description("Ajuste caja por arqueo #" + audit.getId())
                        .build();

                CreateJournalEntryLineRequest contraLine = CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(contraAccountId)
                        .debitAmount(isPositive ? BigDecimal.ZERO : absDiff)
                        .creditAmount(isPositive ? absDiff : BigDecimal.ZERO)
                        .description((isPositive ? "Sobrante" : "Faltante")
                                + " por arqueo #" + audit.getId())
                        .build();

                CreateJournalEntryRequest entryRequest = CreateJournalEntryRequest.builder()
                        .entryDate(LocalDate.now())
                        .description("Asiento de ajuste - Arqueo de caja #" + audit.getId()
                                + " - Caja: " + audit.getCash().getCashCode())
                        .sourceModule(JournalSourceModule.BNK)
                        .sourceId(audit.getId())
                        .lines(List.of(cajaLine, contraLine))
                        .build();

                JournalEntryDTO journalEntry = journalEntryService.createEntry(entryRequest, "SYSTEM");
                audit.setJournalEntryId(journalEntry.getId());
                log.info("Asiento contable generado para arqueo id={}: journalEntryId={}",
                        audit.getId(), journalEntry.getId());
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.error("Error generando asiento contable para arqueo id={}: {}",
                        audit.getId(), e.getMessage());
                throw new IllegalStateException(
                        "No se pudo aprobar el arqueo: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                log.error("Error inesperado generando asiento para arqueo id={}", audit.getId(), e);
                throw e;
            }
        }

        // QA Bloque AU (2026-05-06) — Bug 2: el arqueo APROBADO debe
        // ajustar el saldo fisico de la caja a `physicalBalance` (lo
        // contado realmente). El JE de ajuste contable (lineas arriba) ya
        // refleja la diferencia en libros; aqui actualizamos la entidad
        // Cash para que el balance del modulo BNK tambien quede consistente.
        // Si no se hace, la caja sigue mostrando $50.000 cuando el arqueo
        // dijo que solo hay $35.000 fisicos.
        try {
            com.sigcon.backend.banks.cash_management.domain.model.Cash cash = audit.getCash();
            if (cash != null && audit.getPhysicalBalance() != null) {
                cash.setCurrentBalance(audit.getPhysicalBalance());
                cashRepository.save(cash);
                log.info("Saldo de caja actualizado por arqueo: cashId={} nuevoSaldo={}",
                        cash.getId(), audit.getPhysicalBalance());
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo actualizar el saldo de la caja tras aprobar arqueo {}: {}",
                    audit.getId(), ex.getMessage());
        }

        CashAudit saved = cashAuditRepository.save(audit);
        log.info("Arqueo de caja aprobado: id={}", saved.getId());

        auditPublisher.publishUpdate(AuditModule.BNK, "CashAudit", saved.getId(),
                "Arqueo de caja aprobado");

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Arqueo de caja aprobado exitosamente."),
                        Optional.of(toDTO(saved))));
    }

    /**
     * Cierra un arqueo de caja previamente aprobado.
     *
     * @param id identificador del arqueo
     * @return respuesta con el arqueo cerrado
     */
    @Transactional
    public ResponseEntity<?> close(Long id) {
        Optional<CashAudit> optAudit = cashAuditRepository.findById(id);
        if (optAudit.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El arqueo no fue encontrado.")));
        }

        CashAudit audit = optAudit.get();

        if (audit.getStatus() != CashAuditStatus.APROBADO) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("El arqueo debe estar APROBADO para ser cerrado.")));
        }

        // HU-BNK-047 E4: APROBADO ya es estado terminal inmutable. CERRADO se mantiene
        // como sinonimo legacy para no romper UI existente.
        audit.setStatus(CashAuditStatus.CERRADO);
        CashAudit saved = cashAuditRepository.save(audit);
        log.info("Arqueo de caja cerrado: id={}", saved.getId());

        auditPublisher.publishUpdate(AuditModule.BNK, "CashAudit", saved.getId(),
                "Arqueo de caja cerrado");

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Arqueo de caja cerrado exitosamente."),
                        Optional.of(toDTO(saved))));
    }

    /**
     * HU-BNK-048 E1 - Elimina fisicamente un arqueo en BORRADOR.
     * Bloquea si:
     *   - el arqueo no esta en BORRADOR (E3 mensaje exacto del Excel)
     *   - tiene asiento contable asociado (no deberia ocurrir en BORRADOR pero defensivo)
     *
     * @param id      identificador del arqueo
     * @param request motivo de eliminacion (min 10 chars, registrado en auditoria)
     * @return 200 si se elimino, 400 si esta en estado no permitido
     */
    @Transactional
    public ResponseEntity<?> delete(Long id, DeleteCashAuditRequest request) {
        Optional<CashAudit> optAudit = cashAuditRepository.findById(id);
        if (optAudit.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El arqueo no fue encontrado.")));
        }

        CashAudit audit = optAudit.get();
        CashAuditStatus current = audit.getStatus();

        // HU-BNK-048 E3: bloqueo eliminar APROBADO/CERRADO con mensaje exacto del Excel
        if (current == CashAuditStatus.APROBADO || current == CashAuditStatus.CERRADO) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Los arqueos aprobados no pueden eliminarse fisicamente. "
                                    + "Use la funcion Anular Arqueo para registrar la anulacion con motivo "
                                    + "y conservar el historial (Decreto 2649/1993 Art. 57)")));
        }

        if (current != CashAuditStatus.BORRADOR
                && current != CashAuditStatus.ABIERTO
                && current != CashAuditStatus.EN_REVISION
                && current != CashAuditStatus.RECHAZADO) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Solo se pueden eliminar arqueos en estado BORRADOR.")));
        }

        if (audit.getJournalEntryId() != null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "El arqueo tiene asiento contable asociado y no puede eliminarse. Use Anular Arqueo.")));
        }

        Long auditId = audit.getId();
        String cashCode = audit.getCash() != null ? audit.getCash().getCashCode() : null;
        // Soft delete via @SQLDelete (cumple HU-BNK-048 E1: registro auditable, no se reusan IDs)
        cashAuditRepository.delete(audit);

        auditPublisher.publishDelete(AuditModule.BNK, "CashAudit", auditId,
                "Arqueo BORRADOR eliminado (caja=" + cashCode + ", motivo=" + request.getReason() + ")");
        log.info("Arqueo BORRADOR eliminado: id={}, motivo={}", auditId, request.getReason());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Arqueo eliminado correctamente."), Optional.empty()));
    }

    /**
     * HU-BNK-048 E2 - Anula logicamente un arqueo APROBADO conservando el historial.
     * El arqueo no se elimina; cambia a estado ANULADO con motivo (min 50 chars).
     * Los movimientos financieros y el asiento contable existente NO se modifican.
     *
     * @param id      identificador del arqueo
     * @param request motivo de anulacion (min 50 chars)
     * @return 200 si se anulo, 400 si esta en estado no permitido
     */
    @Transactional
    public ResponseEntity<?> voidAudit(Long id, VoidCashAuditRequest request) {
        Optional<CashAudit> optAudit = cashAuditRepository.findById(id);
        if (optAudit.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El arqueo no fue encontrado.")));
        }

        CashAudit audit = optAudit.get();
        CashAuditStatus current = audit.getStatus();

        if (current != CashAuditStatus.APROBADO && current != CashAuditStatus.CERRADO) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Solo se pueden anular arqueos en estado APROBADO.")));
        }

        if (current == CashAuditStatus.ANULADO) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "El arqueo ya esta anulado.")));
        }

        audit.setStatus(CashAuditStatus.ANULADO);
        audit.setVoidReason(request.getReason());
        audit.setVoidedAt(LocalDateTime.now());

        CashAudit saved = cashAuditRepository.save(audit);
        auditPublisher.publishUpdate(AuditModule.BNK, "CashAudit", saved.getId(),
                "Arqueo APROBADO anulado (motivo=" + request.getReason() + ")");
        log.info("Arqueo anulado: id={}, motivo={}", saved.getId(), request.getReason());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Arqueo anulado correctamente. El historial se conserva."),
                Optional.of(toDTO(saved))));
    }

    /**
     * HU-042: cajero envia arqueo BORRADOR/RECHAZADO al supervisor para revision.
     * Cambia estado a EN_REVISION (lock para edicion del cajero).
     */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> submitReview(Long id) {
        Optional<CashAudit> opt = cashAuditRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El arqueo no fue encontrado.")));
        }
        CashAudit audit = opt.get();
        com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus s = audit.getStatus();
        boolean isDraftLike = s == com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus.BORRADOR
                || s == com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus.ABIERTO
                || s == com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus.RECHAZADO;
        if (!isDraftLike) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("Solo se pueden enviar a revision arqueos en BORRADOR o RECHAZADO. Estado actual: " + s + ".")));
        }
        audit.setStatus(com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus.EN_REVISION);
        cashAuditRepository.save(audit);
        auditPublisher.publishUpdate(AuditModule.BNK, "CashAudit", audit.getId(),
                "Arqueo enviado a revision id=" + audit.getId());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Arqueo enviado a revision."), Optional.of(toDTO(audit))));
    }

    /**
     * HU-043: supervisor rechaza arqueo EN_REVISION con motivo (>=10 chars).
     * Vuelve a BORRADOR y se persiste el motivo en notes para que el cajero lo vea.
     */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> reject(Long id, String reason) {
        if (reason == null || reason.trim().length() < 10) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El motivo del rechazo es obligatorio (minimo 10 caracteres).")));
        }
        Optional<CashAudit> opt = cashAuditRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El arqueo no fue encontrado.")));
        }
        CashAudit audit = opt.get();
        if (audit.getStatus() != com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus.EN_REVISION) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("Solo se pueden rechazar arqueos EN_REVISION. Estado actual: " + audit.getStatus() + ".")));
        }
        audit.setStatus(com.sigcon.backend.banks.cash_audits.domain.model.enums.CashAuditStatus.RECHAZADO);
        // Persistir motivo del rechazo apendiendo a notes (si existe campo)
        try {
            String prev = audit.getNotes() != null ? audit.getNotes() + "\n\n" : "";
            audit.setNotes(prev + "[RECHAZO " + java.time.LocalDateTime.now() + "] " + reason.trim());
        } catch (Exception ignored) {
            // Si la entidad no tiene notes, ignoramos — el motivo queda solo en audit log
        }
        cashAuditRepository.save(audit);
        auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.UPDATE,
                AuditModule.BNK,
                com.sigcon.backend.audit.domain.model.enums.AuditSeverity.HIGH,
                "CashAudit", audit.getId(),
                "Arqueo rechazado id=" + audit.getId() + " motivo: " + reason.trim(),
                null, null, null);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Arqueo rechazado. El cajero debe corregir y reenviar."),
                Optional.of(toDTO(audit))));
    }

    /**
     * Obtiene el detalle de un arqueo de caja por su ID.
     *
     * @param id identificador del arqueo
     * @return respuesta con el DTO del arqueo
     */
    public ResponseEntity<?> getById(Long id) {
        Optional<CashAudit> optAudit = cashAuditRepository.findById(id);
        if (optAudit.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El arqueo no fue encontrado.")));
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Arqueo encontrado."),
                        Optional.of(toDTO(optAudit.get()))));
    }

    /**
     * Convierte una entidad CashAudit a su DTO de respuesta.
     *
     * @param audit entidad a convertir
     * @return DTO con datos del arqueo y de la caja asociada
     */
    private CashAuditDTO toDTO(CashAudit audit) {
        // Defensivo: si la caja fue desactivada (deleted_at != null) Hibernate
        // retorna null por @Where. Mostramos placeholder para no romper el listado.
        Cash cash = null;
        try {
            cash = audit.getCash();
            if (cash != null) {
                cash.getId();
            }
        } catch (org.hibernate.LazyInitializationException | jakarta.persistence.EntityNotFoundException e) {
            cash = null;
        }
        Long cashId = cash != null ? cash.getId() : null;
        String cashCode = cash != null ? cash.getCashCode() : "(caja eliminada)";
        String cashName = cash != null ? cash.getCashName() : "(caja eliminada)";
        return CashAuditDTO.builder()
                .id(audit.getId())
                .cashId(cashId)
                .cashCode(cashCode)
                .cashName(cashName)
                .auditDate(audit.getAuditDate())
                .systemBalance(audit.getSystemBalance())
                .physicalBalance(audit.getPhysicalBalance())
                .difference(audit.getDifference())
                .status(audit.getStatus())
                .notes(audit.getNotes())
                .supervisorId(audit.getSupervisorId())
                .approvedAt(audit.getApprovedAt())
                .approvedBy(audit.getApprovedBy())
                .journalEntryId(audit.getJournalEntryId())
                .voidReason(audit.getVoidReason())
                .voidedAt(audit.getVoidedAt())
                .voidedBy(audit.getVoidedBy())
                .createdBy(audit.getCreatedBy())
                .createdAt(audit.getCreatedAt())
                .updatedAt(audit.getUpdatedAt())
                .build();
    }
}
