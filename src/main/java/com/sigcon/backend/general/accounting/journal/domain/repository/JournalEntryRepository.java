package com.sigcon.backend.general.accounting.journal.domain.repository;

import java.time.LocalDate;
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
     * Asientos por fecha y estado. Usado para detectar duplicados de comprobantes
     * (HU-CG-01A E9) — un asiento es duplicado si comparte fecha + totales + descripcion
     * con otro POSTED.
     */
    List<JournalEntry> findByEntryDateAndStatus(LocalDate entryDate, JournalEntryStatus status);

    /**
     * HU-CG-08C E2: dado un comprobante original que fue reversado, encuentra el
     * comprobante REV-XXXX que lo neutralizo. Devuelve el primero (deberia ser unico).
     */
    java.util.Optional<JournalEntry> findFirstByReversalOf_IdAndDeletedAtIsNull(Long originalId);

    /**
     * HU-CG-07B / HU-CG-08C: dado un comprobante CONTABILIZADO que fue corregido,
     * encuentra el comprobante COR-XXXX vinculado.
     */
    java.util.Optional<JournalEntry> findFirstByCorrectionOf_IdAndDeletedAtIsNull(Long originalId);

    /**
     * Busca asientos por modulo origen e id de origen (excluyendo eliminados logicamente).
     */
    List<JournalEntry> findBySourceModuleAndSourceIdAndDeletedAtIsNull(
            JournalSourceModule module, Long sourceId);

    /**
     * Obtiene asientos contabilizados de un periodo, ordenados por fecha y numero.
     * Usado por el Libro Diario.
     *
     * <p>HU-CG-08A E1: incluye tambien asientos en estado REVERSED. La inmutabilidad
     * contable exige que un asiento contabilizado NO desaparezca de los libros
     * oficiales; cuando se reversa, el original sigue visible junto con su contra-
     * asiento (REV-XXXX) y la suma neta queda en $0. Esta es la lectura correcta
     * segun la NIC 1 y el Decreto 2649/93. Si se quisiera limitar a estrictamente
     * POSTED, usar findByPeriodAndExactStatus.</p>
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.periodYear = :year AND je.periodMonth = :month "
         + "AND je.status IN (:status, com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus.REVERSED) "
         + "AND je.deletedAt IS NULL ORDER BY je.entryDate, je.entryNumber")
    List<JournalEntry> findByPeriodAndStatus(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("status") JournalEntryStatus status);

    /**
     * Obtiene todos los asientos contabilizados hasta un periodo dado (inclusive).
     * Usado para calcular saldos acumulados en estados financieros.
     * Incluye REVERSED — ver nota en findByPeriodAndStatus (HU-CG-08A E1).
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.deletedAt IS NULL "
         + "AND je.status IN (:status, com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus.REVERSED) "
         + "AND (je.periodYear < :year OR (je.periodYear = :year AND je.periodMonth <= :month))")
    List<JournalEntry> findPostedUpToPeriod(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("status") JournalEntryStatus status);

    /**
     * Obtiene asientos contabilizados estrictamente antes de un periodo.
     * Usado para calcular saldos anteriores en el Balance de Comprobacion.
     * Incluye REVERSED — ver nota en findByPeriodAndStatus (HU-CG-08A E1).
     */
    @Query("SELECT je FROM JournalEntry je WHERE je.deletedAt IS NULL "
         + "AND je.status IN (:status, com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus.REVERSED) "
         + "AND (je.periodYear < :year OR (je.periodYear = :year AND je.periodMonth < :month))")
    List<JournalEntry> findPostedBeforePeriod(
            @Param("year") Integer year,
            @Param("month") Integer month,
            @Param("status") JournalEntryStatus status);

    /**
     * QA-BLOQUE-AP (2026-04-29) v2 (2026-04-30): busca JournalEntries POSTED en
     * ventana de fechas. NO filtra por cuenta contable: el contador puede
     * emparejar manualmente con cualquier asiento del periodo, no solo con los
     * que afectan directamente la cuenta del banco. La validacion de
     * coherencia contable queda al usuario.
     *
     * <p>El filtro estricto previo (por accounting_account_id) era demasiado
     * restrictivo: en empresas QA los JEs de nomina, factura compra, etc.
     * afectan cuentas distintas a la del banco y por tanto no aparecian aunque
     * cuadraran en monto y fecha. El usuario en pantalla veia 6 JEs visibles
     * pero el modal decia "no hay asientos disponibles" — confuso.
     *
     * @param companyId filtro de tenant
     * @param from      fecha minima (inclusive)
     * @param to        fecha maxima (inclusive)
     */
    @Query("SELECT je FROM JournalEntry je "
         + "WHERE je.deletedAt IS NULL AND je.companyId = :companyId "
         + "AND je.status = com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus.POSTED "
         + "AND je.entryDate >= :from AND je.entryDate <= :to "
         + "ORDER BY je.entryDate DESC, je.entryNumber DESC")
    List<JournalEntry> findReconciliationCandidatesByAccount(
            @Param("companyId") Long companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * QA-BLOQUE-AP v2: chequeo auxiliar — un JE afecta o no la cuenta del banco.
     * El frontend usa esto via flag affectsAccount en el DTO para mostrar un
     * badge visual al contador.
     */
    @Query("SELECT COUNT(jel) > 0 FROM JournalEntryLine jel "
         + "WHERE jel.journalEntry.id = :journalEntryId "
         + "AND jel.accountingAccount.id = :accountingAccountId")
    boolean existsLineForAccount(
            @Param("journalEntryId") Long journalEntryId,
            @Param("accountingAccountId") Long accountingAccountId);
}
