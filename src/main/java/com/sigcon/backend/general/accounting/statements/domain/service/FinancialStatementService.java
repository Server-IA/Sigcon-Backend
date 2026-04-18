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

        // Obtener todos los asientos POSTED hasta el periodo
        List<JournalEntry> entries = journalEntryRepository.findPostedUpToPeriod(
                year, month, JournalEntryStatus.POSTED);

        // Agrupar por clase PUC
        Map<AccountClass, Map<Long, AccountAccumulator>> classMap = new LinkedHashMap<>();
        classifyEntryLines(entries, classMap);

        // Calcular totales por clase de balance (1=Activos, 2=Pasivos, 3=Patrimonio)
        BigDecimal totalActivos = calculateClassTotal(classMap.get(AccountClass.ASSET), true);
        BigDecimal totalPasivos = calculateClassTotal(classMap.get(AccountClass.LIABILITY), false);
        BigDecimal totalPatrimonio = calculateClassTotal(classMap.get(AccountClass.EQUITY), false);

        // Verificar ecuacion contable
        boolean isBalanced = totalActivos.compareTo(totalPasivos.add(totalPatrimonio)) == 0;

        // Construir detalle
        List<BalanceGeneralDTO.ClassDetailDTO> details = new ArrayList<>();
        addClassDetail(details, "ACTIVOS", classMap.get(AccountClass.ASSET), true);
        addClassDetail(details, "PASIVOS", classMap.get(AccountClass.LIABILITY), false);
        addClassDetail(details, "PATRIMONIO", classMap.get(AccountClass.EQUITY), false);

        BalanceGeneralDTO result = BalanceGeneralDTO.builder()
                .totalActivos(totalActivos)
                .totalPasivos(totalPasivos)
                .totalPatrimonio(totalPatrimonio)
                .isBalanced(isBalanced)
                .details(details)
                .build();

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

        List<JournalEntry> entries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);

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
                .details(details)
                .build();

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

        List<JournalEntry> entries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);

        // Clasificar asientos por tipo de actividad NIC 7
        Map<String, List<JournalEntry>> activityMap = new LinkedHashMap<>();
        activityMap.put("OPERATIVA", new ArrayList<>());
        activityMap.put("INVERSION", new ArrayList<>());
        activityMap.put("FINANCIACION", new ArrayList<>());

        for (JournalEntry entry : entries) {
            String activity = classifyActivity(entry.getSourceModule());
            activityMap.get(activity).add(entry);
        }

        // Construir detalle por actividad
        List<FlujoEfectivoDTO.ActivityDetailDTO> details = new ArrayList<>();
        BigDecimal flujoOperativo = BigDecimal.ZERO;
        BigDecimal flujoInversion = BigDecimal.ZERO;
        BigDecimal flujoFinanciacion = BigDecimal.ZERO;

        for (Map.Entry<String, List<JournalEntry>> mapEntry : activityMap.entrySet()) {
            String activityType = mapEntry.getKey();
            List<JournalEntry> activityEntries = mapEntry.getValue();

            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            List<FlujoEfectivoDTO.EntryDetailDTO> entryDetails = new ArrayList<>();

            for (JournalEntry entry : activityEntries) {
                totalDebit = totalDebit.add(entry.getTotalDebit());
                totalCredit = totalCredit.add(entry.getTotalCredit());

                entryDetails.add(FlujoEfectivoDTO.EntryDetailDTO.builder()
                        .entryId(entry.getId())
                        .entryNumber(entry.getEntryNumber())
                        .description(entry.getDescription())
                        .sourceModule(entry.getSourceModule() != null ? entry.getSourceModule().name() : null)
                        .totalDebit(entry.getTotalDebit())
                        .totalCredit(entry.getTotalCredit())
                        .build());
            }

            BigDecimal netFlow = totalDebit.subtract(totalCredit);

            details.add(FlujoEfectivoDTO.ActivityDetailDTO.builder()
                    .activityType(activityType)
                    .totalDebit(totalDebit)
                    .totalCredit(totalCredit)
                    .netFlow(netFlow)
                    .entries(entryDetails)
                    .build());

            switch (activityType) {
                case "OPERATIVA" -> flujoOperativo = netFlow;
                case "INVERSION" -> flujoInversion = netFlow;
                case "FINANCIACION" -> flujoFinanciacion = netFlow;
            }
        }

        FlujoEfectivoDTO result = FlujoEfectivoDTO.builder()
                .flujoOperativo(flujoOperativo)
                .flujoInversion(flujoInversion)
                .flujoFinanciacion(flujoFinanciacion)
                .flujoNeto(flujoOperativo.add(flujoInversion).add(flujoFinanciacion))
                .details(details)
                .build();

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
    public ResponseEntity<?> getComparativo(Integer year1, Integer month1, Integer year2, Integer month2) {
        log.info("Generando Balance Comparativo entre {}-{} y {}-{}",
                year1, String.format("%02d", month1), year2, String.format("%02d", month2));

        // Generar balance para ambos periodos
        Map<String, BigDecimal> balance1 = calculateBalanceTotals(year1, month1);
        Map<String, BigDecimal> balance2 = calculateBalanceTotals(year2, month2);

        // Construir comparativo
        List<Map<String, Object>> comparison = new ArrayList<>();
        for (String className : List.of("ACTIVOS", "PASIVOS", "PATRIMONIO")) {
            BigDecimal val1 = balance1.getOrDefault(className, BigDecimal.ZERO);
            BigDecimal val2 = balance2.getOrDefault(className, BigDecimal.ZERO);
            BigDecimal variacionAbsoluta = val2.subtract(val1);
            BigDecimal variacionPorcentual = val1.compareTo(BigDecimal.ZERO) != 0
                    ? variacionAbsoluta.multiply(BigDecimal.valueOf(100))
                        .divide(val1, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("className", className);
            row.put("period1Value", val1);
            row.put("period1Label", year1 + "-" + String.format("%02d", month1));
            row.put("period2Value", val2);
            row.put("period2Label", year2 + "-" + String.format("%02d", month2));
            row.put("variacionAbsoluta", variacionAbsoluta);
            row.put("variacionPorcentual", variacionPorcentual);
            comparison.add(row);
        }

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Balance Comparativo generado correctamente"),
                Optional.of(comparison)));
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
        List<JournalEntry> entriesBefore = journalEntryRepository.findPostedBeforePeriod(
                year, month, JournalEntryStatus.POSTED);
        Map<AccountClass, Map<Long, AccountAccumulator>> beforeMap = new LinkedHashMap<>();
        classifyEntryLines(entriesBefore, beforeMap);
        Map<Long, AccountAccumulator> equityBefore = beforeMap.getOrDefault(
                AccountClass.EQUITY, new LinkedHashMap<>());

        // 2. Movimientos del periodo: cuentas clase 3 durante el mes
        List<JournalEntry> entriesPeriod = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);
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
        List<JournalEntry> entries = journalEntryRepository.findPostedUpToPeriod(
                year, month, JournalEntryStatus.POSTED);
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
