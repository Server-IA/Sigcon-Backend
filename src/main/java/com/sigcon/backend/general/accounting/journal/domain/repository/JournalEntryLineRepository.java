package com.sigcon.backend.general.accounting.journal.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntryLine;

/**
 * Repositorio para la entidad JournalEntryLine (lineas de detalle de asientos contables).
 */
public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, Long> {

    /**
     * Obtiene las lineas de un asiento ordenadas por numero de linea.
     */
    List<JournalEntryLine> findByJournalEntryIdOrderByLineOrder(Long journalEntryId);

    /**
     * Verifica si existe al menos una linea contable que referencie a la cuenta del PUC indicada
     * (a traves de la cuenta contable asociada). Usado por el reporte de validacion masiva
     * PUC (HU-CG-09D) para detectar cuentas INACTIVE con movimientos.
     *
     * @param pucAccountId id de la cuenta PUC
     * @return true si hay movimientos contables asociados
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT CASE WHEN COUNT(l) > 0 THEN TRUE ELSE FALSE END FROM JournalEntryLine l "
          + "WHERE l.accountingAccount.pucAccount.id = :pucAccountId")
    boolean existsMovementsByPucAccountId(
            @org.springframework.data.repository.query.Param("pucAccountId") Long pucAccountId);

    /**
     * CFG-08 / CG-09C: calcula el saldo neto (sum debitos - sum creditos) de una
     * cuenta contable considerando solo asientos CONTABILIZADOS. Usado para
     * validar que la cuenta no puede inactivarse si tiene saldo distinto de cero.
     *
     * @param accountingAccountId id de la cuenta contable
     * @return saldo neto (puede ser negativo si la naturaleza es credito)
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(l.debitAmount) - SUM(l.creditAmount), 0) "
          + "FROM JournalEntryLine l "
          + "WHERE l.accountingAccount.id = :accountingAccountId "
          + "AND l.journalEntry.status = com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus.POSTED")
    java.math.BigDecimal netBalanceByAccountingAccountId(
            @org.springframework.data.repository.query.Param("accountingAccountId") Long accountingAccountId);

    /**
     * CFG-RF-08 E2/E4: cuenta cuantas lineas de asientos contables (no anulados)
     * referencian a una cuenta contable. Usado para impedir su eliminacion logica.
     * `JournalEntryLine` no tiene soft delete propio; se filtra por el JE padre
     * que NO este reversado/anulado.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(l) FROM JournalEntryLine l "
          + "WHERE l.accountingAccount.id = :accountingAccountId "
          + "AND l.journalEntry.status <> com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus.REVERSED")
    long countByAccountingAccount_IdAndDeletedAtIsNull(
            @org.springframework.data.repository.query.Param("accountingAccountId") Long accountingAccountId);
}
