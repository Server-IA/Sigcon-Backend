package com.sigcon.backend.invoices.ap_alerts.domain.service;

import com.sigcon.backend.invoices.ap_alerts.application.ApAlertDTO;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * AP-11: Servicio de alertas de facturas de compra proximas a vencer o vencidas.
 *
 * <p>Calcula dias hasta vencimiento basado en {@code invoiceDate + invoiceDueDay}
 * (patron usado en AP-12 aging report). Solo considera facturas en estados
 * abiertos (PENDING / PARTIALLY_PAID) con saldo pendiente mayor a cero.
 *
 * <p>Niveles de severidad:
 * <ul>
 *   <li>{@code CRITICAL} — vencidas (daysUntilDue < 0)</li>
 *   <li>{@code WARNING}  — proximas a vencer (0 &le; daysUntilDue &le; 3)</li>
 *   <li>{@code INFO}     — proximas a vencer (4 &le; daysUntilDue &le; 7)</li>
 * </ul>
 *
 * <p>Adicionalmente expone un scheduler diario (1:30 AM) que registra en el log
 * el conteo y monto de facturas vencidas, util para integracion futura con
 * notificaciones por email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApAlertsService {

    private final InvoiceRepository invoiceRepository;

    /** Dias por defecto de vencimiento si la factura no especifica invoiceDueDay. */
    private static final int DEFAULT_DUE_DAYS = 30;

    /**
     * Facturas proximas a vencer dentro de los proximos N dias.
     *
     * @param daysAhead horizonte en dias (1 a 90). Si null o invalido usa 7.
     * @return lista ordenada por fecha de vencimiento ascendente
     */
    public List<ApAlertDTO> getUpcomingInvoices(Integer daysAhead) {
        int horizon = (daysAhead == null || daysAhead < 1 || daysAhead > 90) ? 7 : daysAhead;
        LocalDate today = LocalDate.now();

        return getOpenInvoices().stream()
                .map(inv -> toAlert(inv, today))
                .filter(a -> a.getDaysUntilDue() != null
                        && a.getDaysUntilDue() >= 0
                        && a.getDaysUntilDue() <= horizon)
                .sorted(Comparator.comparing(ApAlertDTO::getDueDate))
                .toList();
    }

    /**
     * Facturas vencidas (daysUntilDue negativo).
     *
     * @return lista ordenada por fecha de vencimiento ascendente (mas vencidas primero)
     */
    public List<ApAlertDTO> getOverdueInvoices() {
        LocalDate today = LocalDate.now();

        return getOpenInvoices().stream()
                .map(inv -> toAlert(inv, today))
                .filter(a -> a.getDaysUntilDue() != null && a.getDaysUntilDue() < 0)
                .sorted(Comparator.comparing(ApAlertDTO::getDueDate))
                .toList();
    }

    /**
     * Scheduler diario 1:30 AM: registra resumen de facturas vencidas en log
     * (hook para notificaciones futuras).
     */
    @Scheduled(cron = "0 30 1 * * *")
    public void logDailyOverdueSummary() {
        // Multi-tenant (Bloque G fix): scheduler corre sin TenantContext. En modo
        // PLATFORM_ADMIN el @Filter se deshabilita y vemos facturas de todas las
        // empresas (un log agregado a nivel plataforma).
        com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(true);
        try {
            List<ApAlertDTO> overdue = getOverdueInvoices();
            if (overdue.isEmpty()) {
                log.info("AP-11 scheduler: no hay facturas vencidas hoy");
                return;
            }
            BigDecimal total = overdue.stream()
                    .map(a -> a.getBalanceDue() != null ? a.getBalanceDue() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            log.warn("AP-11 scheduler: {} factura(s) de compra vencidas. Total pendiente: {}",
                    overdue.size(), total);
        } catch (Exception e) {
            log.error("AP-11 scheduler: fallo al calcular resumen de facturas vencidas", e);
        } finally {
            com.sigcon.backend.platform.tenant.TenantContext.clear();
        }
    }

    // ===== helpers =====

    /** Devuelve facturas en estados abiertos (PENDING / PARTIALLY_PAID) con saldo pendiente. */
    private List<Invoices> getOpenInvoices() {
        EnumSet<StatusesInvoices> openStates = EnumSet.of(
                StatusesInvoices.PENDING, StatusesInvoices.PARTIALLY_PAID);

        List<Invoices> result = new ArrayList<>();
        for (Invoices inv : invoiceRepository.findAll()) {
            if (inv.getDeletedAt() != null) continue;
            if (inv.getStatus() == null || !openStates.contains(inv.getStatus())) continue;
            if (inv.getBalanceDue() == null || inv.getBalanceDue() <= 0) continue;
            result.add(inv);
        }
        return result;
    }

    /** Convierte una factura a DTO de alerta con calculo de dias y severidad. */
    private ApAlertDTO toAlert(Invoices inv, LocalDate today) {
        int dueDays = inv.getInvoiceDueDay() != null ? inv.getInvoiceDueDay() : DEFAULT_DUE_DAYS;
        LocalDate dueDate = inv.getInvoiceDate() != null
                ? inv.getInvoiceDate().plusDays(dueDays)
                : today;
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);

        String severity;
        if (daysUntilDue < 0)      severity = "CRITICAL";
        else if (daysUntilDue <= 3) severity = "WARNING";
        else                        severity = "INFO";

        String supplierNit = null;
        String supplierName = null;
        try {
            if (inv.getThirdParty() != null) {
                supplierNit = inv.getThirdParty().getNit();
                supplierName = inv.getThirdParty().getBusinessName();
            }
        } catch (Exception ignored) {}

        return ApAlertDTO.builder()
                .invoiceId(inv.getId())
                .invoiceNumber(inv.getResolutionInvoice())
                .supplierNit(supplierNit)
                .supplierName(supplierName)
                .invoiceDate(inv.getInvoiceDate())
                .dueDate(dueDate)
                .daysUntilDue(daysUntilDue)
                .balanceDue(BigDecimal.valueOf(inv.getBalanceDue()))
                .status(inv.getStatus() != null ? inv.getStatus().name() : null)
                .severity(severity)
                .build();
    }
}
