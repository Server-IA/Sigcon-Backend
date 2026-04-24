package com.sigcon.backend.general.accounting.closing.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigcon.backend.general.accounting.AccountingPeriod;
import com.sigcon.backend.general.accounting.AccountingPeriodRepository;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.AccountingPeriodStatus;
import com.sigcon.backend.general.accounting.closing.application.ClosingEntryDTO;
import com.sigcon.backend.general.accounting.closing.application.ClosingPreviewDTO;
import com.sigcon.backend.general.accounting.closing.domain.model.ClosingEntry;
import com.sigcon.backend.general.accounting.closing.domain.model.enums.ClosingStatus;
import com.sigcon.backend.general.accounting.closing.domain.model.enums.ClosingType;
import com.sigcon.backend.general.accounting.closing.domain.repository.ClosingEntryRepository;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntryLine;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de cierre contable.
 * Gestiona el cierre mensual, anual y la generacion de asientos de apertura.
 *
 * El cierre contable consiste en transferir los saldos de las cuentas de resultado
 * (clases 4, 5, 6, 7) a la cuenta de utilidad del ejercicio (clase 3),
 * dejando las cuentas de resultado en cero para el siguiente periodo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClosingService {

    private final ClosingEntryRepository closingEntryRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryService journalEntryService;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;

    // ───────────────────────────────────────────────────────────────
    // Cierre mensual
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el cierre contable mensual para un periodo.
     * 1. Valida que el periodo este abierto y sin asientos en borrador
     * 2. Calcula saldos netos de cuentas de resultado (clases 4, 5, 6, 7)
     * 3. Genera asiento de cierre: debita ingresos, acredita gastos/costos, neto a utilidad
     * 4. Registra el cierre y cierra el periodo
     *
     * @param year      anio del periodo
     * @param month     mes del periodo
     * @param notes     notas opcionales
     * @param createdBy usuario que ejecuta el cierre
     * @return registro de cierre creado
     */
    @Transactional
    public ResponseEntity<?> generateMonthlyClosing(Integer year, Integer month, String notes, String createdBy) {
        log.info("Ejecutando cierre mensual para {}-{} por usuario {}", year, String.format("%02d", month), createdBy);

        // 1. Validar que no exista cierre previo
        if (closingEntryRepository.existsByFiscalYearAndFiscalMonthAndClosingTypeAndStatusAndDeletedAtIsNull(
                year, month, ClosingType.MONTHLY, ClosingStatus.COMPLETED)) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Ya existe un cierre mensual para el periodo " + year + "-" + String.format("%02d", month))));
        }

        // 2. Validar periodo abierto
        if (!accountingPeriodService.isPeriodOpen(year, month)) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "El periodo " + year + "-" + String.format("%02d", month) + " no esta abierto.")));
        }

        // 3. Validar que no haya asientos en borrador
        long draftCount = journalEntryService.countDraftsByPeriod(year, month);
        if (draftCount > 0) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "No se puede cerrar el periodo. Existen " + draftCount
                            + " asiento(s) en estado BORRADOR. Debe contabilizarlos o eliminarlos primero.")));
        }

        // 4. Calcular saldos de cuentas de resultado
        ClosingCalculation calc = calculateResultAccounts(year, month);
        if (calc.lines.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "No se encontraron movimientos en cuentas de resultado para el periodo.")));
        }

        // 5. Crear asiento de cierre via JournalEntryService
        CreateJournalEntryRequest entryRequest = CreateJournalEntryRequest.builder()
                .entryDate(LocalDate.of(year, month, getLastDayOfMonth(year, month)))
                .description("Cierre mensual " + year + "-" + String.format("%02d", month))
                .sourceModule(JournalSourceModule.CG)
                .lines(calc.lines)
                .build();

        JournalEntryDTO journalEntry = journalEntryService.createEntry(entryRequest, createdBy);
        // Contabilizar inmediatamente
        journalEntryService.postEntry(journalEntry.getId());

        // 6. Registrar cierre
        ClosingEntry closing = ClosingEntry.builder()
                .fiscalYear(year)
                .fiscalMonth(month)
                .closingType(ClosingType.MONTHLY)
                .journalEntryId(journalEntry.getId())
                .status(ClosingStatus.COMPLETED)
                .notes(notes)
                .createdBy(createdBy)
                .build();
        closingEntryRepository.save(closing);
        auditPublisher.publishCreate(AuditModule.CG, "ClosingEntry", closing.getId(), "ClosingEntry creado id=" + closing.getId());

        // 7. Cerrar el periodo contable
        AccountingPeriod period = accountingPeriodRepository.findByYearAndMonth(year, month).orElse(null);
        if (period != null && period.isOpen()) {
            accountingPeriodService.closePeriod(period.getId(), createdBy, "Cierre mensual automatico");
        }

        log.info("Cierre mensual completado para {}-{}. Asiento #{}", year, String.format("%02d", month), journalEntry.getEntryNumber());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cierre mensual ejecutado correctamente para " + year + "-" + String.format("%02d", month)),
                Optional.of(toDTO(closing))));
    }

    // ───────────────────────────────────────────────────────────────
    // Preview de cierre mensual
    // ───────────────────────────────────────────────────────────────

    /**
     * Previsualizacion del cierre mensual sin ejecutar.
     * Calcula las lineas del asiento de cierre para que el usuario las revise.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return preview con las lineas del asiento de cierre
     */
    public ResponseEntity<?> previewMonthlyClosing(Integer year, Integer month) {
        log.info("Generando preview de cierre mensual para {}-{}", year, String.format("%02d", month));

        // Validaciones basicas
        long draftCount = journalEntryService.countDraftsByPeriod(year, month);
        if (draftCount > 0) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Existen " + draftCount + " asiento(s) en estado BORRADOR.")));
        }

        ClosingCalculation calc = calculateResultAccounts(year, month);

        // Construir preview
        List<ClosingPreviewDTO.ClosingLinePreviewDTO> previewLines = calc.lines.stream()
                .map(line -> ClosingPreviewDTO.ClosingLinePreviewDTO.builder()
                        .accountingAccountId(line.getAccountingAccountId())
                        .accountCode(calc.accountCodes.getOrDefault(line.getAccountingAccountId(), ""))
                        .accountName(calc.accountNames.getOrDefault(line.getAccountingAccountId(), ""))
                        .debitAmount(line.getDebitAmount())
                        .creditAmount(line.getCreditAmount())
                        .description(line.getDescription())
                        .build())
                .toList();

        BigDecimal totalDebit = calc.lines.stream()
                .map(CreateJournalEntryLineRequest::getDebitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = calc.lines.stream()
                .map(CreateJournalEntryLineRequest::getCreditAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String netLabel = calc.netResult.compareTo(BigDecimal.ZERO) >= 0
                ? "Utilidad del ejercicio" : "Perdida del ejercicio";

        ClosingPreviewDTO preview = ClosingPreviewDTO.builder()
                .fiscalYear(year)
                .fiscalMonth(month)
                .closingType("MONTHLY")
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .netResult(calc.netResult)
                .netResultLabel(netLabel)
                .lines(previewLines)
                .build();

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Preview de cierre generado correctamente"),
                Optional.of(preview)));
    }

    // ───────────────────────────────────────────────────────────────
    // Cierre anual
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el cierre contable anual.
     * 1. Cierra todos los meses abiertos del anio
     * 2. Genera asiento de cierre anual consolidado
     *
     * @param year      anio fiscal
     * @param notes     notas opcionales
     * @param createdBy usuario que ejecuta el cierre
     * @return registro de cierre anual
     */
    @Transactional
    public ResponseEntity<?> generateAnnualClosing(Integer year, String notes, String createdBy) {
        log.info("Ejecutando cierre anual para {} por usuario {}", year, createdBy);

        // Validar que no exista cierre anual previo
        if (closingEntryRepository.existsByFiscalYearAndFiscalMonthAndClosingTypeAndStatusAndDeletedAtIsNull(
                year, 12, ClosingType.ANNUAL, ClosingStatus.COMPLETED)) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Ya existe un cierre anual para el anio " + year)));
        }

        // Cerrar todos los meses abiertos
        List<AccountingPeriod> openPeriods = accountingPeriodRepository.findByYear(year).stream()
                .filter(AccountingPeriod::isOpen)
                .sorted(Comparator.comparingInt(AccountingPeriod::getMonth))
                .toList();

        for (AccountingPeriod period : openPeriods) {
            // Verificar si hay drafts
            long draftCount = journalEntryService.countDraftsByPeriod(year, period.getMonth());
            if (draftCount > 0) {
                return ResponseEntity.badRequest().body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(
                                "No se puede ejecutar cierre anual. El periodo " + year + "-"
                                + String.format("%02d", period.getMonth())
                                + " tiene " + draftCount + " asiento(s) en BORRADOR.")));
            }
        }

        // Calcular saldos de resultado acumulados del anio completo
        ClosingCalculation calc = calculateAnnualResultAccounts(year);
        if (calc.lines.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "No se encontraron movimientos en cuentas de resultado para el anio " + year)));
        }

        // Crear asiento de cierre anual
        CreateJournalEntryRequest entryRequest = CreateJournalEntryRequest.builder()
                .entryDate(LocalDate.of(year, 12, 31))
                .description("Cierre anual " + year)
                .sourceModule(JournalSourceModule.CG)
                .lines(calc.lines)
                .build();

        JournalEntryDTO journalEntry = journalEntryService.createEntry(entryRequest, createdBy);
        journalEntryService.postEntry(journalEntry.getId());

        // Registrar cierre anual
        ClosingEntry closing = ClosingEntry.builder()
                .fiscalYear(year)
                .fiscalMonth(12)
                .closingType(ClosingType.ANNUAL)
                .journalEntryId(journalEntry.getId())
                .status(ClosingStatus.COMPLETED)
                .notes(notes)
                .createdBy(createdBy)
                .build();
        closingEntryRepository.save(closing);
        auditPublisher.publishCreate(AuditModule.CG, "ClosingEntry", closing.getId(), "ClosingEntry creado id=" + closing.getId());

        // Cerrar los meses abiertos
        for (AccountingPeriod period : openPeriods) {
            accountingPeriodService.closePeriod(period.getId(), createdBy, "Cierre anual automatico");
        }

        log.info("Cierre anual completado para {}. Asiento #{}", year, journalEntry.getEntryNumber());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cierre anual ejecutado correctamente para " + year),
                Optional.of(toDTO(closing))));
    }

    // ───────────────────────────────────────────────────────────────
    // Asiento de apertura
    // ───────────────────────────────────────────────────────────────

    /**
     * Genera el asiento de apertura para un nuevo anio fiscal.
     * Toma los saldos de las cuentas de balance (clases 1, 2, 3) al cierre
     * del anio anterior y crea un asiento de apertura en el nuevo anio.
     *
     * @param year      anio fiscal para el cual generar la apertura
     * @param notes     notas opcionales
     * @param createdBy usuario que ejecuta la apertura
     * @return registro de apertura creado
     */
    @Transactional
    public ResponseEntity<?> generateOpeningEntry(Integer year, String notes, String createdBy) {
        log.info("Generando asiento de apertura para {} por usuario {}", year, createdBy);

        // Validar que no exista apertura previa
        if (closingEntryRepository.existsByFiscalYearAndFiscalMonthAndClosingTypeAndStatusAndDeletedAtIsNull(
                year, 1, ClosingType.OPENING, ClosingStatus.COMPLETED)) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Ya existe un asiento de apertura para el anio " + year)));
        }

        // Calcular saldos de cuentas de balance al final del anio anterior
        int previousYear = year - 1;
        List<JournalEntry> entries = journalEntryRepository.findPostedUpToPeriod(
                previousYear, 12, JournalEntryStatus.POSTED);

        // Agrupar por cuenta solo clases de balance (1, 2, 3)
        Map<Long, AccountBalance> balances = new LinkedHashMap<>();
        for (JournalEntry entry : entries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (line.getAccountingAccount() == null
                        || line.getAccountingAccount().getPucAccount() == null) continue;

                AccountClass accountClass = line.getAccountingAccount().getPucAccount().getAccountClass();
                if (accountClass != AccountClass.ASSET
                        && accountClass != AccountClass.LIABILITY
                        && accountClass != AccountClass.EQUITY) continue;

                Long accId = line.getAccountingAccount().getId();
                balances.computeIfAbsent(accId, k -> new AccountBalance(
                        accId,
                        line.getAccountingAccount().getPucAccount().getCode(),
                        accountClass));

                AccountBalance bal = balances.get(accId);
                bal.totalDebit = bal.totalDebit.add(
                        line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
                bal.totalCredit = bal.totalCredit.add(
                        line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
            }
        }

        if (balances.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "No se encontraron saldos de balance para el anio " + previousYear)));
        }

        // Construir lineas de apertura
        List<CreateJournalEntryLineRequest> lines = new ArrayList<>();
        for (AccountBalance bal : balances.values()) {
            BigDecimal netBalance = bal.totalDebit.subtract(bal.totalCredit);
            if (netBalance.compareTo(BigDecimal.ZERO) == 0) continue;

            boolean isDebitNature = bal.accountClass == AccountClass.ASSET;

            lines.add(CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(bal.accountId)
                    .debitAmount(netBalance.compareTo(BigDecimal.ZERO) > 0 ? netBalance : BigDecimal.ZERO)
                    .creditAmount(netBalance.compareTo(BigDecimal.ZERO) < 0 ? netBalance.negate() : BigDecimal.ZERO)
                    .description("Saldo de apertura " + year)
                    .build());
        }

        if (lines.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Todos los saldos de balance son cero. No se requiere asiento de apertura.")));
        }

        // Crear asiento de apertura
        CreateJournalEntryRequest entryRequest = CreateJournalEntryRequest.builder()
                .entryDate(LocalDate.of(year, 1, 1))
                .description("Asiento de apertura " + year)
                .sourceModule(JournalSourceModule.CG)
                .lines(lines)
                .build();

        JournalEntryDTO journalEntry = journalEntryService.createEntry(entryRequest, createdBy);
        journalEntryService.postEntry(journalEntry.getId());

        // Registrar apertura
        ClosingEntry closing = ClosingEntry.builder()
                .fiscalYear(year)
                .fiscalMonth(1)
                .closingType(ClosingType.OPENING)
                .journalEntryId(journalEntry.getId())
                .status(ClosingStatus.COMPLETED)
                .notes(notes)
                .createdBy(createdBy)
                .build();
        closingEntryRepository.save(closing);
        auditPublisher.publishCreate(AuditModule.CG, "ClosingEntry", closing.getId(), "ClosingEntry creado id=" + closing.getId());

        log.info("Asiento de apertura generado para {}. Asiento #{}", year, journalEntry.getEntryNumber());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Asiento de apertura generado correctamente para " + year),
                Optional.of(toDTO(closing))));
    }

    // ───────────────────────────────────────────────────────────────
    // Metodos auxiliares privados
    // ───────────────────────────────────────────────────────────────

    /**
     * Calcula los saldos de las cuentas de resultado (clases 4, 5, 6, 7)
     * para un periodo mensual y construye las lineas del asiento de cierre.
     * El asiento cierra las cuentas de resultado a cero, transfiriendo
     * el resultado neto a una cuenta de patrimonio (utilidad del ejercicio).
     */
    private ClosingCalculation calculateResultAccounts(Integer year, Integer month) {
        List<JournalEntry> entries = journalEntryRepository.findByPeriodAndStatus(
                year, month, JournalEntryStatus.POSTED);
        return buildClosingLines(entries);
    }

    /**
     * Calcula los saldos de las cuentas de resultado para todo el anio fiscal.
     */
    private ClosingCalculation calculateAnnualResultAccounts(Integer year) {
        List<JournalEntry> entries = journalEntryRepository.findPostedUpToPeriod(
                year, 12, JournalEntryStatus.POSTED);

        // Filtrar solo asientos del anio indicado
        List<JournalEntry> yearEntries = entries.stream()
                .filter(e -> e.getPeriodYear() != null && e.getPeriodYear().equals(year))
                .toList();

        return buildClosingLines(yearEntries);
    }

    /**
     * Construye las lineas del asiento de cierre a partir de los asientos proporcionados.
     * Para cuentas de resultado:
     * - Ingresos (clase 4, naturaleza credito): se debitan para cerrar
     * - Gastos (clase 5, naturaleza debito): se acreditan para cerrar
     * - Costos de venta (clase 6, naturaleza debito): se acreditan para cerrar
     * - Costos produccion (clase 7, naturaleza debito): se acreditan para cerrar
     * El neto va a una linea de utilidad del ejercicio.
     */
    private ClosingCalculation buildClosingLines(List<JournalEntry> entries) {
        Map<Long, ResultAccountBalance> accountBalances = new LinkedHashMap<>();
        Map<Long, String> accountCodes = new LinkedHashMap<>();
        Map<Long, String> accountNames = new LinkedHashMap<>();

        for (JournalEntry entry : entries) {
            if (entry.getLines() == null) continue;
            for (JournalEntryLine line : entry.getLines()) {
                if (line.getAccountingAccount() == null
                        || line.getAccountingAccount().getPucAccount() == null) continue;

                AccountClass accountClass = line.getAccountingAccount().getPucAccount().getAccountClass();
                // Solo cuentas de resultado
                if (accountClass != AccountClass.REVENUE
                        && accountClass != AccountClass.EXPENSE
                        && accountClass != AccountClass.COST_OF_SALES
                        && accountClass != AccountClass.PRODUCTION_COST) continue;

                Long accId = line.getAccountingAccount().getId();
                accountBalances.computeIfAbsent(accId, k -> new ResultAccountBalance(accId, accountClass));
                accountCodes.putIfAbsent(accId, line.getAccountingAccount().getPucAccount().getCode());
                accountNames.putIfAbsent(accId, line.getAccountingAccount().getPucAccount().getName());

                ResultAccountBalance bal = accountBalances.get(accId);
                bal.totalDebit = bal.totalDebit.add(
                        line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
                bal.totalCredit = bal.totalCredit.add(
                        line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
            }
        }

        // Construir lineas de cierre
        List<CreateJournalEntryLineRequest> lines = new ArrayList<>();
        BigDecimal totalIngresos = BigDecimal.ZERO;
        BigDecimal totalGastosCostos = BigDecimal.ZERO;

        for (ResultAccountBalance bal : accountBalances.values()) {
            BigDecimal netBalance = bal.totalDebit.subtract(bal.totalCredit);
            if (netBalance.compareTo(BigDecimal.ZERO) == 0) continue;

            if (bal.accountClass == AccountClass.REVENUE) {
                // Ingresos: naturaleza credito, saldo normal credito (credito > debito)
                // Para cerrar: debitar el saldo credito
                BigDecimal creditBalance = bal.totalCredit.subtract(bal.totalDebit);
                if (creditBalance.compareTo(BigDecimal.ZERO) > 0) {
                    totalIngresos = totalIngresos.add(creditBalance);
                    lines.add(CreateJournalEntryLineRequest.builder()
                            .accountingAccountId(bal.accountId)
                            .debitAmount(creditBalance)
                            .creditAmount(BigDecimal.ZERO)
                            .description("Cierre cuenta de ingreso")
                            .build());
                }
            } else {
                // Gastos/Costos: naturaleza debito, saldo normal debito (debito > credito)
                // Para cerrar: acreditar el saldo debito
                BigDecimal debitBalance = bal.totalDebit.subtract(bal.totalCredit);
                if (debitBalance.compareTo(BigDecimal.ZERO) > 0) {
                    totalGastosCostos = totalGastosCostos.add(debitBalance);
                    lines.add(CreateJournalEntryLineRequest.builder()
                            .accountingAccountId(bal.accountId)
                            .debitAmount(BigDecimal.ZERO)
                            .creditAmount(debitBalance)
                            .description("Cierre cuenta de " + bal.accountClass.name().toLowerCase())
                            .build());
                }
            }
        }

        // Resultado neto: ingresos - gastos/costos
        BigDecimal netResult = totalIngresos.subtract(totalGastosCostos);

        // Bug fix post-Bloque G: el asiento SIEMPRE queda desbalanceado
        // a menos que ingresos == gastos/costos (caso raro). Agregamos la
        // linea de utilidad/perdida contra la cuenta 3605 (auto-provisionada
        // per-tenant en V10-G). La direccion depende del signo del neto:
        //   netResult > 0  -> ganancia -> CREDITO a 3605 (saldo natural)
        //   netResult < 0  -> perdida  -> DEBITO  a 3605 (contra saldo)
        // Si netResult == 0 no se agrega la linea (no hay utilidad ni perdida).
        if (netResult.compareTo(BigDecimal.ZERO) != 0) {
            Long utilidadAcctId = accountMappingService.resolveOrThrow(
                    AccountingConcept.UTILIDAD_EJERCICIO);
            BigDecimal amount = netResult.abs();
            if (netResult.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(utilidadAcctId)
                        .debitAmount(BigDecimal.ZERO)
                        .creditAmount(amount)
                        .description("Utilidad del ejercicio (cierre)")
                        .build());
            } else {
                lines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(utilidadAcctId)
                        .debitAmount(amount)
                        .creditAmount(BigDecimal.ZERO)
                        .description("Perdida del ejercicio (cierre)")
                        .build());
            }
        }

        ClosingCalculation calc = new ClosingCalculation();
        calc.lines = lines;
        calc.netResult = netResult;
        calc.accountCodes = accountCodes;
        calc.accountNames = accountNames;
        return calc;
    }

    /** Obtiene el ultimo dia del mes. */
    private int getLastDayOfMonth(int year, int month) {
        return LocalDate.of(year, month, 1).lengthOfMonth();
    }

    /** Convierte la entidad ClosingEntry a su DTO de lectura. */
    private ClosingEntryDTO toDTO(ClosingEntry entity) {
        return ClosingEntryDTO.builder()
                .id(entity.getId())
                .fiscalYear(entity.getFiscalYear())
                .fiscalMonth(entity.getFiscalMonth())
                .closingType(entity.getClosingType().name())
                .journalEntryId(entity.getJournalEntryId())
                .status(entity.getStatus().name())
                .notes(entity.getNotes())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /** Resultado del calculo de cierre con lineas y metadata. */
    private static class ClosingCalculation {
        List<CreateJournalEntryLineRequest> lines = new ArrayList<>();
        BigDecimal netResult = BigDecimal.ZERO;
        Map<Long, String> accountCodes = new LinkedHashMap<>();
        Map<Long, String> accountNames = new LinkedHashMap<>();
    }

    /** Acumulador de saldos por cuenta de resultado. */
    private static class ResultAccountBalance {
        Long accountId;
        AccountClass accountClass;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        ResultAccountBalance(Long accountId, AccountClass accountClass) {
            this.accountId = accountId;
            this.accountClass = accountClass;
        }
    }

    /** Acumulador de saldos por cuenta de balance (para apertura). */
    private static class AccountBalance {
        Long accountId;
        String pucCode;
        AccountClass accountClass;
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        AccountBalance(Long accountId, String pucCode, AccountClass accountClass) {
            this.accountId = accountId;
            this.pucCode = pucCode;
            this.accountClass = accountClass;
        }
    }
}
