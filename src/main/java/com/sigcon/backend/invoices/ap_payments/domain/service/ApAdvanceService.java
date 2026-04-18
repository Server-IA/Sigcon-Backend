package com.sigcon.backend.invoices.ap_payments.domain.service;

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

import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.invoices.ap_payments.application.ApAdvanceDTO;
import com.sigcon.backend.invoices.ap_payments.application.ApplyAdvanceRequest;
import com.sigcon.backend.invoices.ap_payments.application.CreateApAdvanceRequest;
import com.sigcon.backend.invoices.ap_payments.domain.model.ApAdvance;
import com.sigcon.backend.invoices.ap_payments.domain.repository.ApAdvanceRepository;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestion de anticipos a proveedores.
 * Permite registrar anticipos y aplicarlos a facturas de compra,
 * con generacion automatica de asientos contables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApAdvanceService {

    private final ApAdvanceRepository advanceRepository;
    private final InvoiceRepository invoiceRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final JournalEntryService journalEntryService;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;

    private final DataTableSpecificationBuilder<ApAdvance> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Registra un nuevo anticipo a un proveedor.
     * Valida la existencia del tercero, que este activo y que el periodo este abierto.
     *
     * @param request datos del anticipo a registrar
     * @return ResponseEntity con el anticipo creado
     * @throws IllegalArgumentException si el tercero no existe
     * @throws IllegalStateException    si el tercero esta inactivo o el periodo cerrado
     */
    @Transactional
    public ResponseEntity<?> registerAdvance(CreateApAdvanceRequest request) {
        // 1. Validar tercero existe y esta activo
        ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new IllegalArgumentException("El tercero no fue encontrado"));

        if (thirdParty.getStatus() != null && !"ACTIVE".equalsIgnoreCase(thirdParty.getStatus().getName())) {
            throw new IllegalStateException("El tercero no se encuentra activo");
        }

        // 2. Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(request.getAdvanceDate());

        // 3. Crear anticipo
        ApAdvance advance = ApAdvance.builder()
                .thirdParty(thirdParty)
                .amount(request.getAmount())
                .advanceDate(request.getAdvanceDate())
                .status("PENDING")
                .bankAccountId(request.getBankAccountId())
                .cashId(request.getCashId())
                .notes(request.getNotes())
                .build();

        advance = advanceRepository.save(advance);

        // 4. Generar asiento contable (Debito Anticipo / Credito Banco/Caja)
        try {
            // AP-09: Debito Anticipos a proveedores (PUC 1330) / Credito Bancos (PUC 1110)
            Long debitAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AP_ANTICIPOS);
            Long creditAccountId = accountMappingService.resolveOrThrow(AccountingConcept.BANCOS_DEFAULT);

            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                    .entryDate(request.getAdvanceDate())
                    .description("Anticipo a proveedor " + thirdParty.getBusinessName())
                    .sourceModule(JournalSourceModule.AP)
                    .sourceId(advance.getId())
                    .lines(List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(debitAccountId)
                                    .debitAmount(request.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .description("Anticipo a " + thirdParty.getBusinessName())
                                    .thirdPartyNit(thirdParty.getNit())
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(creditAccountId)
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(request.getAmount())
                                    .description("Egreso anticipo " + thirdParty.getBusinessName())
                                    .thirdPartyNit(thirdParty.getNit())
                                    .build()
                    ))
                    .build();

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            advance.setJournalEntryId(je.getId());
            advanceRepository.save(advance);
            log.info("Asiento contable {} generado para anticipo {}", je.getId(), advance.getId());
        } catch (Exception e) {
            log.warn("No se pudo generar asiento contable para anticipo {}: {}",
                    advance.getId(), e.getMessage());
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Anticipo registrado exitosamente"), Optional.of(toDTO(advance))));
    }

    /**
     * Aplica un anticipo existente a una factura de compra.
     * Valida que el anticipo este pendiente, que la factura pertenezca al mismo tercero,
     * y que el monto no supere ni el anticipo ni el saldo de la factura.
     *
     * @param advanceId identificador del anticipo
     * @param request   datos de la aplicacion (factura y monto)
     * @return ResponseEntity con el anticipo actualizado
     * @throws IllegalArgumentException si el anticipo o la factura no existen, o los montos son invalidos
     * @throws IllegalStateException    si el anticipo no esta pendiente o el tercero no coincide
     */
    @Transactional
    public ResponseEntity<?> applyAdvance(Long advanceId, ApplyAdvanceRequest request) {
        // 1. Buscar anticipo y validar estado
        ApAdvance advance = advanceRepository.findById(advanceId)
                .orElseThrow(() -> new IllegalArgumentException("El anticipo no fue encontrado"));

        if (!"PENDING".equals(advance.getStatus())) {
            throw new IllegalStateException("El anticipo ya fue aplicado o no esta en estado pendiente");
        }

        // 2. Buscar factura y validar tercero
        Invoices invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        if (invoice.getThirdParty() == null || advance.getThirdParty() == null
                || !invoice.getThirdParty().getId().equals(advance.getThirdParty().getId())) {
            throw new IllegalStateException(
                    "El anticipo no corresponde al mismo tercero de la factura");
        }

        // 3. Validar monto
        if (request.getAmount().compareTo(advance.getAmount()) > 0) {
            throw new IllegalArgumentException(
                    "El monto a aplicar supera el valor del anticipo. Anticipo disponible: $" + advance.getAmount());
        }

        BigDecimal balanceDue = BigDecimal.valueOf(invoice.getBalanceDue());
        if (request.getAmount().compareTo(balanceDue) > 0) {
            throw new IllegalArgumentException(
                    "El monto a aplicar supera el saldo pendiente de la factura. Saldo: $" + balanceDue);
        }

        // 4. Actualizar anticipo
        advance.setStatus("APPLIED");
        advance.setAppliedInvoiceId(invoice.getId());
        advance.setAppliedAmount(request.getAmount());
        advance.setAppliedAt(LocalDateTime.now());
        advanceRepository.save(advance);

        // 5. Actualizar saldo de la factura
        double newBalance = invoice.getBalanceDue() - request.getAmount().doubleValue();
        invoice.setBalanceDue(newBalance);
        if (newBalance <= 0) {
            invoice.setStatus(StatusesInvoices.PAID);
        } else {
            invoice.setStatus(StatusesInvoices.PARTIALLY_PAID);
        }
        invoiceRepository.save(invoice);

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

        Specification<ApAdvance> spec = specBuilder.build(request);
        Page<ApAdvanceDTO> data = advanceRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Convierte una entidad ApAdvance a su DTO de lectura.
     *
     * @param advance entidad a convertir
     * @return DTO con los datos del anticipo
     */
    private ApAdvanceDTO toDTO(ApAdvance advance) {
        return ApAdvanceDTO.builder()
                .id(advance.getId())
                .thirdPartyId(advance.getThirdParty() != null ? advance.getThirdParty().getId() : null)
                .thirdPartyName(advance.getThirdParty() != null ? advance.getThirdParty().getBusinessName() : null)
                .amount(advance.getAmount())
                .advanceDate(advance.getAdvanceDate())
                .status(advance.getStatus())
                .appliedInvoiceId(advance.getAppliedInvoiceId())
                .appliedAmount(advance.getAppliedAmount())
                .build();
    }
}
