package com.sigcon.backend.accounts_receivable.advances.domain.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.accounts_receivable.advances.application.ApplyArAdvanceRequest;
import com.sigcon.backend.accounts_receivable.advances.application.ArAdvanceDTO;
import com.sigcon.backend.accounts_receivable.advances.application.CreateArAdvanceRequest;
import com.sigcon.backend.accounts_receivable.advances.domain.model.ArAdvance;
import com.sigcon.backend.accounts_receivable.advances.domain.repository.ArAdvanceRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestion de anticipos de clientes.
 * Cubre HU AR-09.
 * Permite registrar anticipos recibidos y aplicarlos parcial o totalmente
 * a facturas de venta. Genera asientos contables automaticos
 * (Debito Bancos / Credito Anticipos clientes al registrar,
 *  Debito Anticipos clientes / Credito CxC al aplicar).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArAdvanceService {

    private final ArAdvanceRepository advanceRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final JournalEntryService journalEntryService;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<ArAdvance> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Registra un nuevo anticipo recibido de un cliente.
     * Valida existencia del tercero, monto positivo y periodo abierto.
     * Genera asiento: Debito Bancos / Credito Anticipos clientes.
     *
     * @param request datos del anticipo a registrar
     * @return ResponseEntity con el anticipo creado
     * @throws IllegalArgumentException si el tercero no existe o el monto es invalido
     * @throws IllegalStateException    si el periodo esta cerrado
     */
    @Transactional
    public ResponseEntity<?> registerAdvance(CreateArAdvanceRequest request) {
        // 1. Validar tercero existe
        ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new IllegalArgumentException("El tercero no fue encontrado"));

        // 2. Validar monto positivo
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del anticipo debe ser mayor a cero");
        }

        // 3. Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(request.getAdvanceDate());

        // 4. Crear anticipo
        ArAdvance advance = ArAdvance.builder()
                .thirdParty(thirdParty)
                .amount(request.getAmount())
                .appliedAmount(BigDecimal.ZERO)
                .advanceDate(request.getAdvanceDate())
                .advanceReference(request.getAdvanceReference())
                .status("PENDING")
                .bankMovementId(request.getBankMovementId())
                .bankAccountId(request.getBankAccountId())
                .cashId(request.getCashId())
                .notes(request.getNotes())
                .build();

        advance = advanceRepository.save(advance);
        auditPublisher.publishCreate(AuditModule.AR, "ArAdvance", advance.getId(), "ArAdvance creado id=" + advance.getId());

        // 5. Generar asiento contable (Debito Bancos / Credito Anticipos clientes)
        try {
            // AR-09: Debito Bancos (PUC 1110) / Credito Anticipos recibidos (PUC 2805)
            Long debitAccountId = accountMappingService.resolveOrThrow(AccountingConcept.BANCOS_DEFAULT);
            Long creditAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AR_ANTICIPOS);

            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                    .entryDate(request.getAdvanceDate())
                    .description("Anticipo recibido de cliente " + thirdParty.getBusinessName())
                    .sourceModule(JournalSourceModule.AR)
                    .sourceId(advance.getId())
                    .lines(List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(debitAccountId)
                                    .debitAmount(request.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .description("Ingreso anticipo " + thirdParty.getBusinessName())
                                    .thirdPartyNit(thirdParty.getNit())
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(creditAccountId)
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(request.getAmount())
                                    .description("Anticipo cliente " + thirdParty.getBusinessName())
                                    .thirdPartyNit(thirdParty.getNit())
                                    .build()
                    ))
                    .build();

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            advance.setJournalEntryId(je.getId());
            advanceRepository.save(advance);
            auditPublisher.publishCreate(AuditModule.AR, "ArAdvance", advance.getId(), "ArAdvance creado id=" + advance.getId());
            log.info("Asiento contable {} generado para anticipo AR {}", je.getId(), advance.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para anticipo AR {}: {}", advance.getId(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar el anticipo: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para anticipo AR {}", advance.getId(), e);
            throw e;
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Anticipo registrado exitosamente"), Optional.of(toDTO(advance))));
    }

    /**
     * Aplica un anticipo parcial o totalmente a una factura de venta.
     * Valida que el anticipo tenga saldo disponible, que la factura pertenezca al mismo
     * tercero y que el monto no supere ni el saldo disponible del anticipo ni el saldo
     * pendiente de la factura. Actualiza appliedAmount del anticipo, balanceDue de la
     * factura y genera asiento contable (Debito Anticipos clientes / Credito CxC).
     *
     * @param advanceId identificador del anticipo
     * @param request   datos de la aplicacion (factura y monto)
     * @return ResponseEntity con el anticipo actualizado
     * @throws IllegalArgumentException si el anticipo o la factura no existen o los montos son invalidos
     * @throws IllegalStateException    si el anticipo esta agotado o el tercero no coincide
     */
    @Transactional
    public ResponseEntity<?> applyAdvance(Long advanceId, ApplyArAdvanceRequest request) {
        // 1. Buscar anticipo y validar estado
        ArAdvance advance = advanceRepository.findById(advanceId)
                .orElseThrow(() -> new IllegalArgumentException("El anticipo no fue encontrado"));

        if ("FULLY_APPLIED".equals(advance.getStatus())) {
            throw new IllegalStateException("El anticipo ya fue totalmente aplicado");
        }

        // 2. Buscar factura
        SalesInvoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        if (invoice.getStatus() == SalesInvoiceStatus.VOIDED) {
            throw new IllegalStateException("No se puede aplicar anticipo a una factura anulada");
        }
        if (invoice.getStatus() == SalesInvoiceStatus.PAID
                || invoice.getStatus() == SalesInvoiceStatus.SETTLED) {
            throw new IllegalStateException("La factura ya se encuentra totalmente pagada");
        }

        // 3. Validar que el tercero coincida
        if (invoice.getThirdParty() == null || advance.getThirdParty() == null
                || !invoice.getThirdParty().getId().equals(advance.getThirdParty().getId())) {
            throw new IllegalStateException(
                    "El anticipo no corresponde al mismo tercero de la factura");
        }

        // 4. Validar monto a aplicar
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto a aplicar debe ser mayor a cero");
        }

        BigDecimal available = advance.getAmount().subtract(
                advance.getAppliedAmount() != null ? advance.getAppliedAmount() : BigDecimal.ZERO);
        if (request.getAmount().compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    "El monto a aplicar supera el saldo disponible del anticipo. Disponible: $" + available);
        }

        BigDecimal balanceDue = invoice.getBalanceDue() != null ? invoice.getBalanceDue() : BigDecimal.ZERO;
        if (request.getAmount().compareTo(balanceDue) > 0) {
            throw new IllegalArgumentException(
                    "El monto a aplicar supera el saldo pendiente de la factura. Saldo: $" + balanceDue);
        }

        // 5. Actualizar anticipo
        BigDecimal newApplied = (advance.getAppliedAmount() != null
                ? advance.getAppliedAmount() : BigDecimal.ZERO).add(request.getAmount());
        advance.setAppliedAmount(newApplied);
        advance.setLastAppliedAt(LocalDateTime.now());

        if (newApplied.compareTo(advance.getAmount()) >= 0) {
            advance.setStatus("FULLY_APPLIED");
        } else {
            advance.setStatus("PARTIALLY_APPLIED");
        }
        advanceRepository.save(advance);

        // 6. Actualizar saldo de la factura
        BigDecimal newBalance = balanceDue.subtract(request.getAmount());
        invoice.setBalanceDue(newBalance);
        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus(SalesInvoiceStatus.PAID);
        } else {
            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);
        }
        invoiceRepository.save(invoice);

        // 7. Generar asiento contable (Debito Anticipos clientes / Credito CxC)
        try {
            // AR-09 aplicacion: Debito Anticipos (PUC 2805) / Credito CxC clientes (PUC 1305)
            Long debitAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AR_ANTICIPOS);
            Long creditAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AR_CLIENTES);

            String thirdPartyNit = advance.getThirdParty().getNit();
            String thirdPartyName = advance.getThirdParty().getBusinessName();

            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                    .entryDate(invoice.getInvoiceDate())
                    .description("Aplicacion anticipo a factura " + invoice.getInvoiceNumber()
                            + " - " + thirdPartyName)
                    .sourceModule(JournalSourceModule.AR)
                    .sourceId(advance.getId())
                    .lines(List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(debitAccountId)
                                    .debitAmount(request.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .description("Aplicacion anticipo " + thirdPartyName)
                                    .thirdPartyNit(thirdPartyNit)
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(creditAccountId)
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(request.getAmount())
                                    .description("Cobro CxC factura " + invoice.getInvoiceNumber())
                                    .thirdPartyNit(thirdPartyNit)
                                    .build()
                    ))
                    .build();

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            log.info("Asiento contable {} generado por aplicacion de anticipo {} a factura {}",
                    je.getId(), advance.getId(), invoice.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento para aplicacion de anticipo {}: {}", advance.getId(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo aplicar el anticipo: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para aplicacion de anticipo {}", advance.getId(), e);
            throw e;
        }

        log.info("Anticipo {} aplicado a factura {} por ${}", advanceId, invoice.getId(), request.getAmount());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Anticipo aplicado exitosamente a la factura"), Optional.of(toDTO(advance))));
    }

    /**
     * Consulta anticipos con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de anticipos
     */
    public ResponseEntity<?> getAdvances(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<ArAdvance> spec = specBuilder.build(request);
        Page<ArAdvanceDTO> data = advanceRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Convierte una entidad ArAdvance a su DTO de lectura.
     *
     * @param advance entidad a convertir
     * @return DTO con los datos del anticipo
     */
    private ArAdvanceDTO toDTO(ArAdvance advance) {
        BigDecimal applied = advance.getAppliedAmount() != null ? advance.getAppliedAmount() : BigDecimal.ZERO;
        BigDecimal available = advance.getAmount() != null
                ? advance.getAmount().subtract(applied) : BigDecimal.ZERO;
        return ArAdvanceDTO.builder()
                .id(advance.getId())
                .thirdPartyId(advance.getThirdParty() != null ? advance.getThirdParty().getId() : null)
                .thirdPartyName(advance.getThirdParty() != null ? advance.getThirdParty().getBusinessName() : null)
                .amount(advance.getAmount())
                .appliedAmount(applied)
                .availableAmount(available)
                .advanceDate(advance.getAdvanceDate())
                .advanceReference(advance.getAdvanceReference())
                .status(advance.getStatus())
                .bankMovementId(advance.getBankMovementId())
                .bankAccountId(advance.getBankAccountId())
                .cashId(advance.getCashId())
                .journalEntryId(advance.getJournalEntryId())
                .notes(advance.getNotes())
                .build();
    }
}
