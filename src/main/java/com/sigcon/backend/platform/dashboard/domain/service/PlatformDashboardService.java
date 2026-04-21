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
                .build();
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
