package com.sigcon.backend.platform.dashboard.domain.service;

import com.sigcon.backend.platform.dashboard.application.PlatformDashboardDTO;
import com.sigcon.backend.platform.dashboard.application.PlatformDashboardDTO.CompanyStat;
import com.sigcon.backend.platform.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio que calcula los KPIs del dashboard de plataforma (HU-PA-PLAT-06).
 *
 * <p>Todas las queries son nativas (SQL) y corren con {@link TenantContext}
 * en modo PLATFORM_ADMIN para saltar el tenant filter y agregar datos
 * cross-empresa. El {@code TenantFilterAspect} solo activa el filter cuando
 * hay un {@code companyId} presente y el usuario NO es platform admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformDashboardService {

    @PersistenceContext
    private EntityManager em;

    /**
     * QA Bloque PA Bug 52 (HU-PA-15 E5, 2026-05-09): inyectar el scheduler de
     * vencimiento de permisos temporales para exponer su ultimo run en el
     * dashboard de plataforma. Required=false: si el bean no esta disponible
     * (entornos minimos), el dashboard devuelve null y no rompe.
     */
    private com.sigcon.backend.parametrization.temporary_permissions.domain.service.TemporaryPermissionExpiryScheduler tempPermScheduler;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setTempPermScheduler(
            com.sigcon.backend.parametrization.temporary_permissions.domain.service.TemporaryPermissionExpiryScheduler scheduler) {
        this.tempPermScheduler = scheduler;
    }

    /**
     * Calcula los KPIs del dashboard.
     *
     * @return DTO con conteos agregados + top-5 empresas por volumen
     */
    @Transactional(readOnly = true)
    public PlatformDashboardDTO getDashboard() {
        long active = scalar("SELECT COUNT(*) FROM companies WHERE status = 'ACTIVE' AND deleted_at IS NULL");
        long inactive = scalar("SELECT COUNT(*) FROM companies WHERE status = 'INACTIVE' AND deleted_at IS NULL");
        long tenantUsers = scalar("SELECT COUNT(*) FROM users WHERE company_id IS NOT NULL AND deleted_at IS NULL");
        long platformAdmins = scalar("SELECT COUNT(*) FROM users WHERE platform_role IS NOT NULL AND deleted_at IS NULL");

        LocalDate cutoff = LocalDate.now().minusMonths(6);
        long jeLast6 = scalar(
                "SELECT COUNT(*) FROM journal_entries WHERE created_at >= ? AND deleted_at IS NULL",
                cutoff.atStartOfDay());

        long aaefTotal = scalar("SELECT COUNT(*) FROM integration_batches WHERE deleted_at IS NULL");
        long ackFailed = scalar(
                "SELECT COUNT(*) FROM integration_batches WHERE status = 'ACK_FAILED' AND deleted_at IS NULL");

        List<CompanyStat> topJe = topStats(
                "SELECT c.id, c.business_name, COUNT(je.id) AS value "
              + "FROM companies c LEFT JOIN journal_entries je "
              + "  ON je.company_id = c.id AND je.deleted_at IS NULL "
              + "WHERE c.deleted_at IS NULL "
              + "GROUP BY c.id, c.business_name ORDER BY value DESC, c.id ASC LIMIT 5");

        List<CompanyStat> failedAck = topStats(
                "SELECT c.id, c.business_name, COUNT(ib.id) AS value "
              + "FROM companies c JOIN integration_batches ib "
              + "  ON ib.company_id = c.id AND ib.status = 'ACK_FAILED' AND ib.deleted_at IS NULL "
              + "WHERE c.deleted_at IS NULL "
              + "GROUP BY c.id, c.business_name ORDER BY value DESC LIMIT 10");

        // QA Bloque PA Bug 52 (HU-PA-15 E5): incluir estado del scheduler de vencimiento
        // de permisos temporales en el panel de plataforma.
        java.util.Map<String, Object> tempPermSchedulerStatus = null;
        if (tempPermScheduler != null) {
            try {
                var s = tempPermScheduler.getLastRun();
                tempPermSchedulerStatus = new java.util.LinkedHashMap<>();
                if (s == null) {
                    tempPermSchedulerStatus.put("status", "NEVER_RAN");
                } else {
                    tempPermSchedulerStatus.put("status", s.status);
                    tempPermSchedulerStatus.put("expiredCount", s.expiredCount);
                    tempPermSchedulerStatus.put("notifiedCount", s.notifiedCount);
                    tempPermSchedulerStatus.put("durationMs", s.durationMs);
                    tempPermSchedulerStatus.put("startedAt", s.startedAt);
                    tempPermSchedulerStatus.put("endedAt", s.endedAt);
                    if (s.errorMessage != null) tempPermSchedulerStatus.put("errorMessage", s.errorMessage);
                }
            } catch (RuntimeException ignored) {
                tempPermSchedulerStatus = java.util.Map.of("status", "ERROR_FETCHING_STATUS");
            }
        }

        // QA Bloque PA Bug 68 (HU-PA-PLAT-06 E2): salud de servicios (DB + AAEF)
        java.util.Map<String, Object> servicesHealth = computeServicesHealth();
        // QA Bloque PA Bug 68 (HU-PA-PLAT-06 E3): metricas de uso
        java.util.Map<String, Object> usageMetrics = computeUsageMetrics();

        return PlatformDashboardDTO.builder()
                .activeCompanies(active)
                .inactiveCompanies(inactive)
                .totalTenantUsers(tenantUsers)
                .totalPlatformAdmins(platformAdmins)
                .journalEntriesLast6Months(jeLast6)
                .totalAaefBatches(aaefTotal)
                .ackFailedBatches(ackFailed)
                .topCompaniesByJe(topJe)
                .companiesWithFailedAck(failedAck)
                .tempPermSchedulerStatus(tempPermSchedulerStatus)
                .servicesHealth(servicesHealth)
                .usageMetrics(usageMetrics)
                .build();
    }

    /**
     * QA Bloque PA Bug 68 (HU-PA-PLAT-06 E2): estado tiempo real de servicios.
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> computeServicesHealth() {
        java.util.Map<String, Object> h = new java.util.LinkedHashMap<>();
        // BD: latencia de un SELECT trivial + activity count
        long t0 = System.currentTimeMillis();
        Number activeConn = null;
        try {
            activeConn = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM pg_stat_activity WHERE state IS NOT NULL").getSingleResult();
        } catch (Exception ignored) {}
        long dbLatencyMs = System.currentTimeMillis() - t0;
        java.util.Map<String, Object> db = new java.util.LinkedHashMap<>();
        db.put("status", dbLatencyMs < 100 ? "OK" : (dbLatencyMs < 500 ? "WARNING" : "CRITICAL"));
        db.put("latencyMs", dbLatencyMs);
        db.put("activeConnections", activeConn != null ? activeConn.longValue() : null);
        h.put("database", db);
        // AAEF: errores en ultima hora + latencia promedio ultimos 15 min
        java.util.Map<String, Object> aaef = new java.util.LinkedHashMap<>();
        try {
            Number errorsLastHour = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM integration_batches WHERE status='ACK_FAILED' " +
                "AND received_at > NOW() - INTERVAL '1 hour' AND deleted_at IS NULL").getSingleResult();
            Number latencyP95 = (Number) em.createNativeQuery(
                "SELECT EXTRACT(EPOCH FROM percentile_cont(0.95) WITHIN GROUP " +
                "(ORDER BY ack_sent_at - received_at)) FROM integration_batches " +
                "WHERE received_at > NOW() - INTERVAL '15 minutes' AND ack_sent_at IS NOT NULL").getSingleResult();
            long errors = errorsLastHour != null ? errorsLastHour.longValue() : 0;
            aaef.put("status", errors == 0 ? "OK" : (errors < 5 ? "WARNING" : "CRITICAL"));
            aaef.put("errorsLastHour", errors);
            aaef.put("latencyP95Seconds15m", latencyP95);
        } catch (Exception ex) {
            aaef.put("status", "WARNING");
            aaef.put("errorMessage", ex.getMessage());
        }
        h.put("aaef", aaef);
        return h;
    }

    /**
     * QA Bloque PA Bug 68 (HU-PA-PLAT-06 E3): metricas de uso (sesiones activas,
     * tiempos de respuesta, errores 5xx).
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> computeUsageMetrics() {
        java.util.Map<String, Object> u = new java.util.LinkedHashMap<>();
        // Sesiones activas: aproximacion via login events recientes (ultimas 8h)
        try {
            Number activeSessions = (Number) em.createNativeQuery(
                "SELECT COUNT(DISTINCT user_id) FROM audit_logs " +
                "WHERE action = 'LOGIN' AND timestamp > NOW() - INTERVAL '8 hours'").getSingleResult();
            u.put("activeSessionsApprox", activeSessions != null ? activeSessions.longValue() : 0);
        } catch (Exception ignored) {
            u.put("activeSessionsApprox", null);
        }
        // Errores 5xx en ultima hora desde audit_logs (severidad CRITICAL)
        try {
            Number errors5xx = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM audit_logs WHERE severity = 'CRITICAL' " +
                "AND timestamp > NOW() - INTERVAL '1 hour'").getSingleResult();
            u.put("errors5xxLastHour", errors5xx != null ? errors5xx.longValue() : 0);
        } catch (Exception ignored) {
            u.put("errors5xxLastHour", null);
        }
        u.put("note", "Metricas de p50/p95/p99 requieren APM externo (Prometheus/Grafana). "
                    + "Aqui se exponen aproximaciones via audit_logs.");
        return u;
    }

    private long scalar(String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
        Object r = q.getSingleResult();
        return r == null ? 0L : ((Number) r).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<CompanyStat> topStats(String sql) {
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        List<CompanyStat> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(CompanyStat.builder()
                    .companyId(((Number) r[0]).longValue())
                    .companyName((String) r[1])
                    .value(((Number) r[2]).longValue())
                    .build());
        }
        return out;
    }
}
