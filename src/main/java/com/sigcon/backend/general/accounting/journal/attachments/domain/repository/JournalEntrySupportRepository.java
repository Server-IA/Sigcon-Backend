package com.sigcon.backend.general.accounting.journal.attachments.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.general.accounting.journal.attachments.domain.model.JournalEntrySupport;

/**
 * Repositorio JPA para {@link JournalEntrySupport}.
 * HU-CG-05A: persistencia de comprobantes adjuntos a asientos contables.
 */
public interface JournalEntrySupportRepository extends JpaRepository<JournalEntrySupport, Long> {

    /**
     * Lista los adjuntos vigentes de un asiento contable, ordenados por fecha
     * de carga descendente (mas recientes primero).
     */
    List<JournalEntrySupport> findByJournalEntryIdAndDeletedAtIsNullOrderByUploadedAtDesc(Long journalEntryId);

    /**
     * Conteo rapido de adjuntos vigentes — usado por HU-CG-02A E2 (validar que
     * un asiento tenga al menos un soporte antes de contabilizarlo).
     */
    long countByJournalEntryIdAndDeletedAtIsNull(Long journalEntryId);
}
