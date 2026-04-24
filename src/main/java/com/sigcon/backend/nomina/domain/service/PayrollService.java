package com.sigcon.backend.nomina.domain.service;

import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.nomina.application.LiquidatePayrollRequest;
import com.sigcon.backend.nomina.application.PayrollReceiptDTO;
import com.sigcon.backend.nomina.domain.model.Employee;
import com.sigcon.backend.nomina.domain.model.PayrollConcept;
import com.sigcon.backend.nomina.domain.model.PayrollLine;
import com.sigcon.backend.nomina.domain.model.PayrollReceipt;
import com.sigcon.backend.nomina.domain.repository.EmployeeRepository;
import com.sigcon.backend.nomina.domain.repository.PayrollConceptRepository;
import com.sigcon.backend.nomina.domain.repository.PayrollLineRepository;
import com.sigcon.backend.nomina.domain.repository.PayrollReceiptRepository;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * HU-NOM-03 y HU-NOM-04: servicio central de liquidacion de nomina.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Liquidar el periodo para un grupo de empleados (HU-NOM-03): calcula devengados
 *       (salario + extras opcionales) + deducciones (salud 4%, pension 4%, rete fuente)
 *       + aportes patronales (salud 8.5%, pension 12%, SENA 2%, ICBF 3%, caja 4%,
 *       cesantias 8.33%, prima 8.33%, vacaciones 4.17%).</li>
 *   <li>Generar el JournalEntry consolidado del periodo via {@code JournalEntryService}
 *       usando las cuentas del {@code AccountMappingService} (NOMINA_SALARIOS,
 *       NOMINA_CXP_EMPLEADOS, NOMINA_RETENCIONES, NOMINA_CESANTIAS).</li>
 *   <li>Gestionar el flujo de estados DRAFT -> APPROVED -> CLOSED (HU-NOM-04).
 *       Al aprobar, el JE se cambia a POSTED. Al cerrar, todo queda inmutable.</li>
 *   <li>Excluir empleados sin EPS/fondo de pension con mensaje de error y continuar
 *       con los demas (HU-NOM-03 E3).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollReceiptRepository receiptRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollConceptRepository conceptRepository;
    private final EmployeeRepository employeeRepository;
    private final JournalEntryService journalEntryService;
    private final AccountMappingService accountMappingService;
    private final RetentionCalculationService retentionService;
    private final UserUtil userUtil;
    private final AuditPublisher auditPublisher;

    /**
     * HU-NOM-03: liquida la nomina del periodo para un grupo de empleados.
     *
     * @param req parametros de liquidacion (año, mes, tipo, dias, filtro de empleados, extras)
     * @return resumen con los recibos creados + empleados excluidos por datos incompletos
     */
    @Transactional
    public Map<String, Object> liquidatePeriod(LiquidatePayrollRequest req) {
        // 1. Resolver lista de empleados a liquidar
        List<Employee> targets = resolveEmployees(req);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "No se encontraron empleados activos que coincidan con el filtro");
        }

        // 2. Agrupar extras por empleado
        Map<Long, List<LiquidatePayrollRequest.ExtraLine>> extrasByEmployee = new HashMap<>();
        if (req.getExtras() != null) {
            for (var extra : req.getExtras()) {
                extrasByEmployee.computeIfAbsent(extra.getEmployeeId(), k -> new ArrayList<>()).add(extra);
            }
        }

        // 3. Liquidar cada empleado. Los que fallen (HU-NOM-03 E3) se excluyen pero no abortan el proceso.
        List<PayrollReceipt> createdReceipts = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        for (Employee emp : targets) {
            try {
                PayrollReceipt receipt = liquidateEmployee(emp, req,
                        extrasByEmployee.getOrDefault(emp.getId(), List.of()));
                createdReceipts.add(receipt);
            } catch (IllegalStateException ex) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("employeeId", emp.getId());
                entry.put("documentNumber", emp.getDocumentNumber());
                entry.put("fullName", emp.getFullName());
                entry.put("error", ex.getMessage());
                excluded.add(entry);
                log.warn("Empleado {} excluido de liquidacion: {}", emp.getId(), ex.getMessage());
            }
        }

        // 4. Si hay al menos un recibo, generar el JE consolidado del periodo
        Long journalEntryId = null;
        if (!createdReceipts.isEmpty()) {
            journalEntryId = generateConsolidatedJournalEntry(createdReceipts, req);
            for (PayrollReceipt r : createdReceipts) {
                r.setJournalEntryId(journalEntryId);
            }
            receiptRepository.saveAll(createdReceipts);
        }

        if (!createdReceipts.isEmpty()) {
            auditPublisher.publishCreate(AuditModule.NOM, "PayrollReceipt",
                    createdReceipts.get(0).getId(),
                    "Liquidacion periodo " + req.getYear() + "-"
                            + String.format("%02d", req.getMonth())
                            + ": " + createdReceipts.size() + " recibos creados, JE #" + journalEntryId);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("periodYear", req.getYear());
        resp.put("periodMonth", req.getMonth());
        resp.put("totalReceipts", createdReceipts.size());
        resp.put("excluded", excluded);
        resp.put("journalEntryId", journalEntryId);
        resp.put("receipts", createdReceipts.stream().map(r -> PayrollReceiptDTO.from(
                r,
                employeeRepository.findById(r.getEmployeeId()).map(Employee::getFullName).orElse(null),
                employeeRepository.findById(r.getEmployeeId()).map(Employee::getDocumentNumber).orElse(null),
                lineRepository.findByReceiptIdAndDeletedAtIsNullOrderByLineOrder(r.getId())
        )).collect(Collectors.toList()));
        return resp;
    }

    private List<Employee> resolveEmployees(LiquidatePayrollRequest req) {
        if (req.getEmployeeIds() != null && !req.getEmployeeIds().isEmpty()) {
            return employeeRepository.findAllById(req.getEmployeeIds()).stream()
                    .filter(e -> "ACTIVE".equals(e.getStatus()))
                    .collect(Collectors.toList());
        }
        if (req.getCostCenterId() != null) {
            return employeeRepository.findByCostCenterIdAndStatusAndDeletedAtIsNull(
                    req.getCostCenterId(), "ACTIVE");
        }
        return employeeRepository.findByStatusAndDeletedAtIsNull("ACTIVE");
    }

    /**
     * Liquida un empleado individual. Lanza {@link IllegalStateException} si el
     * empleado no tiene EPS o fondo de pension (HU-NOM-03 E3).
     */
    private PayrollReceipt liquidateEmployee(Employee emp, LiquidatePayrollRequest req,
                                               List<LiquidatePayrollRequest.ExtraLine> extras) {
        // HU-NOM-03 E3: empleado sin EPS o fondo de pension -> excluido
        if (emp.getEps() == null || emp.getEps().isBlank()
                || emp.getPensionFund() == null || emp.getPensionFund().isBlank()) {
            throw new IllegalStateException(
                    "Error de liquidación: faltan datos de seguridad social (EPS o fondo de pensión)");
        }

        // HU-NOM-03: evitar duplicados por empleado/periodo
        if (receiptRepository.existsByEmployeeIdAndPeriodYearAndPeriodMonthAndDeletedAtIsNull(
                emp.getId(), req.getYear(), req.getMonth())) {
            throw new IllegalStateException(
                    "Ya existe un recibo para " + req.getYear() + "-" + req.getMonth()
                    + " del empleado " + emp.getFullName());
        }

        BigDecimal salary = emp.getBaseSalary();
        Integer days = req.getDaysWorked() != null ? req.getDaysWorked() : 30;
        // Proporcional a dias trabajados si es < 30
        BigDecimal proportionalSalary = days >= 30
                ? salary
                : salary.multiply(BigDecimal.valueOf(days))
                       .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

        // ===== Construir lineas de recibo =====
        List<PayrollLine> lines = new ArrayList<>();
        int order = 0;

        // Devengados
        lines.add(buildLine("SALARIO_BASE", "Salario base", "EARNING", proportionalSalary, order++));
        BigDecimal totalEarnings = proportionalSalary;
        for (var extra : extras) {
            Optional<PayrollConcept> conceptOpt = conceptRepository.findByCodeAndDeletedAtIsNull(extra.getConceptCode());
            String name = conceptOpt.map(PayrollConcept::getName).orElse(extra.getConceptCode());
            String type = conceptOpt.map(PayrollConcept::getConceptType).orElse("EARNING");
            if (!"EARNING".equals(type)) continue; // los extras manuales solo son devengados
            lines.add(buildLine(extra.getConceptCode(), name, "EARNING", extra.getAmount(), order++));
            totalEarnings = totalEarnings.add(extra.getAmount());
        }

        // IBC (base de cotizacion = devengados, pero no se usa auxilio transporte en IBC)
        BigDecimal ibc = totalEarnings;

        // Deducciones empleado
        BigDecimal saludEmp = applyPercentage(ibc, new BigDecimal("4.00"));
        BigDecimal pensionEmp = applyPercentage(ibc, new BigDecimal("4.00"));
        BigDecimal reteFuente = retentionService.calculate(req.getYear(), totalEarnings);
        BigDecimal totalDeductions = saludEmp.add(pensionEmp).add(reteFuente);

        lines.add(buildLine("SALUD_EMPLEADO", "Aporte salud empleado (4%)", "DEDUCTION", saludEmp, order++));
        lines.add(buildLine("PENSION_EMPLEADO", "Aporte pension empleado (4%)", "DEDUCTION", pensionEmp, order++));
        if (reteFuente.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(buildLine("RETE_FUENTE", "Retencion en la fuente", "DEDUCTION", reteFuente, order++));
        }

        // Aportes patronales
        BigDecimal saludEmpr = applyPercentage(ibc, new BigDecimal("8.50"));
        BigDecimal pensionEmpr = applyPercentage(ibc, new BigDecimal("12.00"));
        BigDecimal sena = applyPercentage(ibc, new BigDecimal("2.00"));
        BigDecimal icbf = applyPercentage(ibc, new BigDecimal("3.00"));
        BigDecimal caja = applyPercentage(ibc, new BigDecimal("4.00"));
        BigDecimal cesantias = applyPercentage(ibc, new BigDecimal("8.33"));
        BigDecimal prima = applyPercentage(ibc, new BigDecimal("8.33"));
        BigDecimal vacaciones = applyPercentage(ibc, new BigDecimal("4.17"));
        BigDecimal totalEmployer = saludEmpr.add(pensionEmpr).add(sena).add(icbf).add(caja)
                .add(cesantias).add(prima).add(vacaciones);

        lines.add(buildLine("SALUD_EMPRESA", "Aporte salud empresa (8.5%)", "EMPLOYER_CONTRIBUTION", saludEmpr, order++));
        lines.add(buildLine("PENSION_EMPRESA", "Aporte pension empresa (12%)", "EMPLOYER_CONTRIBUTION", pensionEmpr, order++));
        lines.add(buildLine("SENA", "Aporte SENA (2%)", "EMPLOYER_CONTRIBUTION", sena, order++));
        lines.add(buildLine("ICBF", "Aporte ICBF (3%)", "EMPLOYER_CONTRIBUTION", icbf, order++));
        lines.add(buildLine("CAJA_COMP", "Aporte caja de compensacion (4%)", "EMPLOYER_CONTRIBUTION", caja, order++));
        lines.add(buildLine("CESANTIAS", "Cesantias (8.33%)", "EMPLOYER_CONTRIBUTION", cesantias, order++));
        lines.add(buildLine("PRIMA", "Prima de servicios (8.33%)", "EMPLOYER_CONTRIBUTION", prima, order++));
        lines.add(buildLine("VACACIONES", "Vacaciones (4.17%)", "EMPLOYER_CONTRIBUTION", vacaciones, order++));

        BigDecimal netPay = totalEarnings.subtract(totalDeductions);

        // Crear y persistir cabecera
        PayrollReceipt receipt = PayrollReceipt.builder()
                .employeeId(emp.getId())
                .periodYear(req.getYear())
                .periodMonth(req.getMonth())
                .periodType(req.getPeriodType() != null ? req.getPeriodType() : "MONTHLY")
                .periodStart(LocalDate.of(req.getYear(), req.getMonth(), 1))
                .periodEnd(LocalDate.of(req.getYear(), req.getMonth(), 1)
                        .withDayOfMonth(LocalDate.of(req.getYear(), req.getMonth(), 1).lengthOfMonth()))
                .daysWorked(days)
                .totalEarnings(totalEarnings)
                .totalDeductions(totalDeductions)
                .totalEmployerContributions(totalEmployer)
                .netPay(netPay)
                .status("DRAFT")
                .build();
        receipt = receiptRepository.save(receipt);

        // Persistir lineas
        final Long receiptId = receipt.getId();
        for (PayrollLine line : lines) {
            line.setReceiptId(receiptId);
        }
        lineRepository.saveAll(lines);

        return receipt;
    }

    /**
     * Genera un unico JE consolidado del periodo con los totales de TODOS los recibos.
     *
     * <p>Partida doble (simplificada):
     * <ul>
     *   <li>Debito NOMINA_SALARIOS por (sumTotalEarnings + sumTotalEmployer) - gasto total de personal</li>
     *   <li>Credito NOMINA_CXP_EMPLEADOS por sumNetPay - lo que se debe a los empleados</li>
     *   <li>Credito NOMINA_RETENCIONES por (sumTotalDeductions - sumRetencionesEmp + sumTotalEmployer)
     *       - retenciones y aportes por pagar</li>
     * </ul>
     *
     * <p>Para el JE: Debito total = Credito total. Sumatorio simplificado.
     */
    private Long generateConsolidatedJournalEntry(List<PayrollReceipt> receipts,
                                                    LiquidatePayrollRequest req) {
        BigDecimal sumEarnings = receipts.stream()
                .map(PayrollReceipt::getTotalEarnings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDeductions = receipts.stream()
                .map(PayrollReceipt::getTotalDeductions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumEmployer = receipts.stream()
                .map(PayrollReceipt::getTotalEmployerContributions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumNet = receipts.stream()
                .map(PayrollReceipt::getNetPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Long accSalarios = accountMappingService.resolveOrThrow(AccountingConcept.NOMINA_SALARIOS);
        Long accCxpEmp = accountMappingService.resolveOrThrow(AccountingConcept.NOMINA_CXP_EMPLEADOS);
        Long accRetenciones = accountMappingService.resolveOrThrow(AccountingConcept.NOMINA_RETENCIONES);

        // Total debito = sumEarnings + sumEmployer (todo lo que se gasta)
        BigDecimal totalDebit = sumEarnings.add(sumEmployer);
        // Total credito (debe sumar igual):
        //   sumNet -> CxP empleados
        //   sumDeductions + sumEmployer -> retenciones y aportes
        // sumDeductions = sumNet's complement: earnings - deductions = net => deductions = earnings - net
        // Asi sumNet + sumDeductions = sumEarnings, y agregando sumEmployer => coincide con totalDebit
        BigDecimal creditRetenciones = sumDeductions.add(sumEmployer);

        LocalDate entryDate = LocalDate.of(req.getYear(), req.getMonth(), 1)
                .withDayOfMonth(LocalDate.of(req.getYear(), req.getMonth(), 1).lengthOfMonth());

        List<CreateJournalEntryLineRequest> jeLines = new ArrayList<>();
        jeLines.add(CreateJournalEntryLineRequest.builder()
                .accountingAccountId(accSalarios)
                .debitAmount(totalDebit)
                .creditAmount(BigDecimal.ZERO)
                .description("Gastos de personal del periodo " + req.getYear() + "-" + req.getMonth())
                .build());
        jeLines.add(CreateJournalEntryLineRequest.builder()
                .accountingAccountId(accCxpEmp)
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(sumNet)
                .description("CxP empleados (neto a pagar)")
                .build());
        jeLines.add(CreateJournalEntryLineRequest.builder()
                .accountingAccountId(accRetenciones)
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(creditRetenciones)
                .description("Retenciones y aportes de nomina por pagar")
                .build());

        CreateJournalEntryRequest jeReq = CreateJournalEntryRequest.builder()
                .entryDate(entryDate)
                .description("Liquidación de nómina - periodo " + req.getYear() + "-"
                        + String.format("%02d", req.getMonth()))
                .sourceModule(JournalSourceModule.NOM)
                .sourceId(null)
                .lines(jeLines)
                .build();

        String createdBy;
        try {
            createdBy = userUtil.getUser().getUsername();
        } catch (Exception ex) {
            createdBy = "system";
        }
        JournalEntryDTO dto = journalEntryService.createEntry(jeReq, createdBy);
        return dto.getId();
    }

    // ======== HU-NOM-04: flujo de aprobacion + bloqueo de edicion ========

    /**
     * HU-NOM-04 E2: intento de modificacion directa del recibo.
     *
     * <p>Si el recibo esta en APPROVED o CLOSED, se rechaza con el mensaje exacto
     * del Excel oficial. Si esta en DRAFT, se permite actualizar campos edicables
     * ({@code daysWorked}, {@code notes}, {@code periodStart}, {@code periodEnd}).
     * Los totales NO son editables directamente — para recalcular se debe volver
     * a ejecutar la liquidacion del periodo.
     *
     * @param receiptId identificador del recibo
     * @param daysWorked nuevos dias trabajados (opcional)
     * @param notes notas libres (opcional)
     * @return recibo actualizado
     * @throws IllegalStateException si el recibo esta en APPROVED o CLOSED
     */
    @Transactional
    public PayrollReceiptDTO updateReceipt(Long receiptId, Integer daysWorked, String notes) {
        PayrollReceipt r = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado"));

        if ("APPROVED".equals(r.getStatus()) || "CLOSED".equals(r.getStatus())) {
            throw new IllegalStateException(
                    "La nómina está [" + r.getStatus()
                    + "] y no puede modificarse. Para corregir errores, "
                    + "cree una nómina complementaria o de ajuste");
        }

        if (daysWorked != null && daysWorked > 0 && daysWorked <= 31) {
            r.setDaysWorked(daysWorked);
        }
        if (notes != null) {
            r.setNotes(notes);
        }
        PayrollReceipt saved = receiptRepository.save(r);
        auditPublisher.publishUpdate(AuditModule.NOM, "PayrollReceipt", saved.getId(),
                "Recibo de nomina actualizado (periodo " + saved.getPeriodYear()
                        + "-" + saved.getPeriodMonth() + ")");
        return toDTO(saved);
    }

    /** HU-NOM-04 E1: aprobar recibo y postear el JE asociado. */
    @Transactional
    public PayrollReceiptDTO approve(Long receiptId) {
        PayrollReceipt r = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado"));
        if (!"DRAFT".equals(r.getStatus())) {
            throw new IllegalStateException(
                    "Solo se pueden aprobar recibos en estado DRAFT (actual: " + r.getStatus() + ")");
        }
        String user;
        try { user = userUtil.getUser().getUsername(); } catch (Exception ex) { user = "system"; }
        r.setStatus("APPROVED");
        r.setApprovedBy(user);
        r.setApprovedAt(LocalDateTime.now());

        // Postear el JE si existe (si el recibo tenia JE en DRAFT)
        if (r.getJournalEntryId() != null) {
            try {
                journalEntryService.postEntry(r.getJournalEntryId());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                log.error("Error posteando JE {} del recibo {}: {}",
                        r.getJournalEntryId(), r.getId(), ex.getMessage());
                throw new IllegalStateException(
                        "No se pudo aprobar la nomina: " + ex.getMessage(), ex);
            } catch (RuntimeException ex) {
                log.error("Error inesperado posteando JE {} del recibo {}",
                        r.getJournalEntryId(), r.getId(), ex);
                throw ex;
            }
        }
        r = receiptRepository.save(r);
        auditPublisher.publishUpdate(AuditModule.NOM, "PayrollReceipt", r.getId(),
                "Recibo aprobado (periodo " + r.getPeriodYear() + "-" + r.getPeriodMonth()
                        + ", neto $" + r.getNetPay() + ")");
        return toDTO(r);
    }

    /** HU-NOM-04 E3: cierre definitivo. Inmutable. */
    @Transactional
    public PayrollReceiptDTO close(Long receiptId) {
        PayrollReceipt r = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado"));
        if (!"APPROVED".equals(r.getStatus())) {
            throw new IllegalStateException(
                    "Solo se pueden cerrar recibos en estado APPROVED (actual: " + r.getStatus() + ")");
        }
        String user;
        try { user = userUtil.getUser().getUsername(); } catch (Exception ex) { user = "system"; }
        r.setStatus("CLOSED");
        r.setClosedBy(user);
        r.setClosedAt(LocalDateTime.now());
        r = receiptRepository.save(r);
        auditPublisher.publishUpdate(AuditModule.NOM, "PayrollReceipt", r.getId(),
                "Recibo cerrado definitivamente (periodo " + r.getPeriodYear()
                        + "-" + r.getPeriodMonth() + ")");
        return toDTO(r);
    }

    // ======== Consultas ========

    @Transactional(readOnly = true)
    public List<PayrollReceiptDTO> getByPeriod(Integer year, Integer month) {
        List<PayrollReceipt> receipts = receiptRepository
                .findByPeriodYearAndPeriodMonthAndDeletedAtIsNull(year, month);
        return receipts.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PayrollReceiptDTO getById(Long id) {
        PayrollReceipt r = receiptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado"));
        return toDTO(r);
    }

    // ======== Helpers ========

    private PayrollLine buildLine(String code, String name, String type, BigDecimal amount, int order) {
        return PayrollLine.builder()
                .conceptCode(code)
                .conceptName(name)
                .lineType(type)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .lineOrder(order)
                .build();
    }

    private BigDecimal applyPercentage(BigDecimal base, BigDecimal percentage) {
        return base.multiply(percentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private PayrollReceiptDTO toDTO(PayrollReceipt r) {
        String employeeName = null;
        String employeeDoc = null;
        Optional<Employee> empOpt = employeeRepository.findById(r.getEmployeeId());
        if (empOpt.isPresent()) {
            employeeName = empOpt.get().getFullName();
            employeeDoc = empOpt.get().getDocumentNumber();
        }
        List<PayrollLine> lines = lineRepository
                .findByReceiptIdAndDeletedAtIsNullOrderByLineOrder(r.getId());
        return PayrollReceiptDTO.from(r, employeeName, employeeDoc, lines);
    }
}
