package com.sigcon.backend.general.accounting.books.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.general.accounting.books.application.AuxiliarCuentaDTO;
import com.sigcon.backend.general.accounting.books.application.BalanceComprobacionDTO;
import com.sigcon.backend.general.accounting.books.application.LibroDiarioDTO;
import com.sigcon.backend.general.accounting.books.application.LibroMayorDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntryLine;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de libros contables oficiales.
 * Genera los reportes de Libro Diario, Libro Mayor, Balance de Comprobacion
 * y Auxiliares por Cuenta a partir de los asientos contabilizados (POSTED).
 *
 * Estos libros son obligatorios segun la normativa contable colombiana
 * (Decreto 2649/1993 y NIF/NIIF para PYMES).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingBookService {

    private final JournalEntryRepository journalEntryRepository;
    // QA imagen 3: validar accountId antes de construir Libro Mayor para evitar
    // 500 "Unable to find AccountingAccount with id N" cuando el dropdown del
    // frontend tenia un id stale (cambio de tenant, cuenta soft-deleted, etc).
    private final AccountingAccountRepository accountingAccountRepository;

    // ───────────────────────────────────────────────────────────────
    // Libro Diario
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Libro Diario para un periodo contable.
     * Muestra todos los asientos contabilizados del periodo con sus lineas de detalle,
     * ordenados cronologicamente por fecha y numero de asiento.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return lista de asientos con sus lineas, en formato Libro Diario
     */
    public ResponseEntity<?> getLibroDiario(Integer year, Integer month) {
        return getLibroDiario(year, month, null, null, null);
    }

    /**
     * HU-CG-06C E3: Variante con filtros adicionales por cuenta y rango de fechas.
     * Si dateFrom/dateTo vienen, prevalecen sobre year/month. Si accountId viene,
     * filtra solo asientos que tengan al menos una linea con esa cuenta.
     */
    public ResponseEntity<?> getLibroDiario(Integer year, Integer month,
                                              Long accountId, String dateFrom, String dateTo) {
        return getLibroDiario(year, month, accountId, dateFrom, dateTo, null, null);
    }

    /**
     * HU-CG-17 E1 / QA adic#5 (2026-05-25): variante con filtros adicionales por
     * tipo de comprobante (sourceModule) y centro de costo (costCenterId), ademas
     * de cuenta y rango de fechas. Permite la personalizacion de filtrado de libros
     * que pedia el escenario 1 de la HU-CG-17.
     */
    public ResponseEntity<?> getLibroDiario(Integer year, Integer month,
                                              Long accountId, String dateFrom, String dateTo,
                                              String sourceModule, Long costCenterId) {
        List<LibroDiarioDTO> result = buildLibroDiario(year, month);
        // Filtrado en memoria: aceptable porque el resultado de buildLibroDiario ya
        // esta acotado al periodo. Si se requiere optimizar, el filter podria moverse
        // a la query JPA con un metodo nuevo en JournalEntryRepository.
        java.time.LocalDate from = parseDateOrNull(dateFrom);
        java.time.LocalDate to = parseDateOrNull(dateTo);
        if (from != null || to != null) {
            result = result.stream()
                    .filter(d -> (from == null || !d.getDate().isBefore(from))
                              && (to == null || !d.getDate().isAfter(to)))
                    .collect(Collectors.toList());
        }
        if (accountId != null) {
            result = result.stream()
                    .filter(d -> d.getLines() != null && d.getLines().stream()
                            .anyMatch(l -> accountId.equals(l.getAccountingAccountId())))
                    .collect(Collectors.toList());
        }
        // CG-17: filtro por tipo de comprobante (modulo origen)
        if (sourceModule != null && !sourceModule.isBlank()) {
            result = result.stream()
                    .filter(d -> sourceModule.equalsIgnoreCase(d.getSourceModule()))
                    .collect(Collectors.toList());
        }
        // CG-17: filtro por centro de costo (al menos una linea con ese CC)
        if (costCenterId != null) {
            result = result.stream()
                    .filter(d -> d.getLines() != null && d.getLines().stream()
                            .anyMatch(l -> costCenterId.equals(l.getCostCenterId())))
                    .collect(Collectors.toList());
        }
        String periodo = (from != null || to != null)
                ? "rango " + (from != null ? from : "*") + " a " + (to != null ? to : "*")
                : year + "-" + String.format("%02d", month);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Libro Diario generado correctamente para " + periodo),
                Optional.of(result)));
    }

    /** Parsea yyyy-MM-dd o devuelve null si la cadena es invalida o vacia. */
    private static java.time.LocalDate parseDateOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(s.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Construye la lista de DTOs del Libro Diario para un periodo contable.
     * Metodo publico reutilizable para generacion de PDF y otros formatos.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return lista de asientos contabilizados (POSTED) con sus lineas
     */
    public List<LibroDiarioDTO> buildLibroDiario(Integer year, Integer month) {
        log.info("Generando Libro Diario para periodo {}-{}", year, String.format("%02d", month));

        List<JournalEntry> entries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);

        return entries.stream().map(entry -> {
            List<LibroDiarioDTO.LibroDiarioLineDTO> lineDTOs = entry.getLines() != null
                    ? entry.getLines().stream()
                        .sorted(Comparator.comparingInt(JournalEntryLine::getLineOrder))
                        .map(line -> LibroDiarioDTO.LibroDiarioLineDTO.builder()
                                .lineId(line.getId())
                                .lineOrder(line.getLineOrder())
                                .accountingAccountId(line.getAccountingAccount() != null
                                        ? line.getAccountingAccount().getId() : null)
                                .accountCode(line.getAccountingAccount() != null
                                        && line.getAccountingAccount().getPucAccount() != null
                                        ? line.getAccountingAccount().getPucAccount().getCode() : null)
                                .accountName(line.getAccountingAccount() != null
                                        && line.getAccountingAccount().getPucAccount() != null
                                        ? line.getAccountingAccount().getPucAccount().getName() : null)
                                .debitAmount(line.getDebitAmount())
                                .creditAmount(line.getCreditAmount())
                                .description(line.getDescription())
                                .thirdPartyNit(line.getThirdPartyNit())
                                .costCenterId(line.getCostCenter() != null
                                        ? line.getCostCenter().getId() : null)
                                .costCenterName(line.getCostCenter() != null
                                        ? line.getCostCenter().getName() : null)
                                .build())
                        .collect(Collectors.toList())
                    : List.of();

            return LibroDiarioDTO.builder()
                    .entryId(entry.getId())
                    .entryNumber(entry.getEntryNumber())
                    .voucherCode(JournalEntryService.buildVoucherCode(entry))
                    .date(entry.getEntryDate())
                    .description(entry.getDescription())
                    .status(entry.getStatus().name())
                    .sourceModule(entry.getSourceModule() != null ? entry.getSourceModule().name() : null)
                    .totalDebit(entry.getTotalDebit())
                    .totalCredit(entry.getTotalCredit())
                    .lines(lineDTOs)
                    .build();
        }).collect(Collectors.toList());
    }

    // ───────────────────────────────────────────────────────────────
    // Libro Mayor
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Libro Mayor para un periodo contable.
     * Agrupa las lineas de asientos contabilizados por cuenta contable,
     * mostrando el total de debitos, creditos y saldo neto por cuenta.
     * Si se proporciona accountId, filtra a una sola cuenta.
     *
     * @param year      anio del periodo
     * @param month     mes del periodo
     * @param accountId identificador de cuenta especifica (opcional, null = todas)
     * @return lista de cuentas con totales de debito, credito y saldo
     */
    /**
     * HU-CG-06C E3: variante con filtro por rango de fechas. Si dateFrom/dateTo
     * estan presentes, filtra los asientos a las lineas cuyo entryDate caiga en
     * el rango. Reutiliza la logica original cuando no hay rango.
     */
    public ResponseEntity<?> getLibroMayor(Integer year, Integer month, Long accountId,
                                            String dateFrom, String dateTo) {
        java.time.LocalDate from = parseDateOrNull(dateFrom);
        java.time.LocalDate to = parseDateOrNull(dateTo);
        if (from == null && to == null) {
            return getLibroMayor(year, month, accountId);
        }
        // Validar accountId stale
        if (accountId != null) {
            boolean exists = accountingAccountRepository.findById(accountId).isPresent();
            if (!exists) {
                return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("La cuenta seleccionada no existe en esta empresa. "
                                + "Recargue el listado de cuentas."),
                        Optional.of(java.util.Collections.emptyList())));
            }
        }
        // Construir mayor por rango: leer asientos POSTED y filtrar por fecha + cuenta
        List<JournalEntry> entries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);
        Map<Long, LibroMayorAccumulator> map = new LinkedHashMap<>();
        for (JournalEntry e : entries) {
            if (e.getEntryDate() == null) continue;
            if (from != null && e.getEntryDate().isBefore(from)) continue;
            if (to != null && e.getEntryDate().isAfter(to)) continue;
            if (e.getLines() == null) continue;
            for (JournalEntryLine line : e.getLines()) {
                if (line.getAccountingAccount() == null) continue;
                Long accId = line.getAccountingAccount().getId();
                if (accountId != null && !accountId.equals(accId)) continue;
                map.computeIfAbsent(accId, k -> {
                    String code = line.getAccountingAccount().getPucAccount() != null
                            ? line.getAccountingAccount().getPucAccount().getCode() : "";
                    String name = line.getAccountingAccount().getPucAccount() != null
                            ? line.getAccountingAccount().getPucAccount().getName() : "";
                    return new LibroMayorAccumulator(accId, code, name,
                            line.getAccountingAccount().getNature());
                });
                LibroMayorAccumulator acc = map.get(accId);
                acc.totalDebit = acc.totalDebit.add(
                        line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
                acc.totalCredit = acc.totalCredit.add(
                        line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
            }
        }
        List<LibroMayorDTO> result = map.values().stream()
                .sorted(Comparator.comparing(a -> a.pucCode))
                .map(acc -> LibroMayorDTO.builder()
                        .accountId(acc.accountId)
                        .pucCode(acc.pucCode)
                        .accountName(acc.accountName)
                        .totalDebit(acc.totalDebit)
                        .totalCredit(acc.totalCredit)
                        .balance(acc.nature == AccountNature.CREDIT
                                ? acc.totalCredit.subtract(acc.totalDebit)
                                : acc.totalDebit.subtract(acc.totalCredit))
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Libro Mayor generado correctamente para rango "
                        + (from != null ? from : "*") + " a " + (to != null ? to : "*")),
                Optional.of(result)));
    }

    public ResponseEntity<?> getLibroMayor(Integer year, Integer month, Long accountId) {
        // QA imagen 3 / AP-01 anexo: si el frontend manda un accountId que no
        // existe en el tenant (state stale o cuenta soft-deleted), evitar 500
        // y devolver lista vacia con mensaje claro. El @PostLoad de la entidad
        // tiraba TenantIsolationException -> mapeada a 404 con texto genérico,
        // y tambien Hibernate podia tirar EntityNotFoundException 500.
        if (accountId != null) {
            boolean exists = accountingAccountRepository.findById(accountId).isPresent();
            if (!exists) {
                return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("La cuenta seleccionada no existe en esta empresa. "
                                + "Recargue el listado de cuentas."),
                        Optional.of(java.util.Collections.emptyList())));
            }
        }
        List<LibroMayorDTO> result = buildLibroMayor(year, month, accountId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Libro Mayor generado correctamente para " + year + "-" + String.format("%02d", month)),
                Optional.of(result)));
    }

    /**
     * Construye la lista de DTOs del Libro Mayor para un periodo contable.
     * Metodo publico reutilizable para generacion de PDF y otros formatos.
     *
     * @param year      anio del periodo
     * @param month     mes del periodo
     * @param accountId identificador de cuenta especifica (opcional)
     * @return lista de cuentas con totales de debito, credito y saldo
     */
    public List<LibroMayorDTO> buildLibroMayor(Integer year, Integer month, Long accountId) {
        log.info("Generando Libro Mayor para periodo {}-{}, cuenta: {}",
                year, String.format("%02d", month), accountId != null ? accountId : "TODAS");

        List<JournalEntry> entries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);

        // Agrupar lineas por cuenta contable
        Map<Long, LibroMayorAccumulator> accountMap = new LinkedHashMap<>();

        for (JournalEntry entry : entries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (line.getAccountingAccount() == null) continue;
                Long accId = line.getAccountingAccount().getId();

                // Si se especifico una cuenta, filtrar solo esa
                if (accountId != null && !accountId.equals(accId)) continue;

                accountMap.computeIfAbsent(accId, k -> {
                    String code = line.getAccountingAccount().getPucAccount() != null
                            ? line.getAccountingAccount().getPucAccount().getCode() : "";
                    String name = line.getAccountingAccount().getPucAccount() != null
                            ? line.getAccountingAccount().getPucAccount().getName() : "";
                    return new LibroMayorAccumulator(accId, code, name,
                            line.getAccountingAccount().getNature());
                });

                LibroMayorAccumulator acc = accountMap.get(accId);
                acc.totalDebit = acc.totalDebit.add(
                        line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
                acc.totalCredit = acc.totalCredit.add(
                        line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
            }
        }

        return accountMap.values().stream()
                .sorted(Comparator.comparing(a -> a.pucCode))
                .map(acc -> LibroMayorDTO.builder()
                        .accountId(acc.accountId)
                        .pucCode(acc.pucCode)
                        .accountName(acc.accountName)
                        .totalDebit(acc.totalDebit)
                        .totalCredit(acc.totalCredit)
                        // HU-CG-01B E3 / HU-CG-06B E3: saldo respeta la naturaleza de la cuenta.
                        // Naturaleza CREDIT (Pasivo/Patrimonio/Ingresos): saldo = credito - debito
                        // Naturaleza DEBIT  (Activo/Gasto/Costo):         saldo = debito - credito
                        .balance(acc.nature == AccountNature.CREDIT
                                ? acc.totalCredit.subtract(acc.totalDebit)
                                : acc.totalDebit.subtract(acc.totalCredit))
                        .build())
                .collect(Collectors.toList());
    }

    // ───────────────────────────────────────────────────────────────
    // Balance de Comprobacion
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Balance de Comprobacion para un periodo contable.
     * Incluye tres columnas por cuenta: saldo anterior, movimientos del periodo y saldo final.
     * El saldo anterior se calcula con todos los asientos contabilizados de periodos anteriores.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return lista de cuentas con saldo anterior, movimiento y saldo final
     */
    public ResponseEntity<?> getBalanceComprobacion(Integer year, Integer month) {
        List<BalanceComprobacionDTO> result = buildBalanceComprobacion(year, month);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Balance de Comprobacion generado correctamente para " + year + "-" + String.format("%02d", month)),
                Optional.of(result)));
    }

    /**
     * Construye la lista de DTOs del Balance de Comprobacion para un periodo.
     * Metodo publico reutilizable para generacion de PDF y otros formatos.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return lista de cuentas con saldo anterior, movimiento y saldo final
     */
    public List<BalanceComprobacionDTO> buildBalanceComprobacion(Integer year, Integer month) {
        log.info("Generando Balance de Comprobacion para periodo {}-{}",
                year, String.format("%02d", month));

        // QA Bloque AS-CG (2026-05-25): si el anio solicitado no tiene comprobantes
        // POSTED/REVERSED para este tenant, NO se genera nada. Antes se arrastraban
        // los saldos del anio anterior como "saldo anterior" al consultar un anio
        // inexistente o sin movimientos (ej. 2027), lo cual confundia al usuario.
        if (!journalEntryRepository.hasPostedEntriesInYear(year, JournalEntryStatus.POSTED)) {
            return java.util.Collections.emptyList();
        }

        // 1. Calcular saldos anteriores (todos los periodos antes de este)
        List<JournalEntry> previousEntries = journalEntryRepository.findPostedBeforePeriod(
                year, month, JournalEntryStatus.POSTED);
        Map<Long, BalanceAccumulator> accountMap = new LinkedHashMap<>();
        accumulateEntries(previousEntries, accountMap, true);

        // 2. Calcular movimientos del periodo actual
        List<JournalEntry> currentEntries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);
        accumulateEntries(currentEntries, accountMap, false);

        // 3. Construir DTOs
        return accountMap.values().stream()
                .sorted(Comparator.comparing(a -> a.pucCode))
                .map(acc -> {
                    // Saldo anterior: positivo = debito, negativo = credito
                    BigDecimal saldoAnterior = acc.previousDebit.subtract(acc.previousCredit);
                    BigDecimal saldoAnteriorDebit = saldoAnterior.compareTo(BigDecimal.ZERO) > 0
                            ? saldoAnterior : BigDecimal.ZERO;
                    BigDecimal saldoAnteriorCredit = saldoAnterior.compareTo(BigDecimal.ZERO) < 0
                            ? saldoAnterior.negate() : BigDecimal.ZERO;

                    // Saldo final
                    BigDecimal saldoFinal = saldoAnterior.add(acc.currentDebit).subtract(acc.currentCredit);
                    BigDecimal saldoFinalDebit = saldoFinal.compareTo(BigDecimal.ZERO) > 0
                            ? saldoFinal : BigDecimal.ZERO;
                    BigDecimal saldoFinalCredit = saldoFinal.compareTo(BigDecimal.ZERO) < 0
                            ? saldoFinal.negate() : BigDecimal.ZERO;

                    return BalanceComprobacionDTO.builder()
                            .accountId(acc.accountId)
                            .pucCode(acc.pucCode)
                            .accountName(acc.accountName)
                            .saldoAnteriorDebit(saldoAnteriorDebit)
                            .saldoAnteriorCredit(saldoAnteriorCredit)
                            .movimientoDebit(acc.currentDebit)
                            .movimientoCredit(acc.currentCredit)
                            .saldoFinalDebit(saldoFinalDebit)
                            .saldoFinalCredit(saldoFinalCredit)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ───────────────────────────────────────────────────────────────
    // Auxiliares por Cuenta
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el Auxiliar por Cuenta para una cuenta contable especifica en un periodo.
     * Muestra cada movimiento con saldo acumulado progresivo (running balance).
     *
     * @param year      anio del periodo
     * @param month     mes del periodo
     * @param accountId identificador de la cuenta contable
     * @return lista de movimientos con saldo acumulado
     */
    public ResponseEntity<?> getAuxiliaresCuentas(Integer year, Integer month, Long accountId) {
        if (accountId == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "El parametro accountId es obligatorio para el Auxiliar por Cuenta."));
        }
        List<AuxiliarCuentaDTO> result = buildAuxiliaresCuentas(year, month, accountId);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Auxiliar por Cuenta generado correctamente para " + year + "-" + String.format("%02d", month)),
                Optional.of(result)));
    }

    /**
     * Construye la lista de DTOs del Auxiliar por Cuenta para un periodo.
     * Metodo publico reutilizable para generacion de PDF y otros formatos.
     *
     * @param year      anio del periodo
     * @param month     mes del periodo
     * @param accountId identificador de la cuenta contable (obligatorio)
     * @return lista de movimientos con saldo acumulado
     */
    public List<AuxiliarCuentaDTO> buildAuxiliaresCuentas(Integer year, Integer month, Long accountId) {
        log.info("Generando Auxiliar por Cuenta {} para periodo {}-{}",
                accountId, year, String.format("%02d", month));

        // 1. Calcular saldo anterior de la cuenta
        List<JournalEntry> previousEntries = journalEntryRepository.findPostedBeforePeriod(
                year, month, JournalEntryStatus.POSTED);
        BigDecimal saldoAnterior = BigDecimal.ZERO;
        for (JournalEntry entry : previousEntries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (line.getAccountingAccount() != null && accountId.equals(line.getAccountingAccount().getId())) {
                    saldoAnterior = saldoAnterior
                            .add(line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO)
                            .subtract(line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
                }
            }
        }

        // 2. Obtener movimientos del periodo actual
        List<JournalEntry> currentEntries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);

        BigDecimal runningBalance = saldoAnterior;
        List<AuxiliarCuentaDTO> result = new ArrayList<>();

        // Agregar fila de saldo inicial si hay saldo anterior
        if (saldoAnterior.compareTo(BigDecimal.ZERO) != 0) {
            result.add(AuxiliarCuentaDTO.builder()
                    .date(null)
                    .entryNumber(null)
                    .description("Saldo anterior")
                    .debit(BigDecimal.ZERO)
                    .credit(BigDecimal.ZERO)
                    .runningBalance(saldoAnterior)
                    .build());
        }

        // 3. Procesar cada linea del periodo para la cuenta
        for (JournalEntry entry : currentEntries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (line.getAccountingAccount() == null
                        || !accountId.equals(line.getAccountingAccount().getId())) continue;

                BigDecimal debit = line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
                BigDecimal credit = line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;
                runningBalance = runningBalance.add(debit).subtract(credit);

                result.add(AuxiliarCuentaDTO.builder()
                        .date(entry.getEntryDate())
                        .entryNumber(entry.getEntryNumber())
                        .description(line.getDescription() != null ? line.getDescription() : entry.getDescription())
                        .debit(debit)
                        .credit(credit)
                        .runningBalance(runningBalance)
                        .build());
            }
        }

        return result;
    }

    // ───────────────────────────────────────────────────────────────
    // Clases auxiliares internas
    // ───────────────────────────────────────────────────────────────

    /**
     * Acumula los debitos y creditos de un conjunto de asientos en un mapa por cuenta.
     *
     * @param entries    lista de asientos a procesar
     * @param accountMap mapa acumulador por cuenta
     * @param isPrevious true si son periodos anteriores (saldo anterior), false si es periodo actual
     */
    private void accumulateEntries(List<JournalEntry> entries, Map<Long, BalanceAccumulator> accountMap,
                                   boolean isPrevious) {
        for (JournalEntry entry : entries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (line.getAccountingAccount() == null) continue;
                Long accId = line.getAccountingAccount().getId();

                accountMap.computeIfAbsent(accId, k -> {
                    String code = line.getAccountingAccount().getPucAccount() != null
                            ? line.getAccountingAccount().getPucAccount().getCode() : "";
                    String name = line.getAccountingAccount().getPucAccount() != null
                            ? line.getAccountingAccount().getPucAccount().getName() : "";
                    return new BalanceAccumulator(accId, code, name);
                });

                BalanceAccumulator acc = accountMap.get(accId);
                BigDecimal debit = line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
                BigDecimal credit = line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;

                if (isPrevious) {
                    acc.previousDebit = acc.previousDebit.add(debit);
                    acc.previousCredit = acc.previousCredit.add(credit);
                } else {
                    acc.currentDebit = acc.currentDebit.add(debit);
                    acc.currentCredit = acc.currentCredit.add(credit);
                }
            }
        }
    }

    /** Acumulador interno para el Libro Mayor. */
    private static class LibroMayorAccumulator {
        Long accountId;
        String pucCode;
        String accountName;
        AccountNature nature;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        LibroMayorAccumulator(Long accountId, String pucCode, String accountName, AccountNature nature) {
            this.accountId = accountId;
            this.pucCode = pucCode;
            this.accountName = accountName;
            this.nature = nature;
        }
    }

    /** Acumulador interno para el Balance de Comprobacion. */
    private static class BalanceAccumulator {
        Long accountId;
        String pucCode;
        String accountName;
        BigDecimal previousDebit = BigDecimal.ZERO;
        BigDecimal previousCredit = BigDecimal.ZERO;
        BigDecimal currentDebit = BigDecimal.ZERO;
        BigDecimal currentCredit = BigDecimal.ZERO;

        BalanceAccumulator(Long accountId, String pucCode, String accountName) {
            this.accountId = accountId;
            this.pucCode = pucCode;
            this.accountName = accountName;
        }
    }
}
