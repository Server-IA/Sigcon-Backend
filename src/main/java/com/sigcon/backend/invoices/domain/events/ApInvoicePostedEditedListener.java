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

            CreateJournalEntryRequest correctionReq = CreateJournalEntryRequest.builder()
                    .entryDate(event.getNewInvoiceDate() != null
                            ? event.getNewInvoiceDate() : original.getEntryDate())
                    .description("Ajuste contable factura " + event.getResolutionInvoice())
                    .sourceModule(JournalSourceModule.AP)
                    .sourceId(event.getInvoiceId())
                    .lines(mirrorLines)
                    .build();

            JournalEntryDTO correction = journalEntryService.createCorrection(
                    original.getId(), correctionReq, "sistema");
            log.info("HU-AP-13 E2 listener: ajuste contable DRAFT id={} generado para factura {} (JE original {} POSTED)",
                    correction.getId(), event.getInvoiceId(), original.getId());

            // QA-BLOQUE-AY HU-AP-13 E2 (2026-05-06): registrar en auditoria la
            // generacion del comprobante de ajuste para que el contador pueda
            // rastrear desde AU el flujo completo: edicion factura -> evento ->
            // comprobante de correccion. Antes la generacion solo quedaba en log
            // de servidor y QA reporto que CG no notificaba nada.
            try {
                auditPublisher.publishCreate(AuditModule.CG, "JournalEntry", correction.getId(),
                        "Comprobante de ajuste generado por edicion de factura AP "
                        + event.getResolutionInvoice() + " | JE original=" + original.getId()
                        + " | factura=" + event.getInvoiceId());
            } catch (RuntimeException auditEx) {
                log.warn("HU-AP-13 E2 listener: no se pudo registrar audit log del ajuste {}: {}",
                        correction.getId(), auditEx.getMessage());
            }
        } catch (Exception e) {
            log.warn("HU-AP-13 E2 listener: no se pudo generar ajuste contable para factura {}: {}",
                    event.getInvoiceId(), e.getMessage());
        }
    }
}
