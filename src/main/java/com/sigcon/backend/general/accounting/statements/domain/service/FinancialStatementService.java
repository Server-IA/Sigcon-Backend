package com.sigcon.backend.general.accounting.statements.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntryLine;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.statements.application.BalanceGeneralDTO;
import com.sigcon.backend.general.accounting.statements.application.EstadoPatrimonioDTO;
import com.sigcon.backend.general.accounting.statements.application.EstadoResultadosDTO;
import com.sigcon.backend.general.accounting.statements.application.FlujoEfectivoDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de Estados Financieros.
 * Genera Balance General, Estado de Resultados, Flujo de Efectivo
 * y Estados Comparativos conforme a las NIIF y normativa colombiana.
 *
 * Los estados financieros se construyen a partir de los asientos contabilizados (POSTED),
 * clasificados segun la clase PUC de cada cuenta:
 * - Clase 1: Activos (naturaleza debito)
 * - Clase 2: Pasivos (naturaleza credito)
 * - Clase 3: Patrimonio (naturaleza credito)
 * - Clase 4: Ingresos (naturaleza credito)
 * - Clase 5: Gastos (naturaleza debito)
 * - Clase 6: Costos de venta (naturaleza debito)
 * - Clase 7: Costos de produccion (naturaleza debito)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialStatementService {

    private final JournalEntryRepository journalEntryRepository;

    /**
     * Audit publisher opcional para HU-CG-09 E6 / HU-CG-10 E6 / HU-CG-11 E5 /
     * HU-CG-18 / HU-CG-13 E5 (registrar generacion de estados financieros).
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.audit.domain.service.AuditPublisher auditPublisher;

    /**
     * Registra evento VIEW en auditoria al generar un estado. Defensive: si
     * el publisher falla, NO rompe la generacion del reporte.
     */
    private void publishViewAudit(String reportType, Integer year, Integer month, int rows) {
        if (auditPublisher == null) return;
        try {
            auditPublisher.publish(
                    com.sigcon.backend.audit.domain.model.enums.AuditAction.VIEW,
                    com.sigcon.backend.audit.domain.model.enums.AuditModule.CG,
                    com.sigcon.backend.audit.domain.model.enums.AuditSeverity.LOW,
                    "FinancialStatement", null,
                    "Generacion " + reportType + " "
                            + (year != null ? year : "?") + "-"
                            + (month != null ? String.format("%02d", month) : "??")
                            + " filas=" + rows, null, null, null);
        } catch (RuntimeException ignored) { /* audit no debe romper */ }
    }

    // ───────────────────────────────────────────────────────────────
    // Balance General (Estado de Situacion Financiera)
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Balance General hasta el periodo indicado (acumulado).
     * Ecuacion contable: Activos = Pasivos + Patrimonio.
     * Se incluyen todas las clases de balance (1, 2, 3) con movimientos
     * de todos los periodos hasta el indicado.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return Balance General con totales y detalle por cuenta
     */
    public ResponseEntity<?> getBalanceGeneral(Integer year, Integer month) {
        log.info("Generando Balance General acumulado hasta {}-{}", year, String.format("%02d", month));

        // HU-CG-09 E2 (Alta): el Balance General es ACUMULATIVO; debe arrastrar los
        // saldos de todos los periodos anteriores. Antes un guard por anio
        // (hasPostedEntriesInYear) devolvia 0 cuando el anio consultado no tenia
        // comprobantes propios (ej. 2027), ignorando los saldos historicos de 2026.
        // Ahora se consultan los asientos acumulados HASTA el periodo (todos los anios
        // previos incluidos) y solo se devuelve 0 si no existe historia contable alguna.
        List<JournalEntry> entries = soloVivos(journalEntryRepository.findPostedUpToPeriod(
                year, month, JournalEntryStatus.POSTED));

        if (entries.isEmpty()) {
            BalanceGeneralDTO vacio = BalanceGeneralDTO.builder()
                    .totalActivos(BigDecimal.ZERO)
                    .totalPasivos(BigDecimal.ZERO)
                    .totalPatrimonio(BigDecimal.ZERO)
                    .resultadoEjercicio(BigDecimal.ZERO)
                    .isBalanced(true)
                    .details(new ArrayList<>())
                    .build();
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("No existen comprobantes contabilizados hasta " + year + "-"
                            + String.format("%02d", month) + "."),
                    Optional.of(vacio)));
        }

        // Agrupar por clase PUC
        Map<AccountClass, Map<Long, AccountAccumulator>> classMap = new LinkedHashMap<>();
        classifyEntryLines(entries, classMap);

        // Calcular totales por clase de balance (1=Activos, 2=Pasivos, 3=Patrimonio)
        BigDecimal totalActivos = calculateClassTotal(classMap.get(AccountClass.ASSET), true);
        BigDecimal totalPasivos = calculateClassTotal(classMap.get(AccountClass.LIABILITY), false);
        BigDecimal patrimonioClase3 = calculateClassTotal(classMap.get(AccountClass.EQUITY), false);

        // HU-CG-09 E1 (Media): incorporar el Resultado del Ejercicio al patrimonio.
        // Mientras no se ejecute el asiento de cierre, el resultado (ingresos - gastos -
        // costos) permanece en las cuentas de clase 4/5/6/7 y el balance NO cuadra. Se
        // calcula el resultado acumulado y se suma al patrimonio (NIC 1). Es AUTO-CORRECTIVO:
        // tras el cierre, las clases 4/5/6/7 quedan en 0 y este ajuste vale 0 (el resultado
        // ya esta dentro de la clase 3 via el asiento de cierre).
        BigDecimal ingresos = calculateClassTotal(classMap.get(AccountClass.REVENUE), false);
        BigDecimal gastos = calculateClassTotal(classMap.get(AccountClass.EXPENSE), true);
        BigDecimal costos = calculateClassTotal(classMap.get(AccountClass.COST_OF_SALES), true)
                .add(calculateClassTotal(classMap.get(AccountClass.PRODUCTION_COST), true));
        BigDecimal resultadoEjercicio = ingresos.subtract(gastos).subtract(costos);

        BigDecimal totalPatrimonio = patrimonioClase3.add(resultadoEjercicio);

        // Verificar ecuacion contable con el resultado ya incorporado
        boolean isBalanced = totalActivos.compareTo(totalPasivos.add(totalPatrimonio)) == 0;

        // Construir detalle
        List<BalanceGeneralDTO.ClassDetailDTO> details = new ArrayList<>();
        addClassDetail(details, "ACTIVOS", classMap.get(AccountClass.ASSET), true);
        addClassDetail(details, "PASIVOS", classMap.get(AccountClass.LIABILITY), false);
        addClassDetail(details, "PATRIMONIO", classMap.get(AccountClass.EQUITY), false);

        // HU-CG-09 E1: agregar la linea sintetica del Resultado del Ejercicio dentro del
        // PATRIMONIO (solo si aporta algo). Hace visible y trazable el ajuste.
        if (resultadoEjercicio.compareTo(BigDecimal.ZERO) != 0) {
            details.stream()
                    .filter(c -> "PATRIMONIO".equals(c.getClassName()))
                    .findFirst()
                    .ifPresent(patClass -> {
                        if (patClass.getAccounts() == null) {
                            patClass.setAccounts(new ArrayList<>());
                        }
                        patClass.getAccounts().add(BalanceGeneralDTO.AccountDetailDTO.builder()
                                .accountId(null)
                                .pucCode("3605")
                                .accountName("Resultado del Ejercicio (sin asiento de cierre)")
                                .balance(resultadoEjercicio)
                                .build());
                        patClass.setTotal(patClass.getTotal() == null
                                ? resultadoEjercicio
                                : patClass.getTotal().add(resultadoEjercicio));
                    });
        }

        BalanceGeneralDTO result = BalanceGeneralDTO.builder()
                .totalActivos(totalActivos)
                .totalPasivos(totalPasivos)
                .totalPatrimonio(totalPatrimonio)
                .resultadoEjercicio(resultadoEjercicio)
                .isBalanced(isBalanced)
                .details(details)
                .build();

        publishViewAudit("BalanceGeneral", year, month, details.size());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Balance General generado correctamente hasta " + year + "-" + String.format("%02d", month)),
                Optional.of(result)));
    }

    // ───────────────────────────────────────────────────────────────
    // Estado de Resultados
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Estado de Resultados para el periodo indicado.
     * Solo incluye movimientos del periodo actual (no acumulado).
     * Clases PUC: 4 (Ingresos), 5 (Gastos), 6 (Costos de venta), 7 (Costos de produccion).
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return Estado de Resultados con utilidad bruta y neta
     */
    public ResponseEntity<?> getEstadoResultados(Integer year, Integer month) {
        log.info("Generando Estado de Resultados para {}-{}", year, String.format("%02d", month));

        // HU-CG-10 E3: excluir transacciones reversadas (original REVERSED + su REV-XXXX)
        List<JournalEntry> entries = soloVivos(journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED));

        Map<AccountClass, Map<Long, AccountAccumulator>> classMap = new LinkedHashMap<>();
        classifyEntryLines(entries, classMap);

        // Ingresos: naturaleza credito, saldo positivo = credito - debito
        BigDecimal totalIngresos = calculateClassTotal(classMap.get(AccountClass.REVENUE), false);
        // Gastos: naturaleza debito, saldo positivo = debito - credito
        BigDecimal totalGastos = calculateClassTotal(classMap.get(AccountClass.EXPENSE), true);
        // Costos: naturaleza debito
        BigDecimal costoVentas = calculateClassTotal(classMap.get(AccountClass.COST_OF_SALES), true);
        BigDecimal costoProduccion = calculateClassTotal(classMap.get(AccountClass.PRODUCTION_COST), true);
        BigDecimal totalCostos = costoVentas.add(costoProduccion);

        BigDecimal utilidadBruta = totalIngresos.subtract(totalCostos);
        BigDecimal utilidadNeta = totalIngresos.subtract(totalGastos).subtract(totalCostos);

        // HU-CG-10: clasificacion financiera granular NIC 1 por subgrupo PUC.
        // Cada cuenta de ingreso/gasto se asigna a una categoria por su codigo PUC
        // (Decreto 2650/1993) — la granularidad ya esta codificada en el plan de cuentas,
        // no requiere parametrizacion adicional.
        Map<Long, AccountAccumulator> revAccts = classMap.get(AccountClass.REVENUE);
        Map<Long, AccountAccumulator> expAccts = classMap.get(AccountClass.EXPENSE);

        BigDecimal ingresosOperacionales = sumWhere(revAccts, false, c -> c.startsWith("41"));
        BigDecimal ingresosFinancieros   = sumWhere(revAccts, false, c -> c.startsWith("4210"));
        // Otros ingresos = clase 4 que NO sea operacional (41) ni financiero (4210)
        BigDecimal otrosIngresos = sumWhere(revAccts, false,
                c -> !c.startsWith("41") && !c.startsWith("4210"));

        BigDecimal gastosAdministracion = sumWhere(expAccts, true, c -> c.startsWith("51"));
        BigDecimal gastosVentas         = sumWhere(expAccts, true, c -> c.startsWith("52"));
        BigDecimal gastosFinancieros    = sumWhere(expAccts, true, c -> c.startsWith("5305"));
        BigDecimal impuestoRenta        = sumWhere(expAccts, true, c -> c.startsWith("54"));
        // Otros gastos = clase 5 que NO sea admin (51), ventas (52), financiero (5305) ni impuesto (54)
        BigDecimal otrosGastos = sumWhere(expAccts, true,
                c -> !c.startsWith("51") && !c.startsWith("52")
                        && !c.startsWith("5305") && !c.startsWith("54"));

        // Subtotales NIC 1
        BigDecimal utilidadBrutaOperacional = ingresosOperacionales.subtract(totalCostos);
        BigDecimal utilidadOperacional = utilidadBrutaOperacional
                .subtract(gastosAdministracion).subtract(gastosVentas);
        BigDecimal utilidadAntesImpuestos = utilidadOperacional
                .add(ingresosFinancieros).add(otrosIngresos)
                .subtract(gastosFinancieros).subtract(otrosGastos);

        List<BalanceGeneralDTO.ClassDetailDTO> details = new ArrayList<>();
        addClassDetail(details, "INGRESOS", classMap.get(AccountClass.REVENUE), false);
        addClassDetail(details, "GASTOS", classMap.get(AccountClass.EXPENSE), true);
        addClassDetail(details, "COSTOS DE VENTA", classMap.get(AccountClass.COST_OF_SALES), true);
        addClassDetail(details, "COSTOS DE PRODUCCION", classMap.get(AccountClass.PRODUCTION_COST), true);

        EstadoResultadosDTO result = EstadoResultadosDTO.builder()
                .totalIngresos(totalIngresos)
                .totalGastos(totalGastos)
                .totalCostos(totalCostos)
                .utilidadBruta(utilidadBruta)
                .utilidadNeta(utilidadNeta)
                .ingresosOperacionales(ingresosOperacionales)
                .ingresosFinancieros(ingresosFinancieros)
                .otrosIngresos(otrosIngresos)
                .gastosAdministracion(gastosAdministracion)
                .gastosVentas(gastosVentas)
                .gastosFinancieros(gastosFinancieros)
                .otrosGastos(otrosGastos)
                .impuestoRenta(impuestoRenta)
                .utilidadBrutaOperacional(utilidadBrutaOperacional)
                .utilidadOperacional(utilidadOperacional)
                .utilidadAntesImpuestos(utilidadAntesImpuestos)
                .details(details)
                .build();

        publishViewAudit("EstadoResultados", year, month, details.size());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Estado de Resultados generado correctamente para " + year + "-" + String.format("%02d", month)),
                Optional.of(result)));
    }

    // ───────────────────────────────────────────────────────────────
    // Flujo de Efectivo
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Estado de Flujos de Efectivo para el periodo indicado.
     * Clasifica los asientos por tipo de actividad segun el modulo origen:
     * - OPERATIVA: AP (cuentas por pagar), AR (cuentas por cobrar), CG (contabilidad)
     * - INVERSION: ACT (activos fijos)
     * - FINANCIACION: BNK (bancos)
     * Si el sourceModule no esta definido, se clasifica como OPERATIVA.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return Flujo de Efectivo con detalle por actividad
     */
    public ResponseEntity<?> getFlujoEfectivo(Integer year, Integer month) {
        log.info("Generando Flujo de Efectivo para {}-{}", year, String.format("%02d", month));

        // HU-CG-10 E3 / HU-CG-11: excluir transacciones reversadas del flujo
        List<JournalEntry> entries = soloVivos(journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED));

        // HU-CG-11: el Flujo de Efectivo (NIC 7) rastrea SOLO los movimientos que afectan
        // las cuentas de efectivo y equivalentes (PUC clase 11: Caja 1105, Bancos 1110,
        // remesas/ahorros 1115/1120...). Cada asiento aporta su DELTA de efectivo
        // (debitos - creditos sobre las lineas de efectivo); los asientos que no tocan
        // efectivo (ej. causaciones de nomina D 5105 / C 2505) NO son flujos de caja y se
        // omiten. Cada flujo se clasifica por la contrapartida (cuenta no-efectivo) segun NIC 7.
        Map<String, List<FlujoEfectivoDTO.EntryDetailDTO>> entriesByActivity = new LinkedHashMap<>();
        Map<String, BigDecimal[]> sumsByActivity = new LinkedHashMap<>();
        for (String a : List.of("OPERATIVA", "INVERSION", "FINANCIACION")) {
            entriesByActivity.put(a, new ArrayList<>());
            sumsByActivity.put(a, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        }

        for (JournalEntry entry : entries) {
            if (entry.getLines() == null) continue;
            BigDecimal cashDebit = BigDecimal.ZERO;
            BigDecimal cashCredit = BigDecimal.ZERO;
            for (JournalEntryLine line : entry.getLines()) {
                if (!isCashLine(line)) continue;
                cashDebit = cashDebit.add(line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
                cashCredit = cashCredit.add(line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
            }
            BigDecimal cashDelta = cashDebit.subtract(cashCredit);
            // Sin movimiento neto de efectivo (causacion o traspaso interno) => no es flujo
            if (cashDelta.compareTo(BigDecimal.ZERO) == 0) continue;

            String activity = classifyCashFlowActivity(entry);
            BigDecimal[] s = sumsByActivity.get(activity);
            s[0] = s[0].add(cashDebit);
            s[1] = s[1].add(cashCredit);
            entriesByActivity.get(activity).add(FlujoEfectivoDTO.EntryDetailDTO.builder()
                    .entryId(entry.getId())
                    .entryNumber(entry.getEntryNumber())
                    .description(entry.getDescription())
                    .sourceModule(entry.getSourceModule() != null ? entry.getSourceModule().name() : null)
                    .totalDebit(cashDebit)
                    .totalCredit(cashCredit)
                    .build());
        }

        List<FlujoEfectivoDTO.ActivityDetailDTO> details = new ArrayList<>();
        BigDecimal flujoOperativo = BigDecimal.ZERO;
        BigDecimal flujoInversion = BigDecimal.ZERO;
        BigDecimal flujoFinanciacion = BigDecimal.ZERO;
        for (String activityType : List.of("OPERATIVA", "INVERSION", "FINANCIACION")) {
            BigDecimal[] s = sumsByActivity.get(activityType);
            BigDecimal netFlow = s[0].subtract(s[1]);
            details.add(FlujoEfectivoDTO.ActivityDetailDTO.builder()
                    .activityType(activityType)
                    .totalDebit(s[0])
                    .totalCredit(s[1])
                    .netFlow(netFlow)
                    .entries(entriesByActivity.get(activityType))
                    .build());
            switch (activityType) {
                case "OPERATIVA" -> flujoOperativo = netFlow;
                case "INVERSION" -> flujoInversion = netFlow;
                case "FINANCIACION" -> flujoFinanciacion = netFlow;
            }
        }

        BigDecimal flujoNeto = flujoOperativo.add(flujoInversion).add(flujoFinanciacion);

        // HU-CG-11 E2: conciliacion de efectivo. Saldo inicial = efectivo acumulado antes
        // del periodo; saldo final independiente = efectivo acumulado hasta el periodo.
        BigDecimal saldoInicial = accumulatedCash(soloVivos(
                journalEntryRepository.findPostedBeforePeriod(year, month, JournalEntryStatus.POSTED)));
        BigDecimal saldoFinalIndependiente = accumulatedCash(soloVivos(
                journalEntryRepository.findPostedUpToPeriod(year, month, JournalEntryStatus.POSTED)));
        BigDecimal saldoFinalCalculado = saldoInicial.add(flujoNeto);
        boolean conciliado = saldoFinalCalculado.compareTo(saldoFinalIndependiente) == 0;

        FlujoEfectivoDTO result = FlujoEfectivoDTO.builder()
                .flujoOperativo(flujoOperativo)
                .flujoInversion(flujoInversion)
                .flujoFinanciacion(flujoFinanciacion)
                .flujoNeto(flujoNeto)
                .saldoInicialEfectivo(saldoInicial)
                .saldoFinalEfectivo(saldoFinalIndependiente)
                .conciliado(conciliado)
                .details(details)
                .build();

        publishViewAudit("FlujoEfectivo", year, month, details.size());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Flujo de Efectivo generado correctamente para " + year + "-" + String.format("%02d", month)),
                Optional.of(result)));
    }

    // ───────────────────────────────────────────────────────────────
    // Estado Financiero Comparativo
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera un Balance General comparativo entre dos periodos.
     * Calcula la variacion absoluta y porcentual entre ambos periodos
     * para cada clase contable (Activos, Pasivos, Patrimonio).
     *
     * @param year1  anio del primer periodo
     * @param month1 mes del primer periodo
     * @param year2  anio del segundo periodo
     * @param month2 mes del segundo periodo
     * @return datos comparativos con variaciones absolutas y porcentuales
     */
    /** Umbral de variacion significativa (HU-CG-13 E3): +/-10%. */
    private static final BigDecimal UMBRAL_VARIACION = BigDecimal.valueOf(10);

    /** Overload de 2 periodos (compatibilidad). Delega en la version de 3 periodos. */
    public ResponseEntity<?> getComparativo(Integer year1, Integer month1, Integer year2, Integer month2) {
        return getComparativo(year1, month1, year2, month2, null, null);
    }

    /**
     * HU-CG-13: Balance General comparativo entre DOS o TRES periodos.
     * Calcula la variacion absoluta y porcentual del periodo A->B y, si se
     * indica un tercer periodo, tambien B->C. Marca con {@code umbralExcedido}
     * las filas cuya variacion porcentual supera +/-10% (E3, para resaltado).
     *
     * @param year3  anio del tercer periodo (opcional; null = solo 2 periodos)
     * @param month3 mes del tercer periodo (opcional)
     */
    public ResponseEntity<?> getComparativo(Integer year1, Integer month1,
                                            Integer year2, Integer month2,
                                            Integer year3, Integer month3) {
        boolean tres = year3 != null && month3 != null;
        log.info("Generando Balance Comparativo {}-{} vs {}-{}{}",
                year1, String.format("%02d", month1), year2, String.format("%02d", month2),
                tres ? " vs " + year3 + "-" + String.format("%02d", month3) : "");

        Map<String, BigDecimal> balance1 = calculateBalanceTotals(year1, month1);
        Map<String, BigDecimal> balance2 = calculateBalanceTotals(year2, month2);
        Map<String, BigDecimal> balance3 = tres ? calculateBalanceTotals(year3, month3) : null;

        List<Map<String, Object>> comparison = new ArrayList<>();
        for (String className : List.of("ACTIVOS", "PASIVOS", "PATRIMONIO")) {
            BigDecimal val1 = balance1.getOrDefault(className, BigDecimal.ZERO);
            BigDecimal val2 = balance2.getOrDefault(className, BigDecimal.ZERO);
            BigDecimal variacionAbsoluta = val2.subtract(val1);
            BigDecimal variacionPorcentual = pct(variacionAbsoluta, val1);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("className", className);
            row.put("period1Value", val1);
            row.put("period1Label", year1 + "-" + String.format("%02d", month1));
            row.put("period2Value", val2);
            row.put("period2Label", year2 + "-" + String.format("%02d", month2));
            row.put("variacionAbsoluta", variacionAbsoluta);
            row.put("variacionPorcentual", variacionPorcentual);
            boolean excede = variacionPorcentual.abs().compareTo(UMBRAL_VARIACION) > 0;

            if (tres) {
                BigDecimal val3 = balance3.getOrDefault(className, BigDecimal.ZERO);
                BigDecimal variacionAbsoluta2 = val3.subtract(val2);
                BigDecimal variacionPorcentual2 = pct(variacionAbsoluta2, val2);
                row.put("period3Value", val3);
                row.put("period3Label", year3 + "-" + String.format("%02d", month3));
                row.put("variacionAbsoluta2", variacionAbsoluta2);
                row.put("variacionPorcentual2", variacionPorcentual2);
                excede = excede || variacionPorcentual2.abs().compareTo(UMBRAL_VARIACION) > 0;
            }
            // HU-CG-13 E3: bandera de variacion significativa (>+/-10%) para resaltado.
            row.put("umbralExcedido", excede);
            comparison.add(row);
        }

        publishViewAudit("Comparativo " + year1 + "-" + month1 + " vs " + year2 + "-" + month2
                + (tres ? " vs " + year3 + "-" + month3 : ""), year1, month1, comparison.size());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Balance Comparativo generado correctamente"),
                Optional.of(comparison)));
    }

    /** Variacion porcentual segura (0 si la base es 0). */
    private BigDecimal pct(BigDecimal variacion, BigDecimal base) {
        return base.compareTo(BigDecimal.ZERO) != 0
                ? variacion.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    // ───────────────────────────────────────────────────────────────
    // Estado de Cambios en el Patrimonio (HU-CG-18)
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Estado de Cambios en el Patrimonio para el periodo indicado (HU-CG-18).
     *
     * Cuarto estado financiero obligatorio segun NIC 1. Refleja los movimientos
     * de las cuentas de clase 3 (Patrimonio) del PUC colombiano durante el periodo.
     *
     * Logica:
     * <ul>
     *   <li>Saldo inicial: acumulado (credito - debito) de cuentas clase 3 con entries
     *       anteriores al periodo.</li>
     *   <li>Movimientos del periodo: suma de debitos y creditos del mes indicado.</li>
     *   <li>Saldo final: saldo inicial + creditos - debitos.</li>
     *   <li>Clasificacion por subgrupo PUC: 31=Aportes, 33=Reservas, 36=Utilidad,
     *       37=Resultados Acumulados (incluye dividendos si son debitos), otros=32/34/38.</li>
     * </ul>
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return Estado de Cambios en el Patrimonio con totales y detalle por cuenta
     */
    public ResponseEntity<?> getEstadoCambiosPatrimonio(Integer year, Integer month) {
        log.info("Generando Estado de Cambios en el Patrimonio para {}-{}",
                year, String.format("%02d", month));

        // 1. Saldo inicial: acumulados de cuentas clase 3 antes del periodo
        // HU-CG-10 E3: excluir transacciones reversadas
        List<JournalEntry> entriesBefore = soloVivos(journalEntryRepository.findPostedBeforePeriod(
                year, month, JournalEntryStatus.POSTED));
        Map<AccountClass, Map<Long, AccountAccumulator>> beforeMap = new LinkedHashMap<>();
        classifyEntryLines(entriesBefore, beforeMap);
        Map<Long, AccountAccumulator> equityBefore = beforeMap.getOrDefault(
                AccountClass.EQUITY, new LinkedHashMap<>());

        // 2. Movimientos del periodo: cuentas clase 3 durante el mes
        List<JournalEntry> entriesPeriod = soloVivos(journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED));
        Map<AccountClass, Map<Long, AccountAccumulator>> periodMap = new LinkedHashMap<>();
        classifyEntryLines(entriesPeriod, periodMap);
        Map<Long, AccountAccumulator> equityPeriod = periodMap.getOrDefault(
                AccountClass.EQUITY, new LinkedHashMap<>());

        // 3. Consolidar cuentas involucradas (union de antes y periodo)
        Map<Long, EquityAccountState> consolidated = new LinkedHashMap<>();
        for (AccountAccumulator acc : equityBefore.values()) {
            EquityAccountState st = new EquityAccountState(acc.accountId, acc.pucCode, acc.accountName);
            // saldo inicial para naturaleza credito = credito - debito
            st.saldoInicial = acc.totalCredit.subtract(acc.totalDebit);
            consolidated.put(acc.accountId, st);
        }
        for (AccountAccumulator acc : equityPeriod.values()) {
            EquityAccountState st = consolidated.computeIfAbsent(acc.accountId,
                    k -> new EquityAccountState(acc.accountId, acc.pucCode, acc.accountName));
            st.movimientosDebito = acc.totalDebit;
            st.movimientosCredito = acc.totalCredit;
        }

        // 4. Clasificar por subgrupo (prefijo codigo PUC) y calcular totales
        BigDecimal saldoInicial = BigDecimal.ZERO;
        BigDecimal aportes = BigDecimal.ZERO;
        BigDecimal reservas = BigDecimal.ZERO;
        BigDecimal utilidadNeta = BigDecimal.ZERO;
        BigDecimal resultadosAcumulados = BigDecimal.ZERO;
        BigDecimal dividendosDecretados = BigDecimal.ZERO;
        BigDecimal otrosMovimientos = BigDecimal.ZERO;
        BigDecimal saldoFinal = BigDecimal.ZERO;

        List<EstadoPatrimonioDTO.AccountMovementDTO> details = new ArrayList<>();

        for (EquityAccountState st : consolidated.values()) {
            // saldo final para naturaleza credito = saldo inicial + credito - debito
            BigDecimal netoPeriodo = st.movimientosCredito.subtract(st.movimientosDebito);
            st.saldoFinal = st.saldoInicial.add(netoPeriodo);

            saldoInicial = saldoInicial.add(st.saldoInicial);
            saldoFinal = saldoFinal.add(st.saldoFinal);

            String code = st.pucCode != null ? st.pucCode : "";
            String group = code.length() >= 2 ? code.substring(0, 2) : code;

            switch (group) {
                case "31" -> aportes = aportes.add(netoPeriodo);
                case "33" -> reservas = reservas.add(netoPeriodo);
                case "36" -> utilidadNeta = utilidadNeta.add(netoPeriodo);
                case "37" -> {
                    resultadosAcumulados = resultadosAcumulados.add(netoPeriodo);
                    // Dividendos decretados se reflejan como debitos a cuentas 37/36
                    dividendosDecretados = dividendosDecretados.add(st.movimientosDebito);
                }
                default -> otrosMovimientos = otrosMovimientos.add(netoPeriodo);
            }

            details.add(EstadoPatrimonioDTO.AccountMovementDTO.builder()
                    .pucCode(st.pucCode)
                    .accountName(st.accountName)
                    .saldoInicial(st.saldoInicial)
                    .movimientosDebito(st.movimientosDebito)
                    .movimientosCredito(st.movimientosCredito)
                    .saldoFinal(st.saldoFinal)
                    .build());
        }

        // Ordenar detalles por codigo PUC para presentacion clara
        details.sort(Comparator.comparing(
                d -> d.getPucCode() == null ? "" : d.getPucCode()));

        EstadoPatrimonioDTO result = EstadoPatrimonioDTO.builder()
                .saldoInicial(saldoInicial)
                .aportes(aportes)
                .utilidadNeta(utilidadNeta)
                .reservas(reservas)
                .resultadosAcumulados(resultadosAcumulados)
                .dividendosDecretados(dividendosDecretados)
                .otrosMovimientos(otrosMovimientos)
                .saldoFinal(saldoFinal)
                .details(details)
                .build();

        publishViewAudit("CambiosPatrimonio", year, month, details.size());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Estado de Cambios en el Patrimonio generado correctamente para "
                        + year + "-" + String.format("%02d", month)),
                Optional.of(result)));
    }

    /** Estado interno de una cuenta de patrimonio durante el periodo. */
    private static class EquityAccountState {
        Long accountId;
        String pucCode;
        String accountName;
        BigDecimal saldoInicial = BigDecimal.ZERO;
        BigDecimal movimientosDebito = BigDecimal.ZERO;
        BigDecimal movimientosCredito = BigDecimal.ZERO;
        BigDecimal saldoFinal = BigDecimal.ZERO;

        EquityAccountState(Long accountId, String pucCode, String accountName) {
            this.accountId = accountId;
            this.pucCode = pucCode;
            this.accountName = accountName;
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Metodos auxiliares privados
    // ───────────────────────────────────────────────────────────────

    /**
     * Clasifica las lineas de asientos en un mapa agrupado por clase PUC y cuenta.
     */
    private void classifyEntryLines(List<JournalEntry> entries,
                                    Map<AccountClass, Map<Long, AccountAccumulator>> classMap) {
        for (JournalEntry entry : entries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (line.getAccountingAccount() == null
                        || line.getAccountingAccount().getPucAccount() == null) continue;

                AccountClass accountClass = line.getAccountingAccount().getPucAccount().getAccountClass();
                if (accountClass == null) continue;

                classMap.computeIfAbsent(accountClass, k -> new LinkedHashMap<>());
                Map<Long, AccountAccumulator> accounts = classMap.get(accountClass);

                Long accId = line.getAccountingAccount().getId();
                accounts.computeIfAbsent(accId, k -> new AccountAccumulator(
                        accId,
                        line.getAccountingAccount().getPucAccount().getCode(),
                        line.getAccountingAccount().getPucAccount().getName()));

                AccountAccumulator acc = accounts.get(accId);
                acc.totalDebit = acc.totalDebit.add(
                        line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
                acc.totalCredit = acc.totalCredit.add(
                        line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
            }
        }
    }

    /**
     * Calcula el total de una clase contable.
     * Para cuentas de naturaleza debito (Activos, Gastos, Costos): saldo = debito - credito
     * Para cuentas de naturaleza credito (Pasivos, Patrimonio, Ingresos): saldo = credito - debito
     *
     * @param accounts   mapa de cuentas acumuladas
     * @param debitNature true si la clase es de naturaleza debito
     * @return total de la clase
     */
    private BigDecimal calculateClassTotal(Map<Long, AccountAccumulator> accounts, boolean debitNature) {
        if (accounts == null || accounts.isEmpty()) return BigDecimal.ZERO;
        return accounts.values().stream()
                .map(acc -> debitNature
                        ? acc.totalDebit.subtract(acc.totalCredit)
                        : acc.totalCredit.subtract(acc.totalDebit))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * HU-CG-10: suma el saldo de las cuentas de un mapa cuyo codigo PUC cumple el
     * predicado. Usado para la clasificacion financiera granular NIC 1 del Estado de
     * Resultados (ingresos/gastos por subgrupo PUC). null-safe.
     *
     * @param accounts     cuentas acumuladas (de REVENUE o EXPENSE)
     * @param debitNature  true para gastos/costos (debito - credito), false para ingresos
     * @param codeMatches  predicado sobre el codigo PUC (ej. empieza por "51")
     */
    private BigDecimal sumWhere(Map<Long, AccountAccumulator> accounts, boolean debitNature,
                                java.util.function.Predicate<String> codeMatches) {
        if (accounts == null || accounts.isEmpty()) return BigDecimal.ZERO;
        return accounts.values().stream()
                .filter(acc -> acc.pucCode != null && codeMatches.test(acc.pucCode))
                .map(acc -> debitNature
                        ? acc.totalDebit.subtract(acc.totalCredit)
                        : acc.totalCredit.subtract(acc.totalDebit))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Agrega el detalle de una clase contable a la lista de detalles.
     */
    private void addClassDetail(List<BalanceGeneralDTO.ClassDetailDTO> details, String className,
                                Map<Long, AccountAccumulator> accounts, boolean debitNature) {
        List<BalanceGeneralDTO.AccountDetailDTO> accountDetails = new ArrayList<>();
        BigDecimal classTotal = BigDecimal.ZERO;

        if (accounts != null) {
            for (AccountAccumulator acc : accounts.values()) {
                BigDecimal balance = debitNature
                        ? acc.totalDebit.subtract(acc.totalCredit)
                        : acc.totalCredit.subtract(acc.totalDebit);
                classTotal = classTotal.add(balance);
                accountDetails.add(BalanceGeneralDTO.AccountDetailDTO.builder()
                        .accountId(acc.accountId)
                        .pucCode(acc.pucCode)
                        .accountName(acc.accountName)
                        .balance(balance)
                        .build());
            }
            // Ordenar por codigo PUC
            accountDetails.sort(Comparator.comparing(BalanceGeneralDTO.AccountDetailDTO::getPucCode));
        }

        details.add(BalanceGeneralDTO.ClassDetailDTO.builder()
                .className(className)
                .total(classTotal)
                .accounts(accountDetails)
                .build());
    }

    /**
     * Calcula los totales del balance general para un periodo (usado en comparativos).
     */
    private Map<String, BigDecimal> calculateBalanceTotals(Integer year, Integer month) {
        // HU-CG-10 E3: excluir transacciones reversadas tambien en el comparativo
        List<JournalEntry> entries = soloVivos(journalEntryRepository.findPostedUpToPeriod(
                year, month, JournalEntryStatus.POSTED));
        Map<AccountClass, Map<Long, AccountAccumulator>> classMap = new LinkedHashMap<>();
        classifyEntryLines(entries, classMap);

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("ACTIVOS", calculateClassTotal(classMap.get(AccountClass.ASSET), true));
        totals.put("PASIVOS", calculateClassTotal(classMap.get(AccountClass.LIABILITY), false));
        totals.put("PATRIMONIO", calculateClassTotal(classMap.get(AccountClass.EQUITY), false));
        return totals;
    }

    /**
     * Clasifica un asiento por tipo de actividad NIC 7 segun su modulo origen.
     * AP, AR, CG, NOM = OPERATIVA; ACT = INVERSION; BNK = FINANCIACION.
     */
    private String classifyActivity(JournalSourceModule sourceModule) {
        if (sourceModule == null) return "OPERATIVA";
        return switch (sourceModule) {
            case ACT -> "INVERSION";
            case BNK -> "FINANCIACION";
            default -> "OPERATIVA";
        };
    }

    /** Codigo PUC de la cuenta de una linea (null-safe). */
    private String pucCodeOf(JournalEntryLine line) {
        if (line.getAccountingAccount() == null
                || line.getAccountingAccount().getPucAccount() == null) return null;
        return line.getAccountingAccount().getPucAccount().getCode();
    }

    /**
     * HU-CG-11: true si la linea afecta una cuenta de efectivo o equivalentes
     * (PUC clase 11 = Disponible: 1105 Caja, 1110 Bancos, 1115 Remesas, 1120 Ahorros...).
     */
    private boolean isCashLine(JournalEntryLine line) {
        String code = pucCodeOf(line);
        return code != null && code.startsWith("11");
    }

    /**
     * HU-CG-11: clasifica el flujo de un asiento por la naturaleza de su contrapartida
     * (cuenta NO-efectivo de mayor monto), conforme NIC 7:
     * <ul>
     *   <li>INVERSION: contrapartida en activos no corrientes / inversiones
     *       (PUC 12 inversiones, 15 PPE, 16 intangibles, 17 diferidos, 18 otros activos, 19).</li>
     *   <li>FINANCIACION: contrapartida en obligaciones financieras (PUC 21) o patrimonio (clase 3:
     *       aportes de capital, dividendos).</li>
     *   <li>OPERATIVA: resto (ingresos 4, gastos 5, costos 6/7, CxC 13, inventarios 14,
     *       proveedores 22, CxP 23, impuestos 24, obligaciones laborales 25...).</li>
     * </ul>
     * Si el asiento no tiene contrapartida no-efectivo (ej. traspaso entre cuentas de
     * efectivo) se cae al heuristico por modulo origen.
     */
    private String classifyCashFlowActivity(JournalEntry entry) {
        String bestCode = null;
        BigDecimal bestAmt = BigDecimal.valueOf(-1);
        if (entry.getLines() != null) {
            for (JournalEntryLine line : entry.getLines()) {
                if (isCashLine(line)) continue;
                String code = pucCodeOf(line);
                if (code == null) continue;
                BigDecimal amt = (line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO)
                        .add(line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
                if (amt.compareTo(bestAmt) > 0) {
                    bestAmt = amt;
                    bestCode = code;
                }
            }
        }
        if (bestCode == null) return classifyActivity(entry.getSourceModule());
        String g2 = bestCode.length() >= 2 ? bestCode.substring(0, 2) : bestCode;
        if (bestCode.startsWith("3") || g2.equals("21")) return "FINANCIACION";
        if (List.of("12", "15", "16", "17", "18", "19").contains(g2)) return "INVERSION";
        return "OPERATIVA";
    }

    /**
     * HU-CG-11 E2: suma el efectivo neto (debitos - creditos sobre cuentas de efectivo)
     * acumulado en una lista de asientos. Usado para los saldos inicial/final de la
     * conciliacion de efectivo.
     */
    private BigDecimal accumulatedCash(List<JournalEntry> entries) {
        BigDecimal total = BigDecimal.ZERO;
        if (entries == null) return total;
        for (JournalEntry entry : entries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (!isCashLine(line)) continue;
                total = total.add(line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
                total = total.subtract(line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
            }
        }
        return total;
    }

    /**
     * HU-CG-10 E3: excluye de los estados financieros las transacciones reversadas.
     *
     * <p>Una reversion produce DOS asientos: el original pasa a estado {@code REVERSED}
     * y se genera un contra-asiento {@code REV-XXXX} (identificable por
     * {@code reversalOf != null}) en el periodo actual ({@code LocalDate.now()}). Las
     * queries de repositorio incluyen {@code REVERSED} a proposito para que los LIBROS
     * oficiales (Diario/Mayor) muestren ambos asientos y preserven la trazabilidad
     * (NIC 1 / Decreto 2649/93). Pero en los ESTADOS FINANCIEROS eso descuadra el caso
     * cruzado: si el original estaba en abril y el REV cae en mayo, abril seguia contando
     * el movimiento y mayo contaba el negativo.</p>
     *
     * <p>Solucion: para los estados financieros se descartan AMBAS patas de toda
     * transaccion reversada — el original {@code REVERSED} y su {@code REV-XXXX} — de modo
     * que una transaccion totalmente reversada no aporta a ningun estado en ningun periodo.
     * Las correcciones ({@code correctionOf != null}, estado {@code POSTED}) SI se conservan,
     * porque representan la version corregida que debe reflejarse.</p>
     */
    private List<JournalEntry> soloVivos(List<JournalEntry> entries) {
        if (entries == null || entries.isEmpty()) return entries;
        return entries.stream()
                .filter(e -> e.getStatus() != JournalEntryStatus.REVERSED)
                .filter(e -> e.getReversalOf() == null)
                .collect(Collectors.toList());
    }

    /** Acumulador interno para totales por cuenta contable. */
    private static class AccountAccumulator {
        Long accountId;
        String pucCode;
        String accountName;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        AccountAccumulator(Long accountId, String pucCode, String accountName) {
            this.accountId = accountId;
            this.pucCode = pucCode;
            this.accountName = accountName;
        }
    }
}
