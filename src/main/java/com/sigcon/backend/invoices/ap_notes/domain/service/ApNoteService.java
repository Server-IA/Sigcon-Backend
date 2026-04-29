package com.sigcon.backend.invoices.ap_notes.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
import com.sigcon.backend.invoices.ap_notes.application.ApNoteDTO;
import com.sigcon.backend.invoices.ap_notes.application.CreateApNoteRequest;
import com.sigcon.backend.invoices.ap_notes.domain.model.ApCreditDebitNote;
import com.sigcon.backend.invoices.ap_notes.domain.repository.ApNoteRepository;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
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
 * Servicio para gestion de notas credito y debito asociadas a facturas de compra.
 * Las notas credito reducen el saldo pendiente, las notas debito lo incrementan.
 * Cada nota genera un asiento contable automaticamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApNoteService {

    private final ApNoteRepository noteRepository;
    private final InvoiceRepository invoiceRepository;
    private final JournalEntryService journalEntryService;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<ApCreditDebitNote> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Crea una nota credito o debito asociada a una factura de compra.
     * <ul>
     *   <li>CREDIT: reduce el saldo pendiente. Valida que el monto no supere el saldo.</li>
     *   <li>DEBIT: incrementa el saldo pendiente.</li>
     * </ul>
     * Genera un numero consecutivo automatico y un asiento contable.
     *
     * @param request datos de la nota a crear
     * @return ResponseEntity con la nota creada
     * @throws IllegalArgumentException si la factura no existe, el tipo es invalido
     *                                  o el monto de nota credito supera el saldo
     * @throws IllegalStateException    si la factura esta anulada o el periodo cerrado
     */
    @Transactional
    public ResponseEntity<?> createNote(CreateApNoteRequest request) {
        // 1. Buscar factura
        Invoices invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        // Validar que la factura no este anulada
        if (invoice.getStatus() == StatusesInvoices.VOIDED) {
            throw new IllegalStateException("No se puede crear una nota para una factura anulada");
        }

        // 2. Validar tipo de nota
        String noteType = request.getNoteType().toUpperCase().trim();
        if (!"CREDIT".equals(noteType) && !"DEBIT".equals(noteType)) {
            throw new IllegalArgumentException(
                    "Tipo de nota invalido: " + request.getNoteType() + ". Debe ser CREDIT o DEBIT");
        }

        // 3. Para nota credito: validar que el monto no supere el saldo
        if ("CREDIT".equals(noteType)) {
            BigDecimal balanceDue = BigDecimal.valueOf(invoice.getBalanceDue());
            if (request.getAmount().compareTo(balanceDue) > 0) {
                // HU-AP-10 MT-#02 (2026-04-28): formatear saldo en formato
                // monetario legible. Antes mostraba "1.0E+7" cientifico que
                // confundia al contador.
                String saldoFmt = String.format(java.util.Locale.US, "%,.2f", balanceDue);
                throw new IllegalArgumentException(
                        "El valor de la nota credito ($" + String.format(java.util.Locale.US, "%,.2f", request.getAmount())
                        + ") excede el saldo pendiente de la factura ($" + saldoFmt
                        + "). Reduzca el monto de la nota o aplique parcialmente.");
            }
        }

        // 4. Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(LocalDate.now());

        // 5. Generar numero de nota consecutivo
        String prefix = "CREDIT".equals(noteType) ? "NC" : "ND";
        long count = noteRepository.countByNoteTypeAndDeletedAtIsNull(noteType);
        String noteNumber = prefix + "-" + LocalDate.now().getYear() + String.format("%04d", count + 1);

        // 6. Crear la nota
        ApCreditDebitNote note = ApCreditDebitNote.builder()
                .invoice(invoice)
                .noteType(noteType)
                .noteNumber(noteNumber)
                .amount(request.getAmount())
                .reason(request.getReason())
                .build();

        note = noteRepository.save(note);
        auditPublisher.publishCreate(AuditModule.AP, "ApNote", note.getId(), "ApNote creado id=" + note.getId());

        // 7. Actualizar saldo de la factura
        if ("CREDIT".equals(noteType)) {
            double newBalance = invoice.getBalanceDue() - request.getAmount().doubleValue();
            invoice.setBalanceDue(newBalance);
            if (newBalance <= 0) {
                invoice.setStatus(StatusesInvoices.PAID);
            } else if (invoice.getStatus() == StatusesInvoices.PENDING) {
                invoice.setStatus(StatusesInvoices.PARTIALLY_PAID);
            }
        } else {
            // DEBIT: incrementar saldo
            double newBalance = invoice.getBalanceDue() + request.getAmount().doubleValue();
            invoice.setBalanceDue(newBalance);
            if (invoice.getStatus() == StatusesInvoices.PAID) {
                invoice.setStatus(StatusesInvoices.PARTIALLY_PAID);
            }
        }
        invoiceRepository.save(invoice);
        auditPublisher.publishCreate(AuditModule.AP, "ApNote", invoice.getId(), "ApNote creado id=" + invoice.getId());

        // 8. Generar asiento contable (AP-07 - cuentas resueltas por AccountMappingService)
        try {
            // Cuentas reales del PUC colombiano:
            //   2205 CxP proveedores  |  5105/GASTO asociado a la linea original
            // Para mantener la simetria sin requerir la cuenta original, usamos:
            //   AP_PROVEEDORES como una pata, AP_IVA_DESCONTABLE como contrapartida de gasto
            //   (en un sistema mas complejo esto seria la cuenta de gasto real de la factura).
            Long idCxpProveedores = accountMappingService.resolveOrThrow(AccountingConcept.AP_PROVEEDORES);
            Long idContraGasto = accountMappingService.resolveOrThrow(AccountingConcept.AP_IVA_DESCONTABLE);

            String thirdPartyNit = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getNit() : null;
            String thirdPartyName = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getBusinessName() : "N/A";

            CreateJournalEntryRequest jeRequest;

            if ("CREDIT".equals(noteType)) {
                // NC proveedor: Debito CxP (reduce deuda) / Credito IVA-Gasto (reversion)
                jeRequest = CreateJournalEntryRequest.builder()
                        .entryDate(LocalDate.now())
                        .description("Nota credito " + noteNumber + " - Factura " + invoice.getResolutionInvoice()
                                + " - " + thirdPartyName)
                        .sourceModule(JournalSourceModule.AP)
                        .sourceId(note.getId())
                        .lines(List.of(
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idCxpProveedores)
                                        .debitAmount(request.getAmount())
                                        .creditAmount(BigDecimal.ZERO)
                                        .description("NC " + noteNumber + " - Debito CxP")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build(),
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idContraGasto)
                                        .debitAmount(BigDecimal.ZERO)
                                        .creditAmount(request.getAmount())
                                        .description("NC " + noteNumber + " - Reversion gasto/IVA")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build()
                        ))
                        .build();
            } else {
                // ND proveedor: Debito gasto adicional / Credito CxP (aumenta deuda)
                jeRequest = CreateJournalEntryRequest.builder()
                        .entryDate(LocalDate.now())
                        .description("Nota debito " + noteNumber + " - Factura " + invoice.getResolutionInvoice()
                                + " - " + thirdPartyName)
                        .sourceModule(JournalSourceModule.AP)
                        .sourceId(note.getId())
                        .lines(List.of(
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idContraGasto)
                                        .debitAmount(request.getAmount())
                                        .creditAmount(BigDecimal.ZERO)
                                        .description("ND " + noteNumber + " - Gasto adicional")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build(),
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idCxpProveedores)
                                        .debitAmount(BigDecimal.ZERO)
                                        .creditAmount(request.getAmount())
                                        .description("ND " + noteNumber + " - CxP proveedor")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build()
                        ))
                        .build();
            }

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            note.setJournalEntryId(je.getId());
            noteRepository.save(note);
            auditPublisher.publishCreate(AuditModule.AP, "ApNote", note.getId(), "ApNote creado id=" + note.getId());
            log.info("Asiento contable {} generado para nota {} de factura {}",
                    je.getId(), noteNumber, invoice.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para nota {}: {}",
                    note.getNoteNumber(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar la nota: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para nota {}", note.getNoteNumber(), e);
            throw e;
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Nota " + ("CREDIT".equals(noteType) ? "credito" : "debito")
                                + " creada exitosamente"),
                        Optional.of(toDTO(note))));
    }

    /**
     * Consulta notas credito/debito con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de notas
     */
    public ResponseEntity<?> getNotes(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<ApCreditDebitNote> spec = specBuilder.build(request);
        Page<ApNoteDTO> data = noteRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Obtiene todas las notas asociadas a una factura especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de notas de la factura
     */
    public ResponseEntity<?> getNotesByInvoice(Long invoiceId) {
        List<ApNoteDTO> notes = noteRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Notas de la factura"), Optional.of(notes)));
    }

    /**
     * Convierte una entidad ApCreditDebitNote a su DTO de lectura.
     *
     * @param note entidad a convertir
     * @return DTO con los datos de la nota
     */
    private ApNoteDTO toDTO(ApCreditDebitNote note) {
        return ApNoteDTO.builder()
                .id(note.getId())
                .invoiceId(note.getInvoice() != null ? note.getInvoice().getId() : null)
                .invoiceNumber(note.getInvoice() != null ? note.getInvoice().getResolutionInvoice() : null)
                .noteType(note.getNoteType())
                .noteNumber(note.getNoteNumber())
                .amount(note.getAmount())
                .reason(note.getReason())
                .build();
    }
}
