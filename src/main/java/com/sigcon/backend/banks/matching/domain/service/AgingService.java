package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditLogService;
import com.sigcon.backend.banks.checks.domain.model.Check;
import com.sigcon.backend.banks.checks.domain.model.enums.CheckStatus;
import com.sigcon.backend.banks.checks.domain.repository.CheckRepository;
import com.sigcon.backend.banks.matching.domain.model.PartidaConciliatoria;
import com.sigcon.backend.banks.matching.domain.repository.PartidaConciliatoriaRepository;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/**
 * BNK-HU-074: antigüedad de partidas conciliatorias pendientes, buckets de
 * severidad, alertas a 60/90 días, reporte + dashboard, y monitoreo de cheques
 * próximos a caducar (Art. 721 C.Co.).
 *
 * STAND-IN documentado: las "alertas" se registran en el log de auditoría
 * (PARTIDA_PENDIENTE_60D/90D, CHEQUE_PROXIMO_CADUCAR) y quedan visibles en el
 * dashboard/reporte. El envío por correo a los destinatarios (conciliador/
 * supervisor/revisor) no aplica por falta de SMTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgingService {

    private final PartidaConciliatoriaRepository partidaRepo;
    private final CheckRepository checkRepo;
    private final CompanyRepository companyRepo;
    private final AuditLogService auditLogService;

    /** HU-074 E2: bucket de severidad por antigüedad. */
    public String bucket(Integer dias) {
        int d = dias == null ? 0 : dias;
        if (d <= 30) return "NORMAL";
        if (d <= 60) return "ATENCION";
        if (d <= 90) return "ADVERTENCIA";
        return "CRITICA";
    }

    /** HU-074 E1: recalcula días de antigüedad de las partidas PENDIENTE del tenant. */
    @Transactional
    public int recalcForCurrentTenant() {
        LocalDate hoy = LocalDate.now();
        List<PartidaConciliatoria> pend = partidaRepo.findByEstadoAndDeletedAtIsNullOrderByIdDesc("PENDIENTE");
        for (PartidaConciliatoria p : pend) {
            LocalDate origen = p.getFechaOrigen() != null ? p.getFechaOrigen()
                    : (p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : hoy);
            p.setDiasAntiguedad((int) ChronoUnit.DAYS.between(origen, hoy));
            partidaRepo.save(p);
        }
        return pend.size();
    }

    /** HU-074 E3/E4: genera alertas idempotentes a 60 y 90 días. */
    @Transactional
    public Map<String, Object> runAlertsForCurrentTenant() {
        int a60 = 0, a90 = 0;
        for (PartidaConciliatoria p : partidaRepo.findByEstadoAndDeletedAtIsNullOrderByIdDesc("PENDIENTE")) {
            int dias = p.getDiasAntiguedad() != null ? p.getDiasAntiguedad() : 0;
            if (dias >= 90 && p.getAlerta90dAt() == null) {
                auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.CRITICAL,
                        "PartidaConciliatoria", p.getId(),
                        "PARTIDA_PENDIENTE_90D: partida #" + p.getId() + " (" + p.getTipo() + ") lleva " + dias
                                + " días sin resolver. Destinatarios: conciliador + supervisor + revisor fiscal.",
                        null, "{\"alerta\":\"PARTIDA_PENDIENTE_90D\",\"dias\":" + dias + "}", null);
                p.setAlerta90dAt(LocalDateTime.now());
                partidaRepo.save(p);
                a90++;
            } else if (dias >= 60 && p.getAlerta60dAt() == null) {
                auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.HIGH,
                        "PartidaConciliatoria", p.getId(),
                        "PARTIDA_PENDIENTE_60D: partida #" + p.getId() + " (" + p.getTipo() + ") lleva " + dias
                                + " días sin resolver. Destinatario: conciliador.",
                        null, "{\"alerta\":\"PARTIDA_PENDIENTE_60D\",\"dias\":" + dias + "}", null);
                p.setAlerta60dAt(LocalDateTime.now());
                partidaRepo.save(p);
                a60++;
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("alertas60d", a60);
        r.put("alertas90d", a90);
        return r;
    }

    /** HU-074 E7: cheques EMITIDO próximos a caducar (caducan a 6 meses, alerta <30 días). */
    @Transactional
    public List<Map<String, Object>> chequesProximosCaducar() {
        LocalDate hoy = LocalDate.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Check c : checkRepo.findAll()) {
            if (c.getDeletedAt() != null) continue;
            if (c.getStatusCheck() != CheckStatus.EMITIDO || c.getIssueDate() == null) continue;
            LocalDate caducidad = c.getIssueDate().plusMonths(6);
            long diasParaCaducar = ChronoUnit.DAYS.between(hoy, caducidad);
            if (diasParaCaducar >= 0 && diasParaCaducar <= 30) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("checkId", c.getId());
                m.put("numero", c.getNumberCheck());
                m.put("beneficiario", c.getBeneficiary());
                m.put("valor", c.getValue());
                m.put("fechaEmision", c.getIssueDate().toString());
                m.put("caducidad", caducidad.toString());
                m.put("diasParaCaducar", diasParaCaducar);
                out.add(m);
            }
        }
        return out;
    }

    /** HU-074 E5: reporte de partidas pendientes con buckets + totales por bucket. */
    @Transactional
    public Map<String, Object> report(Long bankAccountId, Integer diasMin, Integer diasMax, String tipo) {
        recalcForCurrentTenant();
        List<PartidaConciliatoria> base = (bankAccountId != null)
                ? partidaRepo.findByBankAccountIdAndEstadoAndDeletedAtIsNullOrderByIdDesc(bankAccountId, "PENDIENTE")
                : partidaRepo.findByEstadoAndDeletedAtIsNullOrderByIdDesc("PENDIENTE");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, int[]> bucketCount = new LinkedHashMap<>(); // bucket -> [count]
        Map<String, BigDecimal> bucketSum = new LinkedHashMap<>();
        for (String b : List.of("NORMAL", "ATENCION", "ADVERTENCIA", "CRITICA")) { bucketCount.put(b, new int[]{0}); bucketSum.put(b, BigDecimal.ZERO); }
        for (PartidaConciliatoria p : base) {
            int dias = p.getDiasAntiguedad() != null ? p.getDiasAntiguedad() : 0;
            if (diasMin != null && dias < diasMin) continue;
            if (diasMax != null && dias > diasMax) continue;
            if (tipo != null && !tipo.isBlank() && !tipo.equals(p.getTipo())) continue;
            String b = bucket(dias);
            bucketCount.get(b)[0]++;
            bucketSum.put(b, bucketSum.get(b).add(p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("bankAccountId", p.getBankAccountId());
            row.put("tipo", p.getTipo());
            row.put("monto", p.getMonto());
            row.put("fechaOrigen", p.getFechaOrigen() != null ? p.getFechaOrigen().toString() : null);
            row.put("diasAntiguedad", dias);
            row.put("bucket", b);
            rows.add(row);
        }
        List<Map<String, Object>> resumen = new ArrayList<>();
        for (String b : bucketCount.keySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bucket", b); m.put("count", bucketCount.get(b)[0]); m.put("suma", bucketSum.get(b));
            resumen.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", rows.size());
        r.put("resumenPorBucket", resumen);
        r.put("partidas", rows);
        return r;
    }

    /** HU-074 E6: resumen para el dashboard del módulo. */
    @Transactional
    public Map<String, Object> dashboard(Long bankAccountId) {
        Map<String, Object> rep = report(bankAccountId, null, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> partidas = (List<Map<String, Object>>) rep.get("partidas");
        BigDecimal suma = partidas.stream().map(p -> (BigDecimal) p.getOrDefault("monto", BigDecimal.ZERO))
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> top10 = partidas.stream()
                .sorted((a, b) -> Integer.compare((int) b.get("diasAntiguedad"), (int) a.get("diasAntiguedad")))
                .limit(10).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalPartidas", partidas.size());
        r.put("sumaMontos", suma);
        r.put("distribucionPorBucket", rep.get("resumenPorBucket"));
        r.put("top10MasAntiguas", top10);
        r.put("chequesProximosCaducar", chequesProximosCaducar());
        return r;
    }

    /** HU-074 E8: resolver una partida manualmente (ajuste o próximo período). */
    @Transactional
    public Map<String, Object> resolver(Long partidaId, String tipoResolucion, Long comprobanteId, String motivo) {
        PartidaConciliatoria p = partidaRepo.findByIdAndDeletedAtIsNull(partidaId)
                .orElseThrow(() -> new IllegalArgumentException("Partida no encontrada."));
        if (!"PENDIENTE".equals(p.getEstado()))
            throw new IllegalStateException("La partida no está PENDIENTE (actual: " + p.getEstado() + ").");
        if ("AJUSTE".equalsIgnoreCase(tipoResolucion)) {
            if (comprobanteId == null) throw new IllegalArgumentException("Debe vincular el comprobante de ajuste generado.");
            p.setEstado("RESUELTA_AJUSTE");
            p.setComprobanteAjusteId(comprobanteId);
        } else if ("PROXIMO_PERIODO".equalsIgnoreCase(tipoResolucion)) {
            if (motivo == null || motivo.trim().length() < 20)
                throw new IllegalArgumentException("Debe justificar (mínimo 20 caracteres) por qué se concilia en el próximo período.");
            p.setEstado("RESUELTA_PROXIMO_PERIODO");
            p.setMotivoResolucion(motivo.trim());
        } else {
            throw new IllegalArgumentException("Tipo de resolución inválido. Use AJUSTE o PROXIMO_PERIODO.");
        }
        // HU-074 E8: las alertas asociadas se cierran (se limpian las marcas).
        p.setAlerta60dAt(null);
        p.setAlerta90dAt(null);
        partidaRepo.save(p);
        auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.MEDIUM,
                "PartidaConciliatoria", partidaId, "Partida resuelta manualmente: " + p.getEstado(), null,
                "{\"estado\":\"" + p.getEstado() + "\"}", null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", partidaId); r.put("estado", p.getEstado());
        return r;
    }

    /** HU-074 E5: exporta el reporte de partidas pendientes a CSV/XLSX. */
    public byte[] exportReport(Long bankAccountId, Integer diasMin, Integer diasMax, String tipo, String format) {
        Map<String, Object> rep = report(bankAccountId, diasMin, diasMax, tipo);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rep.getOrDefault("partidas", List.of());
        List<String> headers = List.of("Cuenta", "Tipo", "Monto", "Fecha origen", "Días", "Bucket");
        List<Function<Map<String, Object>, Object>> cols = List.of(
                r -> r.get("bankAccountId"), r -> r.get("tipo"), r -> r.get("monto"),
                r -> r.get("fechaOrigen"), r -> r.get("diasAntiguedad"), r -> r.get("bucket"));
        if ("xlsx".equalsIgnoreCase(format)) return SimpleTableExporter.toXlsx("PartidasPendientes", headers, cols, rows);
        return SimpleTableExporter.toCsv(headers, cols, rows);
    }

    /**
     * HU-074 E1/E3/E4/E7: job diario — recalcula antigüedad, dispara alertas y
     * revisa cheques por caducar para CADA empresa activa. Configurable por cron.
     */
    @Scheduled(cron = "${sigcon.bnk.aging-cron:0 15 2 * * *}")
    public void dailyAgingJob() {
        for (Company c : companyRepo.findAll()) {
            if (c.getDeletedAt() != null || !"ACTIVE".equals(c.getStatus() != null ? c.getStatus().name() : "")) continue;
            try {
                TenantContext.runAs(c.getId(), false, () -> {
                    recalcForCurrentTenant();
                    Map<String, Object> a = runAlertsForCurrentTenant();
                    List<Map<String, Object>> ch = chequesProximosCaducar();
                    if (!ch.isEmpty()) {
                        auditLogService.register(AuditAction.UPDATE, AuditModule.BNK, AuditSeverity.HIGH,
                                "Check", null, "CHEQUE_PROXIMO_CADUCAR: " + ch.size() + " cheque(s) caducan en <30 días (Art. 721 C.Co.).",
                                null, "{\"cheques\":" + ch.size() + "}", null);
                    }
                    log.info("HU-074 aging empresa {}: alertas {}, cheques caducar {}", c.getId(), a, ch.size());
                });
            } catch (Exception e) {
                log.warn("HU-074 aging job falló para empresa {}: {}", c.getId(), e.getMessage());
            }
        }
    }
}
