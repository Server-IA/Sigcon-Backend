package com.sigcon.backend.general.accounting.journal.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;

/**
 * Repositorio para la entidad JournalEntry (cabecera de asientos contables).
 */
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long>,
        JpaSpecificationExecutor<JournalEntry> {

    /**
     * Obtiene el numero de asiento maximo para un anio fiscal dado.
     * Usado para generar el consecutivo automatico.
     */
    @Query("SELECT MAX(je.entryNumber) FROM JournalEntry je WHERE je.fiscalYear = :fiscalYear AND je.deletedAt IS NULL")
    Long findMaxEntryNumberByFiscalYear(@Param("fiscalYear") Integer fiscalYear);

    /**
     * Cuenta asientos por periodo y estado (excluyendo eliminados logicamente).
     * Usado por AccountingPeriodService para validar cierre de periodo.
     */
    long countByPeriodYearAndPeriodMonthAndStatusAndDeletedAtIsNull(
            Integer year, Integer month, JournalEntryStatus status);

    /**
     * Busca asientos por modulo origen e id de origen (excluyendo eliminados logicamente).
     */
    List<JournalEntry> findBySourceModuleAndSourceIdAndDeletedAtIsNull(
            JournalSourceModule module, Long sourceId);

    /**
     * Obtiene asientos contabilizados de un periodo, ordenados por fecha y numero.
     * Usado por el Libro Diario.
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.periodYear = :year AND je.periodMonth = :month "
         + "AND je.status = :status AND je.deletedAt IS NULL ORDER BY je.entryDate, je.entryNumber")
    List<JournalEntry> findByPeriodAndStatus(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("status") JournalEntryStatus status);

    /**
     * Obtiene todos los asientos contabilizados hasta un periodo dado (inclusive).
     * Usado para calcular saldos acumulados en estados financieros.
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.status = :status AND je.deletedAt IS NULL "
         + "AND (je.periodYear < :year OR (je.periodYear = :year AND je.periodMonth <= :month))")
    List<JournalEntry> findPostedUpToPeriod(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("status") JournalEntryStatus status);

    /**
     * Obtiene asientos contabilizados estrictamente antes de un periodo.
     * Usado para calcular saldos anteriores en el Balance de Comprobacion.
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.status = :status AND je.deletedAt IS NULL "
         + "AND (je.periodYear < :year OR (je.periodYear = :year AND je.periodMonth < :month))")
    List<JournalEntry> findPostedBeforePeriod(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("status") JournalEntryStatus status);
}
