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
        try {
            int count = salesInvoiceService.updateOverdueInvoices();
            log.info("Tarea programada completada: {} facturas marcadas como OVERDUE", count);
        } catch (Exception e) {
            log.error("Error ejecutando tarea programada de vencimientos: {}", e.getMessage(), e);
        }
    }
}
