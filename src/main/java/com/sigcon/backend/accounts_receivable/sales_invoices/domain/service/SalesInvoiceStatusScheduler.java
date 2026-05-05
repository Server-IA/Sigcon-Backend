package com.sigcon.backend.accounts_receivable.sales_invoices.domain.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AR-06: Scheduler diario que actualiza los estados de las facturas de venta.
 *
 * <p>Ejecuta diariamente a la 1:00 AM hora del servidor y marca como
 * OVERDUE todas las facturas con saldo pendiente cuya fecha de vencimiento
 * sea anterior a la fecha actual.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesInvoiceStatusScheduler {

    private final SalesInvoiceService salesInvoiceService;

    /**
     * Ejecuta la actualizacion automatica de vencimientos.
     * Cron: segundos minutos hora dia-mes mes dia-semana.
     */
    @Scheduled(cron = "0 0 1 * * *")
    public void updateOverdueInvoicesJob() {
        log.info("Iniciando tarea programada de actualizacion de facturas vencidas");
        // Multi-tenant (Bloque G fix): scheduler sin TenantContext. Activamos
        // modo PLATFORM_ADMIN para que el @Filter de Hibernate NO restrinja la
        // query y se procesen todas las facturas de todas las empresas.
        com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(true);
        try {
            int count = salesInvoiceService.updateOverdueInvoices();
            log.info("Tarea programada completada: {} facturas marcadas como OVERDUE", count);
        } catch (Exception e) {
            log.error("Error ejecutando tarea programada de vencimientos: {}", e.getMessage(), e);
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
    }

    /**
     * HU-AR-06 E1 + E3: pasada de RECONCILIACION integral, 1:30 AM.
     * Corrige status de facturas que no coincidan con su balanceDue real
     * (ej. PAID con saldo > 0, OVERDUE con saldo = 0, etc.).
     * Complementa al job de OVERDUE: cubre los demas estados.
     */
    @Scheduled(cron = "0 30 1 * * *")
    public void reconcileInvoiceStatusesJob() {
        log.info("Iniciando reconciliacion de estados AR-06");
        com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(true);
        try {
            int count = salesInvoiceService.reconcileInvoiceStatuses();
            log.info("Reconciliacion AR-06 completada: {} facturas corregidas", count);
        } catch (Exception e) {
            log.error("Error en reconciliacion AR-06: {}", e.getMessage(), e);
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
    }
}
