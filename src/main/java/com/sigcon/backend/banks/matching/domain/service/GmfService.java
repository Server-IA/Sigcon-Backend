package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * BNK-HU-061: Gravamen a los Movimientos Financieros (GMF, 4x1000, art. 870 ET).
 *
 *  - E3: validación cruzada del GMF del período (esperado 0.004 × Σ retiros
 *    gravados vs cargado por el banco).
 *  - E4: reporte de GMF por cuenta y período, exportable a Excel/CSV.
 */
@Service
@RequiredArgsConstructor
public class GmfService {

    /** Tarifa GMF 4x1000 = 0.004 (art. 872 ET). */
    private static final BigDecimal TARIFA_GMF = new BigDecimal("0.004");
    /** Tolerancia de inconsistencia 1% (HU-061 E3). */
    private static final BigDecimal TOL = new BigDecimal("0.01");
    /** Tipos excluidos del cálculo de retiros gravados (HU-061 E3). */
    private static final Set<String> EXCLUIDOS = Set.of("GMF", "COMISION", "TRANSFERENCIA");

    private final FinancialMovementRepository movementRepository;

    /**
     * BNK-HU-061 E3: valida la consistencia del GMF del período. No bloquea cierre.
     */
    public Map<String, Object> validate(Long bankAccountId) {
        return computeReport(bankAccountId, null, null, false);
    }

    /**
     * BNK-HU-061 E4: reporte de GMF causado por cuenta y período (incluye el
     * listado de movimientos GMF detectados).
     */
    public Map<String, Object> report(Long bankAccountId, LocalDate from, LocalDate to) {
        return computeReport(bankAccountId, from, to, true);
    }

    /** BNK-HU-061 E4: exporta el listado de movimientos GMF a CSV/XLSX. */
    public byte[] exportReport(Long bankAccountId, LocalDate from, LocalDate to, String format) {
        Map<String, Object> rep = computeReport(bankAccountId, from, to, true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> movs = (List<Map<String, Object>>) rep.getOrDefault("movimientosGmf", List.of());
        List<String> headers = List.of("Fecha", "Descripción", "Monto GMF");
        List<Function<Map<String, Object>, Object>> cols = List.of(
                r -> r.get("fecha"), r -> r.get("descripcion"), r -> r.get("monto"));
        if ("xlsx".equalsIgnoreCase(format)) {
            return SimpleTableExporter.toXlsx("GMF", headers, cols, movs);
        }
        return SimpleTableExporter.toCsv(headers, cols, movs);
    }

    // ===================== core =====================

    private Map<String, Object> computeReport(Long bankAccountId, LocalDate from, LocalDate to, boolean includeList) {
        List<FinancialMovement> all = movementRepository.findAllByBankAccountIdOrdered(bankAccountId);

        BigDecimal retirosGravados = BigDecimal.ZERO;
        BigDecimal gmfCargado = BigDecimal.ZERO;
        List<Map<String, Object>> gmfMovs = new ArrayList<>();

        for (FinancialMovement m : all) {
            if (from != null && m.getMovementDate() != null && m.getMovementDate().isBefore(from)) continue;
            if (to != null && m.getMovementDate() != null && m.getMovementDate().isAfter(to)) continue;
            BigDecimal amt = m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO;
            String tipo = m.getTipoMovimiento();

            if ("GMF".equals(tipo)) {
                gmfCargado = gmfCargado.add(amt.abs());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", m.getId());
                row.put("fecha", m.getMovementDate() != null ? m.getMovementDate().toString() : "");
                row.put("descripcion", m.getDescripcionNormalizada() != null ? m.getDescripcionNormalizada() : m.getDescription());
                row.put("monto", amt.abs());
                gmfMovs.add(row);
            } else if (amt.signum() < 0 && (tipo == null || !EXCLUIDOS.contains(tipo))) {
                // Retiro gravado: egreso que NO es GMF/comisión/transferencia interna.
                retirosGravados = retirosGravados.add(amt.abs());
            }
        }

        BigDecimal gmfEsperado = retirosGravados.multiply(TARIFA_GMF).setScale(2, RoundingMode.HALF_UP);
        BigDecimal diferenciaAbs = gmfEsperado.subtract(gmfCargado).abs();
        BigDecimal diferenciaPct = BigDecimal.ZERO;
        boolean inconsistente = false;
        String mensaje;
        if (gmfEsperado.signum() == 0) {
            mensaje = "No hay retiros gravados en el período; no se calcula GMF esperado.";
        } else {
            diferenciaPct = diferenciaAbs.divide(gmfEsperado, 4, RoundingMode.HALF_UP);
            inconsistente = diferenciaPct.compareTo(TOL) > 0;
            mensaje = inconsistente
                    ? "GMF_INCONSISTENTE: el GMF cargado ($" + gmfCargado + ") difiere del esperado ($" + gmfEsperado
                      + ") en " + diferenciaPct.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                      + "% (> 1%). Revise el extracto. No bloquea el cierre."
                    : "GMF consistente: cargado $" + gmfCargado + " vs esperado $" + gmfEsperado + " (dentro del 1%).";
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("bankAccountId", bankAccountId);
        r.put("retirosGravados", retirosGravados);
        r.put("gmfEsperado", gmfEsperado);
        r.put("gmfCargado", gmfCargado);
        r.put("diferenciaAbsoluta", diferenciaAbs);
        r.put("diferenciaPorcentual", diferenciaPct.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
        r.put("inconsistente", inconsistente);
        r.put("mensaje", mensaje);
        r.put("totalMovimientosGmf", gmfMovs.size());
        if (includeList) r.put("movimientosGmf", gmfMovs);
        return r;
    }
}
