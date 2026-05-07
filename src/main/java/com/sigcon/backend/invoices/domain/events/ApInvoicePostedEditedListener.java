package com.sigcon.backend.invoices.domain.events;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HU-AP-13 E2 (Bloque AS): listener AFTER_COMMIT que crea un asiento de
 * correccion DRAFT vinculado al JE original via correctionOf cuando se edita
 * una factura con JE POSTED. Si falla NO afecta el update de la factura
 * (esta TX es REQUIRES_NEW + el listener corre solo despues del commit del
 * update).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApInvoicePostedEditedListener {

    private final JournalEntryService journalEntryService;
    private final JournalEntryRepository journalEntryRepository;
    private final AuditPublisher auditPublisher;
    private final InvoiceRepository invoiceRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApInvoicePostedEdited(ApInvoicePostedEditedEvent event) {
        try {
            JournalEntry original = journalEntryRepository.findById(event.getOriginalJournalEntryId())
                    .orElse(null);
            if (original == null) {
                log.warn("HU-AP-13 E2 listener: JE original {} no encontrado", event.getOriginalJournalEntryId());
                return;
            }

            // Construir lineas espejo del JE original
            List<CreateJournalEntryLineRequest> mirrorLines = new ArrayList<>();
            if (original.getLines() != null) {
                for (var origLine : original.getLines()) {
                    mirrorLines.add(CreateJournalEntryLineRequest.builder()
                            .accountingAccountId(origLine.getAccountingAccount() != null
                                    ? origLine.getAccountingAccount().getId() : null)
                            .debitAmount(origLine.getDebitAmount())
                            .creditAmount(origLine.getCreditAmount())
                            .description("Ajuste por edicion factura " + event.getResolutionInvoice())
                            .thirdPartyNit(origLine.getThirdPartyNit())
                            .build());
                }
            }

            // QA Bloque AU+ HU-AP-13 E2 (2026-05-06): la HU exige DOS pasos cuando
            // se edita una factura con JE POSTED:
            //   1) Reversar el JE original (queda REVERSED + crea contrapartida REV-).
            //   2) Crear NUEVO JE DRAFT con datos editados, listo para contabilizar.
            // Antes solo se llamaba createCorrection que dejaba el JE original POSTED
            // intacto y agregaba un asiento de correccion. Eso NO refleja la HU
            // porque el contador veia el JE original sin reversar y un correctivo
            // adicional. Ahora reversamos primero y creamos el nuevo independiente.
            try {
                journalEntryService.reverseEntry(original.getId(),
                        "Reversion automatica por edicion de factura AP "
                        + event.getResolutionInvoice() + " (HU-AP-13 E2)",
                        "sistema");
                log.info("HU-AP-13 E2 listener: JE original {} REVERSED por edicion factura {}",
                        original.getId(), event.getInvoiceId());
            } catch (RuntimeException revEx) {
                log.warn("HU-AP-13 E2 listener: no se pudo reversar JE original {}: {}",
                        original.getId(), revEx.getMessage());
                // Si la reversion falla, abortamos para no dejar JE viejo + nuevo
                // duplicados. La factura ya tiene el journalEntryId apuntando al
                // viejo POSTED — el contador puede revertir manualmente.
                return;
            }

            CreateJournalEntryRequest newJeReq = CreateJournalEntryRequest.builder()
                    .entryDate(event.getNewInvoiceDate() != null
                            ? event.getNewInvoiceDate() : original.getEntryDate())
                    .description("Factura compra " + event.getResolutionInvoice()
                            + " (recreado por edicion HU-AP-13 E2)")
                    .sourceModule(JournalSourceModule.AP)
                    .sourceId(event.getInvoiceId())
                    .lines(mirrorLines)
                    .build();

            JournalEntryDTO newJe = journalEntryService.createEntry(newJeReq, "sistema");
            log.info("HU-AP-13 E2 listener: NUEVO JE DRAFT id={} creado para factura {} (reemplaza JE {})",
                    newJe.getId(), event.getInvoiceId(), original.getId());

            // Re-vincular la factura al JE nuevo (asi el contador opera sobre el
            // borrador editable y no el viejo reversado).
            try {
                Invoices invoice = invoiceRepository.findById(event.getInvoiceId()).orElse(null);
                if (invoice != null) {
                    invoice.setJournalEntryId(newJe.getId());
                    invoiceRepository.save(invoice);
                }
            } catch (RuntimeException linkEx) {
                log.warn("HU-AP-13 E2 listener: no se pudo re-vincular factura {} al nuevo JE {}: {}",
                        event.getInvoiceId(), newJe.getId(), linkEx.getMessage());
            }

            // QA-BLOQUE-AY HU-AP-13 E2 (2026-05-06): registrar en auditoria toda
            // la cadena de cambios para trazabilidad CG.
            try {
                auditPublisher.publishCreate(AuditModule.CG, "JournalEntry", newJe.getId(),
                        "Comprobante DRAFT generado por edicion de factura AP "
                        + event.getResolutionInvoice() + " | JE original REVERSED=" + original.getId()
                        + " | factura=" + event.getInvoiceId() + " (HU-AP-13 E2)");
            } catch (RuntimeException auditEx) {
                log.warn("HU-AP-13 E2 listener: no se pudo registrar audit log del nuevo JE {}: {}",
                        newJe.getId(), auditEx.getMessage());
            }
        } catch (Exception e) {
            log.warn("HU-AP-13 E2 listener: no se pudo generar ajuste contable para factura {}: {}",
                    event.getInvoiceId(), e.getMessage());
        }
    }
}
