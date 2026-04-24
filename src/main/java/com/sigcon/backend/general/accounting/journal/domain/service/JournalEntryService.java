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
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException(
                    "Partida doble desbalanceada: debitos $" + totalDebit + " != creditos $" + totalCredit);
        }

        // 4. Obtener siguiente numero de asiento
        int fiscalYear = entryDate.getYear();
        Long maxNumber = journalEntryRepository.findMaxEntryNumberByFiscalYear(fiscalYear);
        long nextNumber = (maxNumber != null ? maxNumber : 0) + 1;

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

            // ERR-MNT-CG-01A / HU-CG-01A E3: mensaje exacto del Excel.
            if (!"ACTIVE".equals(account.getStatus().name())) {
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
            String pucCode = account.getPucAccount().getCode();
            char clazz = pucCode != null && !pucCode.isEmpty() ? pucCode.charAt(0) : ' ';
            boolean isExpenseLike = clazz == '5' || clazz == '6' || clazz == '7';
            boolean isIncomeLike  = clazz == '4';
            if (isExpenseLike && c.signum() > 0 && account.getNature() == AccountNature.DEBIT) {
                throw new IllegalArgumentException(
                        "La naturaleza de la cuenta " + pucCode + " (" + account.getPucAccount().getName()
                        + ") no corresponde al tipo de movimiento contable. "
                        + "Las cuentas de gastos/costos (clases 5, 6, 7) deben debitarse, no acreditarse.");
            }
            if (isIncomeLike && d.signum() > 0 && account.getNature() == AccountNature.CREDIT) {
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
        entry.setStatus(JournalEntryStatus.POSTED);
        JournalEntry saved = journalEntryRepository.save(entry);
        auditPublisher.publishUpdate(AuditModule.CG, "JournalEntry", saved.getId(),
                "Asiento contable contabilizado #" + saved.getEntryNumber());
        return toDTO(saved);
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

        // Obtener siguiente numero de asiento
        Long maxNumber = journalEntryRepository.findMaxEntryNumberByFiscalYear(original.getFiscalYear());
        long nextNumber = (maxNumber != null ? maxNumber : 0) + 1;

        // Crear asiento de reversion con lineas espejo (debitos y creditos invertidos)
        JournalEntry reversal = JournalEntry.builder()
                .entryNumber(nextNumber)
                .fiscalYear(original.getFiscalYear())
                .entryDate(LocalDate.now())
                .periodYear(LocalDate.now().getYear())
                .periodMonth(LocalDate.now().getMonthValue())
                .description(description != null ? description : "Reversion de asiento #" + original.getEntryNumber())
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

        // Validar periodo abierto para la nueva fecha
        accountingPeriodService.validatePeriodOpen(request.getEntryDate());

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
                .costCenterId(line.getCostCenter() != null ? line.getCostCenter().getId() : null)
                .costCenterName(line.getCostCenter() != null ? line.getCostCenter().getName() : null)
                .build();
    }
}
