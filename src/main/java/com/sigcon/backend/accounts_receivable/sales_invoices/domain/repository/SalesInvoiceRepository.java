package com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus;

/**
 * Repositorio de facturas de venta (FV).
 */
public interface SalesInvoiceRepository
        extends JpaRepository<SalesInvoice, Long>, JpaSpecificationExecutor<SalesInvoice> {

    /** AR-01A: validar unicidad del consecutivo. */
    boolean existsByInvoiceNumberAndDeletedAtIsNull(String invoiceNumber);

    Optional<SalesInvoice> findByInvoiceNumberAndDeletedAtIsNull(String invoiceNumber);

    /**
     * HU-INT-RF-05: busca una FV por su externalId (DocumentId de AAEF).
     * Usado para resolver RelatedInvoiceId de transacciones PAY recibidas por integracion.
     */
    Optional<SalesInvoice> findByIntegrationSource_ExternalIdAndDeletedAtIsNull(String externalId);

    /**
     * AR-01A: obtiene el ultimo consecutivo emitido en un año fiscal
     * para calcular el siguiente numero de factura.
     */
    @Query("SELECT MAX(s.invoiceNumber) FROM SalesInvoice s "
         + "WHERE YEAR(s.invoiceDate) = :year AND s.deletedAt IS NULL")
    String findMaxInvoiceNumberByYear(Integer year);

    /** AR-06/AR-10: facturas vencidas con saldo pendiente. */
    @Query("SELECT s FROM SalesInvoice s WHERE s.balanceDue > 0 AND s.dueDate < :today "
         + "AND s.status NOT IN ('VOIDED','PAID','SETTLED') AND s.deletedAt IS NULL")
    List<SalesInvoice> findOverdueInvoices(@Param("today") LocalDate today);

    /** AR-10: facturas proximas a vencer en un rango de dias. */
    @Query("SELECT s FROM SalesInvoice s WHERE s.balanceDue > 0 "
         + "AND s.dueDate BETWEEN :start AND :end "
         + "AND s.status NOT IN ('VOIDED','PAID','SETTLED') AND s.deletedAt IS NULL")
    List<SalesInvoice> findUpcomingInvoices(@Param("start") LocalDate start,
                                             @Param("end") LocalDate end);

    /** AR-05: listado por rango de fechas. */
    @Query("SELECT s FROM SalesInvoice s WHERE s.invoiceDate BETWEEN :startDate AND :endDate "
         + "AND s.deletedAt IS NULL")
    List<SalesInvoice> findByInvoiceDateBetween(@Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    /** AR-05: listado por estado y rango de fechas. */
    @Query("SELECT s FROM SalesInvoice s WHERE s.status = :status "
         + "AND s.invoiceDate BETWEEN :startDate AND :endDate AND s.deletedAt IS NULL")
    List<SalesInvoice> findByStatusAndDateRange(@Param("status") SalesInvoiceStatus status,
                                                 @Param("startDate") LocalDate startDate,
                                                 @Param("endDate") LocalDate endDate);

    /** AR-05: listado por cliente y rango de fechas. */
    @Query("SELECT s FROM SalesInvoice s WHERE s.thirdParty.id = :thirdPartyId "
         + "AND s.invoiceDate BETWEEN :startDate AND :endDate AND s.deletedAt IS NULL")
    List<SalesInvoice> findByThirdPartyAndDateRange(@Param("thirdPartyId") Long thirdPartyId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    /** AR-12: facturas abiertas de un cliente. */
    @Query("SELECT s FROM SalesInvoice s WHERE s.thirdParty.id = :thirdPartyId "
         + "AND s.balanceDue > 0 AND s.status NOT IN ('VOIDED','PAID','SETTLED') "
         + "AND s.deletedAt IS NULL")
    List<SalesInvoice> findOpenInvoicesByThirdParty(@Param("thirdPartyId") Long thirdPartyId);

    /** AR-12: saldo total pendiente de un cliente. */
    @Query("SELECT COALESCE(SUM(s.balanceDue),0) FROM SalesInvoice s "
         + "WHERE s.thirdParty.id = :thirdPartyId AND s.balanceDue > 0 "
         + "AND s.status NOT IN ('VOIDED','PAID','SETTLED') AND s.deletedAt IS NULL")
    BigDecimal sumBalanceDueByThirdParty(@Param("thirdPartyId") Long thirdPartyId);
}
