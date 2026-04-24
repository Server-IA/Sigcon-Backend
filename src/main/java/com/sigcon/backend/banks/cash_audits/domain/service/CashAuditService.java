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
                .status(CashAuditStatus.ABIERTO)
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

        // 2. Validar estado ABIERTO
        if (audit.getStatus() != CashAuditStatus.ABIERTO) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("El arqueo debe estar en estado ABIERTO para ser aprobado.")));
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
            } catch (Exception e) {
                log.warn("No se pudo generar asiento contable para arqueo id={}: {}",
                        audit.getId(), e.getMessage());
                // Se aprueba el arqueo aunque falle el asiento (se puede generar despues)
            }
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
        return CashAuditDTO.builder()
                .id(audit.getId())
                .cashId(audit.getCash() != null ? audit.getCash().getId() : null)
                .cashCode(audit.getCash() != null ? audit.getCash().getCashCode() : null)
                .cashName(audit.getCash() != null ? audit.getCash().getCashName() : null)
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
                .createdBy(audit.getCreatedBy())
                .createdAt(audit.getCreatedAt())
                .updatedAt(audit.getUpdatedAt())
                .build();
    }
}
