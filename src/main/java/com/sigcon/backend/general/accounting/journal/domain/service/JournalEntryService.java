package com.sigcon.backend.general.accounting.journal.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryLineDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntryLine;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryLineRepository;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.cost_centers.domain.repository.CostCenterRepository;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio central del motor de asientos contables.
 * Gestiona la creacion, contabilizacion, reversion y eliminacion de asientos.
 * Valida partida doble, periodos abiertos, cuentas activas y centros de costo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountingAccountRepository accountingAccountRepository;
    private final CostCenterRepository costCenterRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final AuditPublisher auditPublisher;
    private final com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository parameterRepository;
    private final com.sigcon.backend.general.accounting.journal.attachments.domain.repository.JournalEntrySupportRepository supportRepository;
    private final com.sigcon.backend.general.accounting.series.domain.service.VoucherSeriesService voucherSeriesService;
    /**
     * QA Bloque PA Bug 49 (HU-PA-20 E5, 2026-05-09): NotificationService inyectado
     * por setter (evitar ciclo) para emitir USER_VOUCHER_REJECTED al creador
     * cuando un asiento es reversado/rechazado.
     */
    private com.sigcon.backend.parametrization.notifications.domain.service.NotificationService notificationService;
    private com.sigcon.backend.parametrization.users.domain.repository.UserRepository jeUserRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setNotificationService(
            com.sigcon.backend.parametrization.notifications.domain.service.NotificationService ns) {
        this.notificationService = ns;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setJeUserRepository(
            com.sigcon.backend.parametrization.users.domain.repository.UserRepository repo) {
        this.jeUserRepository = repo;
    }

    private final DataTableSpecificationBuilder<JournalEntry> specBuilder = new DataTableSpecificationBuilder<>();

    // ───────────────────────────────────────────────────────────────
    // Creacion de asiento contable
    // ───────────────────────────────────────────────────────────────

    /**
     * Crea un asiento contable en estado BORRADOR.
     * Valida: periodo abierto, cuentas activas, partida doble (debitos = creditos).
     *
     * @param request   datos del asiento y sus lineas
     * @param createdBy usuario que crea el asiento
     * @return DTO del asiento creado
     */
    @Transactional
    public JournalEntryDTO createEntry(CreateJournalEntryRequest request, String createdBy) {
        // 1. Validar periodo abierto
        LocalDate entryDate = request.getEntryDate();
        accountingPeriodService.validatePeriodOpen(entryDate);

        // HU-CG-01A E7: la fecha del comprobante NO puede ser futura.
        // Se compara contra la fecha actual del servidor (hora local).
        if (entryDate != null && entryDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La fecha ingresada no es válida. No se permiten comprobantes con fecha futura.");
        }

        // 2. Validar que tenga lineas
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("El asiento debe tener al menos una linea.");
        }

        // 3. Calcular totales y validar partida doble
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (var line : request.getLines()) {
            totalDebit = totalDebit.add(
                    line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO);
            totalCredit = totalCredit.add(
                    line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO);
        }
        // HU-CG-02B: en BORRADOR se PERMITE descuadre. La validacion estricta de
        // partida doble se aplica al contabilizar (postEntry). Esto permite al
        // contador construir el asiento por etapas sin perder el progreso.
        // Solo bloqueamos descuadre si el flag CG_STRICT_DRAFT esta en true.
        if (totalDebit.compareTo(totalCredit) != 0 && readBoolParam("CG_STRICT_DRAFT", false)) {
            throw new IllegalArgumentException(
                    "Partida doble desbalanceada: debitos $" + totalDebit + " != creditos $" + totalCredit);
        }

        // 4. Obtener siguiente numero de asiento desde VoucherSeriesService.
        // HU-CG-03A E3/E5: el rango y prefijo viven en voucher_series_config.
        // Si la empresa no tiene serie 'JE' configurada, se auto-provisiona en
        // el primer consumo (rango 1..999999). El fail-fast aqui es importante:
        // si la serie esta EXHAUSTED, el endpoint retorna 400 con mensaje claro.
        int fiscalYear = entryDate.getYear();
        long nextNumber;
        try {
            nextNumber = voucherSeriesService.consumeNext("JE");
        } catch (IllegalStateException seriesEx) {
            // Re-lanza con mensaje original; controller lo convierte a 400.
            throw new IllegalStateException(seriesEx.getMessage(), seriesEx);
        }

        // 5. Construir cabecera del asiento
        JournalEntry entry = JournalEntry.builder()
                .entryNumber(nextNumber)
                .fiscalYear(fiscalYear)
                .entryDate(entryDate)
                .periodYear(entryDate.getYear())
                .periodMonth(entryDate.getMonthValue())
                .description(request.getDescription())
                .sourceModule(request.getSourceModule())
                .sourceId(request.getSourceId())
                .status(JournalEntryStatus.DRAFT)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .createdBy(createdBy)
                .build();

        // 6. Construir lineas con validacion de cuentas y centros de costo
        List<JournalEntryLine> lines = new ArrayList<>();
        int order = 1;
        for (var lineReq : request.getLines()) {
            AccountingAccount account = accountingAccountRepository.findById(lineReq.getAccountingAccountId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cuenta contable no encontrada: " + lineReq.getAccountingAccountId()));

            // QA CG (2026-05-25) ERR adicional #1: se permitia crear un comprobante
            // MANUAL con una cuenta inactiva (antes solo bloqueaba con CG_STRICT_DRAFT).
            // La HU-CG-01A E3 exige rechazarlo. Bloqueamos SIEMPRE en asientos manuales
            // del CG (sourceModule null o CG). Los asientos automaticos de AP/AR/BNK/
            // ACT/NOM resuelven sus cuentas via AccountMappingService (ya validadas),
            // por eso no se les aplica esta restriccion para no romper esos flujos.
            boolean isManualCgEntry = request.getSourceModule() == null
                    || request.getSourceModule() == JournalSourceModule.CG;
            if (!"ACTIVE".equals(account.getStatus().name()) && isManualCgEntry) {
                throw new IllegalArgumentException(
                        "La cuenta proporcionada está inactiva. "
                        + "Cuenta " + account.getPucAccount().getCode()
                        + " (" + account.getPucAccount().getName() + ").");
            }

            // HU-CG-09C: cada linea debe tener SOLO debito O credito (no ambos > 0)
            // Esta es la validacion de coherencia debito/credito vs naturaleza de cuenta:
            // si una linea trae ambos campos > 0, es ambigua e invalida.
            BigDecimal d = lineReq.getDebitAmount() != null ? lineReq.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal c = lineReq.getCreditAmount() != null ? lineReq.getCreditAmount() : BigDecimal.ZERO;
            if (d.signum() > 0 && c.signum() > 0) {
                throw new IllegalArgumentException(
                        "Linea con cuenta " + account.getPucAccount().getCode()
                        + " tiene debito y credito simultaneos. "
                        + "Use lineas separadas para mantener la coherencia debito/credito vs naturaleza.");
            }
            if (d.signum() == 0 && c.signum() == 0) {
                throw new IllegalArgumentException(
                        "Linea con cuenta " + account.getPucAccount().getCode()
                        + " no tiene debito ni credito. Especifique al menos un valor.");
            }

            // HU-CG-01A E4: naturaleza de la cuenta vs tipo de movimiento.
            // - Cuenta DEBIT (Activos/Gastos/Costos) NO debe acreditarse en un
            //   asiento manual normal (clase 5/6/7 acreditada es un cargo invalido).
            // - Cuenta CREDIT (Pasivos/Patrimonio/Ingresos) NO debe debitarse.
            // Las clases 1 y 2 admiten ambos movimientos por dinamica de cuenta
            // (un activo puede acreditarse al venderlo); por eso solo restringimos
            // las clases 4, 5, 6 y 7 cuyo movimiento contrario es excepcional y
            // tipicamente indica error de captura.
            //
            // HU-AR-07 DEF#2 (2026-04-27): la validacion solo aplica a asientos
            // MANUALES del CG. Notas credito/debito, reversiones de pago y otros
            // movimientos automatizados de modulos AR/AP/BNK/ACT/NOM SI debitan
            // legitimamente cuentas de ingreso (es un anti-ingreso). Si el JE
            // viene con sourceModule != null, el modulo origen ya validó la
            // logica contable y este chequeo se omite.
            boolean isManualEntry = request.getSourceModule() == null
                    || request.getSourceModule() == JournalSourceModule.CG;
            String pucCode = account.getPucAccount().getCode();
            char clazz = pucCode != null && !pucCode.isEmpty() ? pucCode.charAt(0) : ' ';
            boolean isExpenseLike = clazz == '5' || clazz == '6' || clazz == '7';
            boolean isIncomeLike  = clazz == '4';
            if (isManualEntry && isExpenseLike && c.signum() > 0
                    && account.getNature() == AccountNature.DEBIT) {
                throw new IllegalArgumentException(
                        "La naturaleza de la cuenta " + pucCode + " (" + account.getPucAccount().getName()
                        + ") no corresponde al tipo de movimiento contable. "
                        + "Las cuentas de gastos/costos (clases 5, 6, 7) deben debitarse, no acreditarse.");
            }
            if (isManualEntry && isIncomeLike && d.signum() > 0
                    && account.getNature() == AccountNature.CREDIT) {
                throw new IllegalArgumentException(
                        "La naturaleza de la cuenta " + pucCode + " (" + account.getPucAccount().getName()
                        + ") no corresponde al tipo de movimiento contable. "
                        + "Las cuentas de ingresos (clase 4) deben acreditarse, no debitarse.");
            }

            // HU-CG-01A E6: si la linea trae thirdPartyNit, debe existir un tercero
            // ACTIVO con ese NIT. No aceptamos texto libre.
            String nit = lineReq.getThirdPartyNit();
            if (nit != null && !nit.trim().isEmpty()) {
                if (!thirdPartyRepository.existsByNitAndDeletedAtIsNull(nit.trim())) {
                    throw new IllegalArgumentException(
                            "El NIT ingresado (" + nit + ") no es válido en la base de terceros.");
                }
            }

            JournalEntryLine line = JournalEntryLine.builder()
                    .journalEntry(entry)
                    .lineOrder(order++)
                    .accountingAccount(account)
                    .debitAmount(lineReq.getDebitAmount() != null ? lineReq.getDebitAmount() : BigDecimal.ZERO)
                    .creditAmount(lineReq.getCreditAmount() != null ? lineReq.getCreditAmount() : BigDecimal.ZERO)
                    .description(lineReq.getDescription())
                    .thirdPartyNit(lineReq.getThirdPartyNit())
                    .build();

            if (lineReq.getCostCenterId() != null) {
                CostCenter cc = costCenterRepository.findById(lineReq.getCostCenterId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Centro de costo no encontrado: " + lineReq.getCostCenterId()));
                line.setCostCenter(cc);
            }

            lines.add(line);
        }

        entry.setLines(lines);

        // HU-CG-01A E9: detectar duplicidad antes del commit.
        // Un asiento se considera duplicado si existe otro POSTED con la misma
        // combinacion de (fecha + totalDebit + totalCredit + descripcion) — los
        // datos identitarios capturados a nivel de cabecera. El analisis line-by-line
        // (cuenta+tercero+valor) seria mas preciso pero tambien mas costoso; este
        // chequeo a nivel cabecera atrapa el caso recurrente de doble-clic en Guardar
        // y es suficiente para HU-CG-01A E9.
        List<JournalEntry> sameDay = journalEntryRepository.findByEntryDateAndStatus(
                entryDate, JournalEntryStatus.POSTED);
        for (JournalEntry existing : sameDay) {
            if (existing.getTotalDebit().compareTo(totalDebit) == 0
                    && existing.getTotalCredit().compareTo(totalCredit) == 0
                    && safeEquals(existing.getDescription(), request.getDescription())) {
                throw new IllegalArgumentException(
                        "Ya existe un comprobante con estos datos (mismo fecha, monto y descripcion). "
                        + "Comprobante existente: #" + existing.getEntryNumber() + ".");
            }
        }

        JournalEntry saved = journalEntryRepository.save(entry);
        auditPublisher.publishCreate(AuditModule.CG, "JournalEntry", entry.getId(), "JournalEntry creado id=" + entry.getId());
        return toDTO(saved);
    }

    /** Comparacion null-safe de dos strings. */
    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    // ───────────────────────────────────────────────────────────────
    // Contabilizacion: DRAFT -> POSTED
    // ───────────────────────────────────────────────────────────────

    /**
     * Contabiliza un asiento en estado BORRADOR, cambiando su estado a POSTED.
     *
     * @param id identificador del asiento
     * @return DTO del asiento contabilizado
     */
    @Transactional
    public JournalEntryDTO postEntry(Long id) {
        JournalEntry entry = findByIdOrThrow(id);
        if (entry.getStatus() != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden contabilizar asientos en estado BORRADOR.");
        }

        // HU-CG-02B: validaciones obligatorias al contabilizar (no en createEntry).
        // El BORRADOR puede tener inconsistencias mientras el contador lo construye;
        // al contabilizar el sistema bloquea cualquier irregularidad.

        // 1. Periodo abierto (siempre)
        try {
            accountingPeriodService.validatePeriodOpen(entry.getEntryDate());
        } catch (IllegalStateException psEx) {
            // HU-CG-02A E3 (QA 2026-05-18): mensaje contextual para que el
            // usuario sepa que la operacion fallida fue CONTABILIZACION (no
            // creacion o edicion). El periodo subyacente sigue siendo el de
            // la fecha del asiento. Mensaje original conservado para forensia.
            throw new IllegalStateException(
                    "No se puede contabilizar el comprobante. " + psEx.getMessage(), psEx);
        }

        // HU-CG-02A E7 (QA 2026-05-18): validar duplicidad al contabilizar.
        // Antes solo se validaba al crear, asi que dos DRAFTs identicos
        // creados antes de contabilizar el primero podian quedar en BD y luego
        // contabilizarse ambos. Ahora cada postEntry comprueba si ya existe
        // OTRO POSTED en la misma fecha con totales y descripcion equivalentes.
        java.util.List<JournalEntry> sameDayPosted = journalEntryRepository
                .findByEntryDateAndStatus(entry.getEntryDate(), JournalEntryStatus.POSTED);
        for (JournalEntry existing : sameDayPosted) {
            if (existing.getId().equals(entry.getId())) continue; // self-skip
            if (existing.getTotalDebit() != null && existing.getTotalCredit() != null
                    && existing.getTotalDebit().compareTo(entry.getTotalDebit()) == 0
                    && existing.getTotalCredit().compareTo(entry.getTotalCredit()) == 0
                    && safeEquals(existing.getDescription(), entry.getDescription())) {
                throw new IllegalArgumentException(
                        "No se puede contabilizar: ya existe un comprobante CONTABILIZADO "
                        + "con los mismos datos identitarios (fecha " + entry.getEntryDate()
                        + ", totales y descripcion). Comprobante existente: #"
                        + existing.getEntryNumber() + ".");
            }
        }

        // 2. Partida doble cuadrada (siempre)
        if (entry.getTotalDebit() == null || entry.getTotalCredit() == null
                || entry.getTotalDebit().compareTo(entry.getTotalCredit()) != 0) {
            throw new IllegalArgumentException(
                    "El asiento no se puede contabilizar: la partida doble esta desbalanceada. "
                    + "Debitos $" + entry.getTotalDebit() + " != creditos $" + entry.getTotalCredit() + ".");
        }

        // 3. Todas las cuentas siguen activas (HU-CG-02B E3)
        if (entry.getLines() != null) {
            for (JournalEntryLine line : entry.getLines()) {
                AccountingAccount acc = line.getAccountingAccount();
                if (acc == null) {
                    throw new IllegalArgumentException(
                            "Una de las lineas del asiento no tiene cuenta contable asignada.");
                }
                if (acc.getStatus() == null || !"ACTIVE".equals(acc.getStatus().name())) {
                    // HU-CG-01A E3: mensaje exacto del Excel (preservado tras relajar createEntry).
                    throw new IllegalArgumentException(
                            "La cuenta proporcionada está inactiva. "
                            + "Cuenta " + (acc.getPucAccount() != null ? acc.getPucAccount().getCode() : acc.getId())
                            + (acc.getPucAccount() != null ? " (" + acc.getPucAccount().getName() + ")" : "") + ".");
                }
            }
        }

        // 4. HU-CG-02A E2 / HU-CG-05B: NIT obligatorio en todas las lineas si el
        // parametro CG_NIT_REQUIRED_ON_POST esta en true (default false para no
        // romper flujos AP/AR/BNK que crean POSTED directamente). Configurable
        // por empresa desde modulo de parametros.
        if (readBoolParam("CG_NIT_REQUIRED_ON_POST", false)) {
            if (entry.getLines() != null) {
                for (JournalEntryLine line : entry.getLines()) {
                    String nit = line.getThirdPartyNit();
                    if (nit == null || nit.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "El comprobante no se puede contabilizar: la linea con cuenta "
                                + (line.getAccountingAccount() != null && line.getAccountingAccount().getPucAccount() != null
                                        ? line.getAccountingAccount().getPucAccount().getCode() : "?")
                                + " no tiene NIT del tercero. La empresa requiere NIT obligatorio "
                                + "en todas las lineas para contabilizar.");
                    }
                }
            }
        }

        // 5. HU-CG-05B: soporte documental obligatorio si el parametro
        // CG_SUPPORT_REQUIRED_ON_POST esta en true. Default false: el contador
        // puede contabilizar sin soporte pero la HU recomienda activarlo para
        // cumplir con los principios de auditoria contable.
        if (readBoolParam("CG_SUPPORT_REQUIRED_ON_POST", false)) {
            long supports = supportRepository.countByJournalEntryIdAndDeletedAtIsNull(entry.getId());
            if (supports == 0) {
                throw new IllegalArgumentException(
                        "El comprobante no se puede contabilizar: no tiene soportes documentales adjuntos. "
                        + "Adjunte al menos un PDF/JPG/PNG (factura, recibo, contrato) antes de contabilizar.");
            }
        }

        entry.setStatus(JournalEntryStatus.POSTED);
        JournalEntry saved = journalEntryRepository.save(entry);
        auditPublisher.publishUpdate(AuditModule.CG, "JournalEntry", saved.getId(),
                "Asiento contable contabilizado #" + saved.getEntryNumber());
        return toDTO(saved);
    }

    /**
     * HU-CG-02A E2 / HU-CG-05B: lee un parametro booleano de la tabla parameters
     * SIN tenant filter (config global de plataforma). Si el parametro no existe
     * o no es parseable, devuelve el default. Acepta "true", "TRUE", "1", "YES".
     */
    private boolean readBoolParam(String name, boolean def) {
        try {
            return parameterRepository.findGlobalValueByName(name)
                    .map(s -> s != null && (s.equalsIgnoreCase("true")
                                          || s.equalsIgnoreCase("yes")
                                          || s.trim().equals("1")))
                    .orElse(def);
        } catch (Exception ex) {
            log.warn("No se pudo leer parametro {}: {}", name, ex.getMessage());
            return def;
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Reversion de asiento contabilizado
    // ───────────────────────────────────────────────────────────────

    /**
     * Reversa un asiento contabilizado creando un asiento espejo con debitos y creditos invertidos.
     * El asiento original queda en estado REVERSED.
     *
     * @param id          identificador del asiento original
     * @param description descripcion de la reversion
     * @param createdBy   usuario que realiza la reversion
     * @return DTO del nuevo asiento de reversion
     */
    @Transactional
    public JournalEntryDTO reverseEntry(Long id, String description, String createdBy) {
        return reverseEntry(id, description, createdBy, false);
    }

    /**
     * HU-CG-07B E1 (QA 2026-05-18): variante que opcionalmente crea ademas un
     * DRAFT correctivo clonado del original. Util cuando el contador quiere
     * corregir un asiento sin tener que re-capturar lineas a mano. El DRAFT
     * queda vinculado al original via {@code correctionOf} y debe ser
     * editado/contabilizado por el usuario.
     */
    @Transactional
    public JournalEntryDTO reverseEntry(Long id, String description, String createdBy,
                                          boolean createCorrectionDraft) {
        JournalEntry original = findByIdOrThrow(id);
        if (original.getStatus() != JournalEntryStatus.POSTED) {
            throw new IllegalStateException("Solo se pueden reversar asientos CONTABILIZADOS.");
        }

        // HU-CG-08B: motivo de anulacion obligatorio
        if (description == null || description.trim().length() < 10) {
            throw new IllegalArgumentException(
                    "El motivo de anulación es obligatorio (minimo 10 caracteres). "
                    + "Indique la razon de la reversion para auditoria.");
        }

        // Validar que el periodo siga abierto
        accountingPeriodService.validatePeriodOpen(original.getEntryDate());

        // QA-BLOQUE-AL (2026-04-29): consumir siguiente numero por la serie
        // unificada `voucherSeriesService.consumeNext("JE")`. Antes este flujo
        // usaba `findMaxEntryNumberByFiscalYear` (forma legacy) mientras
        // `createEntry` ya usaba consumeNext, lo que producia colisiones de
        // entry_number cuando un mismo flujo (ej. Pull+Diff MODIFIED: cancel +
        // recreate) corria los dos paths consecutivamente. Ahora ambos paths
        // comparten la misma fuente de verdad y respetan el lock JVM por
        // tipo de comprobante.
        long nextNumber = voucherSeriesService.consumeNext("JE");

        // Crear asiento de reversion con lineas espejo (debitos y creditos invertidos)
        JournalEntry reversal = JournalEntry.builder()
                .entryNumber(nextNumber)
                .fiscalYear(original.getFiscalYear())
                .entryDate(LocalDate.now())
                .periodYear(LocalDate.now().getYear())
                .periodMonth(LocalDate.now().getMonthValue())
                // QA reeval Q4 (2026-05-27): el asiento de reversion es un STORNO
                // (lineas espejo con debito/credito invertidos) que neutraliza el
                // original; NO es un duplicado. Antes la descripcion mostraba solo
                // el motivo crudo y, como los totales de un storno son identicos a
                // los del original (debito total = credito total), un revisor podia
                // leerlo como "comprobante contabilizado duplicado". Prefijamos la
                // descripcion en el origen para que sea autoexplicativa en listado,
                // exportaciones (Excel/CSV/PDF) y auditoria.
                .description("Reversión de " + buildVoucherCode(original)
                        + " (asiento inverso): "
                        + (description != null ? description : "asiento #" + original.getEntryNumber()))
                .sourceModule(original.getSourceModule())
                .sourceId(original.getSourceId())
                .status(JournalEntryStatus.POSTED)
                .reversalOf(original)
                .totalDebit(original.getTotalCredit())
                .totalCredit(original.getTotalDebit())
                .createdBy(createdBy)
                .build();

        List<JournalEntryLine> reversalLines = new ArrayList<>();
        int order = 1;
        for (JournalEntryLine origLine : original.getLines()) {
            reversalLines.add(JournalEntryLine.builder()
                    .journalEntry(reversal)
                    .lineOrder(order++)
                    .accountingAccount(origLine.getAccountingAccount())
                    .debitAmount(origLine.getCreditAmount())
                    .creditAmount(origLine.getDebitAmount())
                    .description("REV: " + (origLine.getDescription() != null ? origLine.getDescription() : ""))
                    .thirdPartyNit(origLine.getThirdPartyNit())
                    .costCenter(origLine.getCostCenter())
                    .build());
        }
        reversal.setLines(reversalLines);

        // Marcar asiento original como REVERSED
        original.setStatus(JournalEntryStatus.REVERSED);
        journalEntryRepository.save(original);

        JournalEntry savedReversal = journalEntryRepository.save(reversal);
        auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.DELETE,
                AuditModule.CG,
                com.sigcon.backend.audit.domain.model.enums.AuditSeverity.HIGH,
                "JournalEntry", original.getId(),
                "Asiento contable reversado #" + original.getEntryNumber()
                        + " -> nuevo asiento #" + savedReversal.getEntryNumber()
                        + " motivo: " + description,
                null, null, savedReversal.getId());

        // QA Bloque PA Bug 49 (HU-PA-20 E5): notif al creador del asiento original
        // cuando es rechazado/reversado por otro usuario.
        try {
            if (notificationService != null && jeUserRepository != null
                    && original.getCreatedBy() != null
                    && !original.getCreatedBy().equalsIgnoreCase(createdBy)) {
                jeUserRepository.findByUsernameOrEmail(original.getCreatedBy(), original.getCreatedBy())
                    .ifPresent(creator ->
                        notificationService.publishToUser(creator.getId(),
                            com.sigcon.backend.parametrization.notifications.application.PublishEventRequest.builder()
                                .companyId(creator.getCompanyId())
                                .eventKey("USER_VOUCHER_REJECTED")
                                .title("Un comprobante que creo fue rechazado")
                                .body("El comprobante #" + original.getEntryNumber()
                                        + " fue reversado/rechazado por " + createdBy
                                        + ". Motivo: " + description)
                                .actionUrl("/contabilidad/comprobantes/" + original.getId())
                                .sourceId(original.getId())
                                .sourceType("JournalEntry")
                                .severity(com.sigcon.backend.parametrization.notifications.domain.model.Notification.Severity.WARNING)
                                .build()));
            }
        } catch (RuntimeException ignored) { /* notif no rompe el reverse */ }

        // HU-CG-07B E1: si el contador pidio createCorrectionDraft=true,
        // generamos un nuevo asiento en BORRADOR clonado del original con
        // correctionOf apuntando al original. El contador podra editarlo y
        // contabilizarlo con el flujo normal. Si la creacion del DRAFT falla,
        // el REV ya se persistio y la operacion principal NO se rollbackea.
        if (createCorrectionDraft) {
            try {
                long correctionNumber = voucherSeriesService.consumeNext("JE");
                JournalEntry correctionDraft = JournalEntry.builder()
                        .entryNumber(correctionNumber)
                        .fiscalYear(LocalDate.now().getYear())
                        .entryDate(LocalDate.now())
                        .periodYear(LocalDate.now().getYear())
                        .periodMonth(LocalDate.now().getMonthValue())
                        .description("Borrador correctivo de asiento #" + original.getEntryNumber()
                                + " (reversado el " + LocalDate.now() + ")")
                        .sourceModule(JournalSourceModule.CG)
                        .sourceId(original.getId())
                        .status(JournalEntryStatus.DRAFT)
                        .correctionOf(original)
                        .totalDebit(original.getTotalDebit())
                        .totalCredit(original.getTotalCredit())
                        .createdBy(createdBy)
                        .build();

                List<JournalEntryLine> draftLines = new ArrayList<>();
                int orderDraft = 1;
                for (JournalEntryLine origLine : original.getLines()) {
                    draftLines.add(JournalEntryLine.builder()
                            .journalEntry(correctionDraft)
                            .lineOrder(orderDraft++)
                            .accountingAccount(origLine.getAccountingAccount())
                            .debitAmount(origLine.getDebitAmount())
                            .creditAmount(origLine.getCreditAmount())
                            .description(origLine.getDescription())
                            .thirdPartyNit(origLine.getThirdPartyNit())
                            .costCenter(origLine.getCostCenter())
                            .build());
                }
                correctionDraft.setLines(draftLines);
                journalEntryRepository.save(correctionDraft);
                auditPublisher.publishCreate(AuditModule.CG, "JournalEntry",
                        correctionDraft.getId(),
                        "Borrador correctivo generado automaticamente tras reverse de #"
                                + original.getEntryNumber());
                log.info("HU-CG-07B E1: DRAFT correctivo {} creado tras reverse de {}",
                        correctionDraft.getId(), original.getId());
            } catch (RuntimeException corrEx) {
                log.error("HU-CG-07B E1: no se pudo crear DRAFT correctivo tras reverse {}: {}",
                        original.getId(), corrEx.getMessage());
                // No rompemos el reverse; el REV ya se persistio.
            }
        }

        return toDTO(savedReversal);
    }

    // ───────────────────────────────────────────────────────────────
    // Consultas
    // ───────────────────────────────────────────────────────────────

    /**
     * Obtiene un asiento contable por su identificador.
     *
     * @param id identificador del asiento
     * @return DTO del asiento con sus lineas
     */
    public JournalEntryDTO getEntry(Long id) {
        return toDTO(findByIdOrThrow(id));
    }

    /**
     * Obtiene los asientos de un periodo especifico (anio-mes).
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return lista de DTOs de asientos del periodo
     */
    public List<JournalEntryDTO> getEntriesByPeriod(int year, int month) {
        return journalEntryRepository.findAll().stream()
                .filter(e -> e.getPeriodYear() == year && e.getPeriodMonth() == month)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ───────────────────────────────────────────────────────────────
    // Eliminacion logica (solo DRAFT)
    // ───────────────────────────────────────────────────────────────

    /**
     * Elimina logicamente un asiento contable.
     * Solo se permite eliminar asientos en estado BORRADOR.
     *
     * @param id identificador del asiento
     */
    @Transactional
    public void deleteEntry(Long id) {
        JournalEntry entry = findByIdOrThrow(id);
        if (entry.getStatus() != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException("Solo se pueden eliminar asientos en estado BORRADOR.");
        }
        Long entryNumber = entry.getEntryNumber();
        journalEntryRepository.delete(entry);
        auditPublisher.publishDelete(AuditModule.CG, "JournalEntry", id,
                "Asiento contable eliminado #" + entryNumber);
    }

    /**
     * CG-07A: Modifica un asiento contable en estado BORRADOR.
     *
     * <p>Reglas de negocio:
     * <ul>
     *   <li>Solo se pueden modificar asientos en estado DRAFT (contabilizados son inmutables).</li>
     *   <li>El periodo contable de la nueva fecha debe estar abierto.</li>
     *   <li>Se re-valida partida doble (debitos = creditos).</li>
     *   <li>Se re-valida cuentas contables existentes y activas.</li>
     * </ul>
     *
     * @param id       identificador del asiento a modificar
     * @param request  nuevos datos del asiento (fecha, descripcion, lineas)
     * @return DTO del asiento actualizado
     * @throws IllegalStateException si el asiento no esta en estado DRAFT
     */
    @Transactional
    public JournalEntryDTO updateEntry(Long id, CreateJournalEntryRequest request) {
        JournalEntry entry = findByIdOrThrow(id);

        if (entry.getStatus() != JournalEntryStatus.DRAFT) {
            throw new IllegalStateException(
                    "Solo se pueden modificar asientos en estado BORRADOR. "
                    + "Para asientos contabilizados use el endpoint de correccion (/correct).");
        }

        // HU-CG-02A E3 (QA 2026-05-18): validar periodo abierto para la nueva
        // fecha con mensaje contextual de EDICION. Asi el usuario distingue
        // entre fallar al crear, editar o contabilizar.
        try {
            accountingPeriodService.validatePeriodOpen(request.getEntryDate());
        } catch (IllegalStateException psEx) {
            throw new IllegalStateException(
                    "No se puede modificar el comprobante con esa fecha. " + psEx.getMessage(), psEx);
        }

        // Validar y construir lineas nuevas
        List<JournalEntryLine> newLines = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("El asiento debe tener al menos una linea.");
        }

        int lineOrder = 1;
        for (var lineReq : request.getLines()) {
            AccountingAccount account = accountingAccountRepository.findById(lineReq.getAccountingAccountId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Cuenta contable no encontrada: " + lineReq.getAccountingAccountId()));

            CostCenter costCenter = null;
            if (lineReq.getCostCenterId() != null) {
                costCenter = costCenterRepository.findById(lineReq.getCostCenterId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Centro de costo no encontrado: " + lineReq.getCostCenterId()));
            }

            BigDecimal debit = lineReq.getDebitAmount() != null ? lineReq.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal credit = lineReq.getCreditAmount() != null ? lineReq.getCreditAmount() : BigDecimal.ZERO;
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);

            JournalEntryLine line = JournalEntryLine.builder()
                    .journalEntry(entry)
                    .accountingAccount(account)
                    .costCenter(costCenter)
                    .lineOrder(lineOrder++)
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .description(lineReq.getDescription())
                    .thirdPartyNit(lineReq.getThirdPartyNit())
                    .build();
            newLines.add(line);
        }

        // Validar partida doble
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException(
                    "Partida doble desbalanceada: debitos (" + totalDebit
                    + ") != creditos (" + totalCredit + ")");
        }

        // HU-CG-02A E7 (QA 2026-05-18): tambien validar duplicidad al editar
        // BORRADOR. Si el usuario cambia la descripcion (o totales o fecha)
        // y queda igual a un POSTED existente, debemos bloquear. Asi se
        // cierra el escape via edicion posterior.
        java.util.List<JournalEntry> samePostedAfterEdit = journalEntryRepository
                .findByEntryDateAndStatus(request.getEntryDate(), JournalEntryStatus.POSTED);
        for (JournalEntry existing : samePostedAfterEdit) {
            if (existing.getId().equals(entry.getId())) continue;
            if (existing.getTotalDebit() != null && existing.getTotalCredit() != null
                    && existing.getTotalDebit().compareTo(totalDebit) == 0
                    && existing.getTotalCredit().compareTo(totalCredit) == 0
                    && safeEquals(existing.getDescription(), request.getDescription())) {
                throw new IllegalArgumentException(
                        "No se puede actualizar el comprobante: los nuevos datos "
                        + "coinciden con un comprobante CONTABILIZADO existente "
                        + "(#" + existing.getEntryNumber() + "). Para evitar duplicidad "
                        + "modifique la fecha, los totales o la descripcion.");
            }
        }

        // Reemplazar lineas (orphanRemoval elimina las viejas)
        entry.getLines().clear();
        entry.getLines().addAll(newLines);
        entry.setEntryDate(request.getEntryDate());
        entry.setDescription(request.getDescription());
        entry.setPeriodYear(request.getEntryDate().getYear());
        entry.setPeriodMonth(request.getEntryDate().getMonthValue());
        entry.setFiscalYear(request.getEntryDate().getYear());
        entry.setTotalDebit(totalDebit);
        entry.setTotalCredit(totalCredit);
        // QA CG (2026-05-25) ERR adicional #4: al editar el comprobante, el modulo
        // origen NO se actualizaba (seguia mostrando el valor antiguo). Lo seteamos
        // si viene en el request (solo en BORRADOR; este metodo ya exige DRAFT).
        if (request.getSourceModule() != null) {
            entry.setSourceModule(request.getSourceModule());
        }

        entry = journalEntryRepository.save(entry);
        auditPublisher.publishUpdate(AuditModule.CG, "JournalEntry", entry.getId(), "JournalEntry actualizado id=" + entry.getId());
        log.info("CG-07A: Asiento {} actualizado en estado BORRADOR", id);
        return toDTO(entry);
    }

    /**
     * CG-07B: Crea una nueva version correctiva de un asiento CONTABILIZADO.
     *
     * <p>El asiento original permanece inmutable (principio contable de partida inmutable).
     * Se crea un NUEVO asiento en estado DRAFT con {@code correctionOf} apuntando al
     * original, conservando la trazabilidad de versiones. Debe ser contabilizado
     * posteriormente via {@code postEntry()} tras revision.
     *
     * @param originalId identificador del asiento original CONTABILIZADO
     * @param request    datos de la nueva version
     * @param createdBy  usuario que crea la correccion
     * @return DTO de la nueva version (DRAFT, vinculada al original via correctionOf)
     * @throws IllegalStateException si el asiento original no esta CONTABILIZADO
     */
    @Transactional
    public JournalEntryDTO createCorrection(Long originalId,
                                            CreateJournalEntryRequest request,
                                            String createdBy) {
        JournalEntry original = findByIdOrThrow(originalId);

        if (original.getStatus() != JournalEntryStatus.POSTED) {
            throw new IllegalStateException(
                    "Solo se pueden corregir asientos en estado CONTABILIZADO. "
                    + "Para BORRADOR use el endpoint de actualizacion (PUT /{id}).");
        }

        // Crear nuevo asiento correctivo (reutiliza createEntry para validaciones)
        JournalEntryDTO correctionDto = createEntry(request, createdBy);

        // Vincular la correccion al original
        JournalEntry correction = findByIdOrThrow(correctionDto.getId());
        correction.setCorrectionOf(original);
        correction.setDescription(
                (request.getDescription() != null ? request.getDescription() : "")
                + " [Correccion de asiento " + original.getEntryNumber() + "/" + original.getFiscalYear() + "]");
        correction = journalEntryRepository.save(correction);
        auditPublisher.publishCreate(AuditModule.CG, "JournalEntry", correction.getId(), "JournalEntry creado id=" + correction.getId());

        log.info("CG-07B: Correccion {} creada (original: {})", correction.getId(), originalId);
        return toDTO(correction);
    }

    // ───────────────────────────────────────────────────────────────
    // Busqueda paginada DataTable
    // ───────────────────────────────────────────────────────────────

    /**
     * Busqueda paginada de asientos contables para el componente DataTable del frontend.
     * Soporta filtros globales y por columna, ordenamiento y paginacion.
     *
     * @param request parametros de busqueda DataTable (draw, start, length, search, columns)
     * @return respuesta paginada compatible con DataTable
     */
    public ResponseEntity<?> searchEntries(DataTableRequest request) {
        int page = request.getStart() / request.getLength();
        String orderColumn = request.getOrderColumnName();
        String orderDir = request.getOrderDir();

        Sort sort = Sort.by(Sort.Direction.fromString(orderDir), orderColumn != null ? orderColumn : "id");
        Pageable pageable = PageRequest.of(page, request.getLength(), sort);
        Specification<JournalEntry> spec = specBuilder.build(request);

        Page<JournalEntryDTO> data = journalEntryRepository.findAll(spec, pageable).map(this::toDTO);
        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * HU-CG-02B E3 (QA 2026-05-19): exportacion masiva del listado completo
     * de comprobantes filtrados. Reusa el Specification del DataTable para
     * respetar los filtros activos en pantalla. Devuelve una lista plana
     * con campos identitarios + totales (NO incluye lineas detalle).
     *
     * @param request DataTableRequest con los filtros vigentes
     * @return lista filtrada y ordenada (sin paginar)
     */
    public java.util.List<JournalEntryDTO> findFilteredAsList(DataTableRequest request) {
        Specification<JournalEntry> spec = specBuilder.build(request);
        String orderColumn = request.getOrderColumnName();
        String orderDir = request.getOrderDir();
        Sort sort = Sort.by(Sort.Direction.fromString(orderDir),
                orderColumn != null ? orderColumn : "id");
        return journalEntryRepository.findAll(spec, sort).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ───────────────────────────────────────────────────────────────
    // Utilidades para otros modulos
    // ───────────────────────────────────────────────────────────────

    /**
     * Cuenta la cantidad de asientos en estado BORRADOR para un periodo dado.
     * Utilizado por AccountingPeriodService para validar si se puede cerrar un periodo.
     *
     * @param year  anio del periodo
     * @param month mes del periodo
     * @return cantidad de asientos en borrador
     */
    public long countDraftsByPeriod(int year, int month) {
        return journalEntryRepository.countByPeriodYearAndPeriodMonthAndStatusAndDeletedAtIsNull(
                year, month, JournalEntryStatus.DRAFT);
    }

    // ───────────────────────────────────────────────────────────────
    // Metodos auxiliares privados
    // ───────────────────────────────────────────────────────────────

    /**
     * Busca un asiento por ID o lanza excepcion si no existe.
     */
    private JournalEntry findByIdOrThrow(Long id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Asiento contable no encontrado: " + id));
    }

    /**
     * HU-CG-08C E2/E3: documentos relacionados con un comprobante.
     * Retorna una lista de mapas con: relation, voucherCode, entryNumber, id,
     * status, description. La UI los renderiza en el panel "Documentos
     * Relacionados" del viewer.
     */
    public List<java.util.Map<String, Object>> getRelatedDocuments(Long id) {
        JournalEntry entry = findByIdOrThrow(id);
        List<java.util.Map<String, Object>> related = new ArrayList<>();

        // Caso A: este comprobante reversa a otro -> mostrar el original
        if (entry.getReversalOf() != null) {
            related.add(toRelatedMap("REVERSA_A", entry.getReversalOf()));
        }
        // Caso B: este comprobante corrige a otro -> mostrar el original
        if (entry.getCorrectionOf() != null) {
            related.add(toRelatedMap("CORRIGE_A", entry.getCorrectionOf()));
        }
        // Caso C: este comprobante fue reversado por otro -> mostrar el REV
        journalEntryRepository.findFirstByReversalOf_IdAndDeletedAtIsNull(id)
                .ifPresent(rev -> related.add(toRelatedMap("REVERSADO_POR", rev)));
        // Caso D: este comprobante fue corregido por otro -> mostrar el COR
        journalEntryRepository.findFirstByCorrectionOf_IdAndDeletedAtIsNull(id)
                .ifPresent(cor -> related.add(toRelatedMap("CORREGIDO_POR", cor)));
        return related;
    }

    /**
     * HU-CG-07C E1/E2/E3: arbol completo de versiones del comprobante.
     *
     * Recorre RECURSIVAMENTE las relaciones reversalOf y correctionOf en ambos
     * sentidos (hacia arriba: ancestros; hacia abajo: descendientes) y construye
     * un grafo plano de todas las versiones vinculadas al comprobante consultado.
     *
     * Cada nodo del resultado incluye:
     *   - id, voucherCode, entryNumber, status, description, entryDate
     *   - relation: "ORIGINAL" (raiz), "REVERSAL" (anula), "CORRECTION" (corrige)
     *   - parentId: id del comprobante padre en el arbol (null para la raiz)
     *   - depth: profundidad desde la raiz (0 = original)
     *
     * El frontend lo renderiza como arbol en una pestaña "Historial de versiones"
     * del modal view.
     */
    public List<java.util.Map<String, Object>> getVersionHistory(Long id) {
        JournalEntry start = findByIdOrThrow(id);
        // 1. Encontrar la raiz: subir por reversalOf/correctionOf hasta no encontrar mas.
        JournalEntry root = findRootAncestor(start);

        // 2. BFS hacia abajo desde la raiz, recolectando todos los descendientes.
        java.util.Set<Long> visited = new java.util.LinkedHashSet<>();
        List<java.util.Map<String, Object>> tree = new ArrayList<>();
        java.util.Deque<Object[]> queue = new java.util.ArrayDeque<>();
        queue.add(new Object[]{root, null, 0, "ORIGINAL"});

        while (!queue.isEmpty()) {
            Object[] node = queue.poll();
            JournalEntry e = (JournalEntry) node[0];
            Long parentId = (Long) node[1];
            int depth = (int) node[2];
            String relation = (String) node[3];
            if (visited.contains(e.getId())) continue;
            visited.add(e.getId());
            tree.add(toVersionNode(e, relation, parentId, depth));
            // Buscar descendientes: comprobantes que reversaron o corrigieron a este
            journalEntryRepository.findFirstByReversalOf_IdAndDeletedAtIsNull(e.getId())
                    .ifPresent(rev -> queue.add(new Object[]{rev, e.getId(), depth + 1, "REVERSAL"}));
            journalEntryRepository.findFirstByCorrectionOf_IdAndDeletedAtIsNull(e.getId())
                    .ifPresent(cor -> queue.add(new Object[]{cor, e.getId(), depth + 1, "CORRECTION"}));
        }
        // HU-CG-07C E4: registrar consulta del historial para forensia
        auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.VIEW,
                AuditModule.CG,
                com.sigcon.backend.audit.domain.model.enums.AuditSeverity.LOW,
                "JournalEntry", id,
                "Consulta historial de versiones del comprobante " + buildVoucherCode(start)
                        + " (" + tree.size() + " versiones)",
                null, null, id);
        return tree;
    }

    /**
     * HU-CG-07C E3 (QA 2026-05-25): comparacion detallada entre DOS versiones de
     * un comprobante. Devuelve los cambios de cabecera (campo, valor anterior,
     * valor nuevo) y el diff de lineas por cuenta PUC (agregadas / eliminadas /
     * modificadas en debito/credito/descripcion/tercero). El frontend lo pinta
     * resaltando lo que cambio.
     *
     * @param idA version origen (normalmente la mas antigua)
     * @param idB version destino (normalmente la mas reciente)
     */
    public java.util.Map<String, Object> compareVersions(Long idA, Long idB) {
        JournalEntry a = findByIdOrThrow(idA);
        JournalEntry b = findByIdOrThrow(idB);

        // ── Diff de cabecera ──
        List<java.util.Map<String, Object>> headerDiffs = new ArrayList<>();
        addHeaderDiff(headerDiffs, "Descripción", a.getDescription(), b.getDescription());
        addHeaderDiff(headerDiffs, "Fecha", str(a.getEntryDate()), str(b.getEntryDate()));
        addHeaderDiff(headerDiffs, "Estado", a.getStatus() != null ? a.getStatus().name() : null,
                b.getStatus() != null ? b.getStatus().name() : null);
        addHeaderDiff(headerDiffs, "Módulo origen", a.getSourceModule() != null ? a.getSourceModule().name() : null,
                b.getSourceModule() != null ? b.getSourceModule().name() : null);
        addHeaderDiff(headerDiffs, "Total débito", str(a.getTotalDebit()), str(b.getTotalDebit()));
        addHeaderDiff(headerDiffs, "Total crédito", str(a.getTotalCredit()), str(b.getTotalCredit()));

        // ── Diff de lineas (agrupadas por cuenta PUC) ──
        java.util.Map<String, JournalEntryLine> linesA = indexLinesByAccount(a);
        java.util.Map<String, JournalEntryLine> linesB = indexLinesByAccount(b);
        java.util.Set<String> allKeys = new java.util.LinkedHashSet<>();
        allKeys.addAll(linesA.keySet());
        allKeys.addAll(linesB.keySet());

        List<java.util.Map<String, Object>> lineDiffs = new ArrayList<>();
        for (String key : allKeys) {
            JournalEntryLine la = linesA.get(key);
            JournalEntryLine lb = linesB.get(key);
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("account", key);
            if (la != null && lb == null) {
                row.put("changeType", "REMOVED");
                row.put("debitA", la.getDebitAmount()); row.put("creditA", la.getCreditAmount());
                row.put("debitB", null); row.put("creditB", null);
            } else if (la == null && lb != null) {
                row.put("changeType", "ADDED");
                row.put("debitA", null); row.put("creditA", null);
                row.put("debitB", lb.getDebitAmount()); row.put("creditB", lb.getCreditAmount());
            } else {
                boolean changed = !eqAmt(la.getDebitAmount(), lb.getDebitAmount())
                        || !eqAmt(la.getCreditAmount(), lb.getCreditAmount())
                        || !safeEquals(la.getDescription(), lb.getDescription())
                        || !safeEquals(la.getThirdPartyNit(), lb.getThirdPartyNit());
                row.put("changeType", changed ? "MODIFIED" : "UNCHANGED");
                row.put("debitA", la.getDebitAmount()); row.put("creditA", la.getCreditAmount());
                row.put("debitB", lb.getDebitAmount()); row.put("creditB", lb.getCreditAmount());
            }
            lineDiffs.add(row);
        }

        // Auditoria de la consulta de comparacion (HU-CG-07C E4)
        auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.VIEW,
                AuditModule.CG, com.sigcon.backend.audit.domain.model.enums.AuditSeverity.LOW,
                "JournalEntry", idB,
                "Comparacion de versiones " + buildVoucherCode(a) + " vs " + buildVoucherCode(b),
                null, null, idB);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("versionA", toVersionNode(a, "A", null, 0));
        result.put("versionB", toVersionNode(b, "B", null, 0));
        result.put("headerDiffs", headerDiffs);
        result.put("lineDiffs", lineDiffs);
        boolean anyChange = headerDiffs.stream().anyMatch(h -> Boolean.TRUE.equals(h.get("changed")))
                || lineDiffs.stream().anyMatch(l -> !"UNCHANGED".equals(l.get("changeType")));
        result.put("hasChanges", anyChange);
        return result;
    }

    private void addHeaderDiff(List<java.util.Map<String, Object>> diffs, String field, String a, String b) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("field", field);
        m.put("valueA", a);
        m.put("valueB", b);
        m.put("changed", !safeEquals(a, b));
        diffs.add(m);
    }

    private java.util.Map<String, JournalEntryLine> indexLinesByAccount(JournalEntry e) {
        java.util.Map<String, JournalEntryLine> map = new java.util.LinkedHashMap<>();
        if (e.getLines() == null) return map;
        for (JournalEntryLine l : e.getLines()) {
            String code = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                    ? l.getAccountingAccount().getPucAccount().getCode() : ("ID-" + (l.getId()));
            String name = l.getAccountingAccount() != null && l.getAccountingAccount().getPucAccount() != null
                    ? l.getAccountingAccount().getPucAccount().getName() : "";
            map.put(code + " - " + name, l);
        }
        return map;
    }

    private boolean eqAmt(BigDecimal x, BigDecimal y) {
        BigDecimal xa = x != null ? x : BigDecimal.ZERO;
        BigDecimal ya = y != null ? y : BigDecimal.ZERO;
        return xa.compareTo(ya) == 0;
    }

    private String str(Object o) { return o != null ? o.toString() : null; }

    /** Sube por reversalOf/correctionOf hasta encontrar la raiz original. */
    private JournalEntry findRootAncestor(JournalEntry e) {
        JournalEntry current = e;
        java.util.Set<Long> guard = new java.util.HashSet<>();
        while (current != null) {
            if (!guard.add(current.getId())) break;  // proteccion contra ciclo
            JournalEntry parent = current.getReversalOf() != null
                    ? current.getReversalOf()
                    : current.getCorrectionOf();
            if (parent == null) return current;
            current = parent;
        }
        return e;
    }

    private java.util.Map<String, Object> toVersionNode(JournalEntry e, String relation,
                                                          Long parentId, int depth) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", e.getId());
        m.put("entryNumber", e.getEntryNumber());
        m.put("voucherCode", buildVoucherCode(e));
        m.put("status", e.getStatus() != null ? e.getStatus().name() : null);
        m.put("description", e.getDescription());
        m.put("entryDate", e.getEntryDate() != null ? e.getEntryDate().toString() : null);
        m.put("totalDebit", e.getTotalDebit());
        m.put("totalCredit", e.getTotalCredit());
        m.put("createdBy", e.getCreatedBy());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        m.put("relation", relation);
        m.put("parentId", parentId);
        m.put("depth", depth);
        return m;
    }

    private static java.util.Map<String, Object> toRelatedMap(String relation, JournalEntry e) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("relation", relation);
        m.put("id", e.getId());
        m.put("entryNumber", e.getEntryNumber());
        m.put("voucherCode", buildVoucherCode(e));
        m.put("status", e.getStatus() != null ? e.getStatus().name() : null);
        m.put("description", e.getDescription());
        m.put("entryDate", e.getEntryDate() != null ? e.getEntryDate().toString() : null);
        return m;
    }

    /**
     * Convierte una entidad JournalEntry a su DTO de lectura, incluyendo lineas.
     */
    private JournalEntryDTO toDTO(JournalEntry entry) {
        List<JournalEntryLineDTO> lineDTOs = entry.getLines() != null
                ? entry.getLines().stream().map(this::toLineDTO).collect(Collectors.toList())
                : List.of();

        // HU-CG-08B E3 / HU-CG-07B: prefijo del codigo del comprobante segun rol contable.
        // El UI usa este voucherCode como identificador legible (ej. REV-2026-3).
        String voucherCode = buildVoucherCode(entry);

        Long reversalOfId = entry.getReversalOf() != null ? entry.getReversalOf().getId() : null;
        Long reversalOfNumber = entry.getReversalOf() != null ? entry.getReversalOf().getEntryNumber() : null;
        String reversalOfVoucherCode = entry.getReversalOf() != null ? buildVoucherCode(entry.getReversalOf()) : null;

        return JournalEntryDTO.builder()
                .id(entry.getId())
                .entryNumber(entry.getEntryNumber())
                .voucherCode(voucherCode)
                .fiscalYear(entry.getFiscalYear())
                .entryDate(entry.getEntryDate())
                .periodYear(entry.getPeriodYear())
                .periodMonth(entry.getPeriodMonth())
                .description(entry.getDescription())
                .sourceModule(entry.getSourceModule() != null ? entry.getSourceModule().name() : null)
                .sourceId(entry.getSourceId())
                .status(entry.getStatus() != null ? entry.getStatus().name() : null)
                .reversalOfId(reversalOfId)
                .reversalOfNumber(reversalOfNumber)
                .reversalOfVoucherCode(reversalOfVoucherCode)
                .correctionOfId(entry.getCorrectionOf() != null ? entry.getCorrectionOf().getId() : null)
                .totalDebit(entry.getTotalDebit())
                .totalCredit(entry.getTotalCredit())
                .lines(lineDTOs)
                .createdBy(entry.getCreatedBy())
                .createdAt(entry.getCreatedAt())
                .auditLogId(entry.getAuditLogId()) // HU-AU-09 E5
                .build();
    }

    /**
     * Calcula el codigo legible del comprobante.
     * Devuelve REV-aaaa-N para reversiones, COR-aaaa-N para correcciones,
     * o JE-aaaa-N para asientos normales. Sin tocar entryNumber (sigue numerando
     * en secuencia continua dentro del anio fiscal).
     */
    public static String buildVoucherCode(JournalEntry entry) {
        String prefix = "JE";
        if (entry.getReversalOf() != null) prefix = "REV";
        else if (entry.getCorrectionOf() != null) prefix = "COR";
        Integer year = entry.getFiscalYear();
        Long num = entry.getEntryNumber();
        return prefix + "-" + (year != null ? year : "????") + "-" + (num != null ? num : "?");
    }

    /**
     * Convierte una entidad JournalEntryLine a su DTO de lectura,
     * incluyendo codigo y nombre de la cuenta contable.
     */
    private JournalEntryLineDTO toLineDTO(JournalEntryLine line) {
        String accountCode = null;
        String accountName = null;
        if (line.getAccountingAccount() != null && line.getAccountingAccount().getPucAccount() != null) {
            accountCode = line.getAccountingAccount().getPucAccount().getCode();
            accountName = line.getAccountingAccount().getPucAccount().getName();
        }

        return JournalEntryLineDTO.builder()
                .id(line.getId())
                .lineOrder(line.getLineOrder())
                .accountingAccountId(line.getAccountingAccount() != null ? line.getAccountingAccount().getId() : null)
                .accountCode(accountCode)
                .accountName(accountName)
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .description(line.getDescription())
                .thirdPartyNit(line.getThirdPartyNit())
                .thirdPartyInactive(isThirdPartyInactive(line.getThirdPartyNit()))
                .costCenterId(line.getCostCenter() != null ? line.getCostCenter().getId() : null)
                .costCenterName(line.getCostCenter() != null ? line.getCostCenter().getName() : null)
                .build();
    }

    /**
     * HU-CG-05C E3: determina si el NIT de una linea corresponde a un tercero
     * que actualmente esta INACTIVO en el catalogo de Terceros (scoped a la
     * empresa via @Filter). Devuelve false si el NIT esta vacio o el tercero no
     * existe (no se muestra aviso). Defensivo: cualquier error => false.
     */
    private Boolean isThirdPartyInactive(String nit) {
        if (nit == null || nit.isBlank()) return false;
        try {
            return thirdPartyRepository.findByNitAndDeletedAtIsNull(nit.trim()).stream()
                    .findFirst()
                    .map(tp -> tp.getStatus() != null
                            && !"ACTIVO".equalsIgnoreCase(tp.getStatus().getName()))
                    .orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
