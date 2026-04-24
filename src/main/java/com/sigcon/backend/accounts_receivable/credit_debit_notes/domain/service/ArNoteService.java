package com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.service;

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

import com.sigcon.backend.accounts_receivable.credit_debit_notes.application.ArNoteDTO;
import com.sigcon.backend.accounts_receivable.credit_debit_notes.application.CreateArNoteRequest;
import com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.model.ArCreditDebitNote;
import com.sigcon.backend.accounts_receivable.credit_debit_notes.domain.repository.ArNoteRepository;
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
 * Servicio para gestion de notas credito y debito sobre facturas de venta.
 * Cubre HU AR-07.
 * Las notas credito reducen el saldo pendiente (Debito Ingresos / Credito CxC).
 * Las notas debito incrementan el saldo pendiente (Debito CxC / Credito Ingresos).
 * Cada nota genera un asiento contable automaticamente y un consecutivo
 * con formato NC-FV-{anio}{6d} o ND-FV-{anio}{6d}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArNoteService {

    private final ArNoteRepository noteRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final JournalEntryService journalEntryService;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<ArCreditDebitNote> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Crea una nota credito o debito asociada a una factura de venta.
     *
     * @param request datos de la nota a crear
     * @return ResponseEntity con la nota creada
     * @throws IllegalArgumentException si la factura no existe, el tipo es invalido
     *                                  o el monto de nota credito supera el saldo
     * @throws IllegalStateException    si la factura esta anulada o el periodo cerrado
     */
    @Transactional
    public ResponseEntity<?> createNote(CreateArNoteRequest request) {
        // 1. Buscar factura
        SalesInvoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        // Validar que la factura no este anulada
        if (invoice.getStatus() == SalesInvoiceStatus.VOIDED) {
            throw new IllegalStateException("No se puede crear una nota para una factura anulada");
        }
        if (invoice.getStatus() == SalesInvoiceStatus.DRAFT) {
            throw new IllegalStateException("No se puede crear una nota para una factura en borrador");
        }

        // 2. Validar tipo de nota
        String noteType = request.getNoteType().toUpperCase().trim();
        if (!"CREDIT".equals(noteType) && !"DEBIT".equals(noteType)) {
            throw new IllegalArgumentException(
                    "Tipo de nota invalido: " + request.getNoteType() + ". Debe ser CREDIT o DEBIT");
        }

        // 3. Validar monto positivo
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto de la nota debe ser mayor a cero");
        }

        // 4. Para nota credito: validar que el monto no supere el saldo
        BigDecimal balanceDue = invoice.getBalanceDue() != null ? invoice.getBalanceDue() : BigDecimal.ZERO;
        if ("CREDIT".equals(noteType)) {
            if (request.getAmount().compareTo(balanceDue) > 0) {
                throw new IllegalArgumentException(
                        "El valor de la nota credito excede el saldo pendiente. Saldo actual: $" + balanceDue);
            }
        }

        // HU-AR-07 E2: doble aprobacion para notas de monto medio o grande.
        // Umbrales tomados como porcentaje del saldo de la factura para escalar:
        //   - MED: nota >= 30% del valor original de la factura
        //   - GRA: nota >= 60% del valor original de la factura
        // Cuando supera umbral MED, se requiere segunda aprobacion (request.approverComment
        // debe estar presente) - de lo contrario crea la nota en estado pendiente.
        BigDecimal totalAmount = invoice.getTotalAmount() != null
                ? invoice.getTotalAmount() : balanceDue;
        if (totalAmount.signum() > 0) {
            BigDecimal pct = request.getAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalAmount, 2, java.math.RoundingMode.HALF_UP);
            boolean requiresSecondApproval = pct.compareTo(BigDecimal.valueOf(30)) >= 0;
            if (requiresSecondApproval
                    && (request.getApproverComment() == null
                        || request.getApproverComment().trim().length() < 10)) {
                throw new IllegalStateException(
                        "Nota " + noteType + " por " + pct + "% del valor de la factura ("
                        + (pct.compareTo(BigDecimal.valueOf(60)) >= 0 ? "GRA" : "MED")
                        + ") requiere doble aprobacion. "
                        + "Incluya 'approverComment' (minimo 10 caracteres) confirmando la segunda aprobacion.");
            }
        }

        // 5. Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(LocalDate.now());

        // 6. Generar numero de nota consecutivo con formato NC-FV-{anio}{6d} o ND-FV-{anio}{6d}
        String prefix = "CREDIT".equals(noteType) ? "NC-FV" : "ND-FV";
        long count = noteRepository.countByNoteTypeAndDeletedAtIsNull(noteType);
        String noteNumber = prefix + "-" + LocalDate.now().getYear() + String.format("%06d", count + 1);
        // Garantizar unicidad (por si hay colision entre anios)
        while (noteRepository.existsByNoteNumberAndDeletedAtIsNull(noteNumber)) {
            count++;
            noteNumber = prefix + "-" + LocalDate.now().getYear() + String.format("%06d", count + 1);
        }

        // 7. Crear la nota
        ArCreditDebitNote note = ArCreditDebitNote.builder()
                .invoice(invoice)
                .noteType(noteType)
                .noteNumber(noteNumber)
                .amount(request.getAmount())
                .reason(request.getReason())
                .build();

        note = noteRepository.save(note);
        auditPublisher.publishCreate(AuditModule.AR, "ArNote", note.getId(), "ArNote creado id=" + note.getId());

        // 8. Actualizar saldo de la factura
        if ("CREDIT".equals(noteType)) {
            // Nota credito: reduce saldo
            BigDecimal newBalance = balanceDue.subtract(request.getAmount());
            invoice.setBalanceDue(newBalance);
            if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
                invoice.setStatus(SalesInvoiceStatus.PAID);
            } else if (invoice.getStatus() == SalesInvoiceStatus.ISSUED) {
                invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);
            }
        } else {
            // Nota debito: incrementa saldo
            BigDecimal newBalance = balanceDue.add(request.getAmount());
            invoice.setBalanceDue(newBalance);
            if (invoice.getStatus() == SalesInvoiceStatus.PAID) {
                invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);
            }
        }
        invoiceRepository.save(invoice);
        auditPublisher.publishCreate(AuditModule.AR, "ArNote", invoice.getId(), "ArNote creado id=" + invoice.getId());

        // 9. Generar asiento contable (AR-07 - cuentas resueltas por AccountMappingService)
        try {
            // Cuentas reales del PUC colombiano:
            //   4135 Ingresos operacionales  |  1305 CxC clientes
            Long idIngresos = accountMappingService.resolveOrThrow(AccountingConcept.AR_INGRESOS);
            Long idCxcClientes = accountMappingService.resolveOrThrow(AccountingConcept.AR_CLIENTES);

            String thirdPartyNit = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getNit() : null;
            String thirdPartyName = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getBusinessName() : "N/A";

            CreateJournalEntryRequest jeRequest;

            if ("CREDIT".equals(noteType)) {
                // NC: Debito Ingresos (reversion) / Credito CxC (baja saldo)
                jeRequest = CreateJournalEntryRequest.builder()
                        .entryDate(LocalDate.now())
                        .description("Nota credito " + noteNumber + " - Factura " + invoice.getInvoiceNumber()
                                + " - " + thirdPartyName)
                        .sourceModule(JournalSourceModule.AR)
                        .sourceId(note.getId())
                        .lines(List.of(
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idIngresos)
                                        .debitAmount(request.getAmount())
                                        .creditAmount(BigDecimal.ZERO)
                                        .description("NC " + noteNumber + " - Reversion ingresos")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build(),
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idCxcClientes)
                                        .debitAmount(BigDecimal.ZERO)
                                        .creditAmount(request.getAmount())
                                        .description("NC " + noteNumber + " - CxC cliente")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build()
                        ))
                        .build();
            } else {
                // ND: Debito CxC (aumenta saldo) / Credito Ingresos (ingreso adicional)
                jeRequest = CreateJournalEntryRequest.builder()
                        .entryDate(LocalDate.now())
                        .description("Nota debito " + noteNumber + " - Factura " + invoice.getInvoiceNumber()
                                + " - " + thirdPartyName)
                        .sourceModule(JournalSourceModule.AR)
                        .sourceId(note.getId())
                        .lines(List.of(
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idCxcClientes)
                                        .debitAmount(request.getAmount())
                                        .creditAmount(BigDecimal.ZERO)
                                        .description("ND " + noteNumber + " - CxC cliente")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build(),
                                CreateJournalEntryLineRequest.builder()
                                        .accountingAccountId(idIngresos)
                                        .debitAmount(BigDecimal.ZERO)
                                        .creditAmount(request.getAmount())
                                        .description("ND " + noteNumber + " - Ingreso adicional")
                                        .thirdPartyNit(thirdPartyNit)
                                        .build()
                        ))
                        .build();
            }

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            note.setJournalEntryId(je.getId());
            noteRepository.save(note);
            auditPublisher.publishCreate(AuditModule.AR, "ArNote", note.getId(), "ArNote creado id=" + note.getId());
            log.info("Asiento contable {} generado para nota AR {} de factura {}",
                    je.getId(), noteNumber, invoice.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para nota AR {}: {}",
                    note.getNoteNumber(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar la nota: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para nota AR {}", note.getNoteNumber(), e);
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

        Specification<ArCreditDebitNote> spec = specBuilder.build(request);
        Page<ArNoteDTO> data = noteRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Obtiene una nota por su identificador.
     *
     * @param id identificador de la nota
     * @return ResponseEntity con el DTO de la nota
     * @throws IllegalArgumentException si no existe
     */
    public ResponseEntity<?> getById(Long id) {
        ArCreditDebitNote note = noteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La nota no fue encontrada"));
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Nota encontrada"), Optional.of(toDTO(note))));
    }

    /**
     * Obtiene todas las notas asociadas a una factura de venta.
     *
     * @param invoiceId identificador de la factura
     * @return lista de notas de la factura
     */
    public ResponseEntity<?> getNotesByInvoice(Long invoiceId) {
        List<ArNoteDTO> notes = noteRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Notas de la factura"), Optional.of(notes)));
    }

    /**
     * Convierte una entidad ArCreditDebitNote a su DTO de lectura.
     *
     * @param note entidad a convertir
     * @return DTO con los datos de la nota
     */
    private ArNoteDTO toDTO(ArCreditDebitNote note) {
        return ArNoteDTO.builder()
                .id(note.getId())
                .invoiceId(note.getInvoice() != null ? note.getInvoice().getId() : null)
                .invoiceNumber(note.getInvoice() != null ? note.getInvoice().getInvoiceNumber() : null)
                .noteType(note.getNoteType())
                .noteNumber(note.getNoteNumber())
                .amount(note.getAmount())
                .reason(note.getReason())
                .journalEntryId(note.getJournalEntryId())
                .build();
    }
}
