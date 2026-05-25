package com.sigcon.backend.nomina.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.nomina.domain.model.Employee;
import com.sigcon.backend.nomina.domain.repository.EmployeeRepository;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-NOM-05: liquidacion de prestaciones sociales.
 *
 * <p>Cubre 3 escenarios:
 * <ul>
 *   <li>E1: Cesantias e intereses - base del salario promedio y dias trabajados (CST Art. 249, Ley 52/1975).</li>
 *   <li>E2: Prima de servicios semestral - dos liquidaciones por año (CST Art. 306).</li>
 *   <li>E3: Liquidacion definitiva de contrato - cesantias pendientes + intereses + prima proporcional +
 *       vacaciones compensadas + indemnizacion si aplica (CST Art. 64).</li>
 * </ul>
 *
 * <p>Formulas base (en dias ordinarios de 360 que usa el CST colombiano):
 * <pre>
 *   cesantias  = salario * dias_trabajados / 360
 *   intereses  = cesantias * 12% * dias_trabajados / 360   (Ley 52/1975)
 *   prima      = salario * dias_trabajados_semestre / 360  (CST Art. 306)
 *   vacaciones = salario * dias_trabajados / 720           (15 dias por año)
 *   indemniz.  (sin justa causa, Art. 64):
 *     - contrato a termino indefinido con salario < 10 SMLMV:
 *         30 dias por el primer año + 20 dias adicionales por cada año subsiguiente
 *     - contrato a termino indefinido con salario >= 10 SMLMV:
 *         20 dias por el primer año + 15 dias adicionales por cada año subsiguiente
 * </pre>
 *
 * <p>Cada liquidacion genera un JournalEntry consolidado por empleado via
 * {@code JournalEntryService} usando las cuentas mapeadas (NOMINA_SALARIOS /
 * NOMINA_CESANTIAS / NOMINA_CXP_EMPLEADOS).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitLiquidationService {

    private final EmployeeRepository employeeRepository;
    private final JournalEntryService journalEntryService;
    private final AccountMappingService accountMappingService;
    private final UserUtil userUtil;
    private final AuditPublisher auditPublisher;

    /** HU-NOM-05 E1: cesantias + intereses del año. */
    @Transactional
    public Map<String, Object> liquidateSeverance(Long employeeId, int year) {
        Employee emp = loadEmployee(employeeId);
        BigDecimal salary = emp.getBaseSalary();
        LocalDate hire = emp.getHireDate() != null ? emp.getHireDate() : LocalDate.of(year, 1, 1);
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate from = hire.isAfter(yearStart) ? hire : yearStart;
        LocalDate to = emp.getTerminationDate() != null && emp.getTerminationDate().isBefore(yearEnd)
                ? emp.getTerminationDate() : yearEnd;

        long daysWorked = daysBetweenInclusive(from, to);
        BigDecimal severance = salary.multiply(BigDecimal.valueOf(daysWorked))
                .divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);
        BigDecimal interest = severance.multiply(new BigDecimal("0.12"))
                .multiply(BigDecimal.valueOf(daysWorked))
                .divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);

        // JE: D Gasto Cesantias (NOMINA_SALARIOS) / C CxP Fondo Cesantias (NOMINA_CESANTIAS)
        Long journalEntryId = postSimpleJE(
                "Liquidación cesantías e intereses - " + emp.getFullName() + " - " + year,
                severance.add(interest),
                AccountingConcept.NOMINA_SALARIOS,
                AccountingConcept.NOMINA_CESANTIAS,
                yearEnd);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("employeeId", emp.getId());
        resp.put("employeeName", emp.getFullName());
        resp.put("year", year);
        resp.put("daysWorked", daysWorked);
        resp.put("baseSalary", salary);
        resp.put("severance", severance);
        resp.put("interest", interest);
        resp.put("totalPayable", severance.add(interest));
        resp.put("journalEntryId", journalEntryId);
        resp.put("legalRef", "CST Art. 249, Ley 52/1975. Consignacion antes del 15 de febrero");

        auditPublisher.publishCreate(AuditModule.NOM, "SeveranceLiquidation", emp.getId(),
                "Cesantias liquidadas empleado=" + emp.getFullName()
                        + " año=" + year + " total=$" + severance.add(interest));
        return resp;
    }

    /** HU-NOM-05 E2: prima de servicios semestral (junio/diciembre). */
    @Transactional
    public Map<String, Object> liquidateServiceBonus(Long employeeId, int year, int semester) {
        if (semester != 1 && semester != 2) {
            throw new IllegalArgumentException("semester debe ser 1 o 2");
        }
        Employee emp = loadEmployee(employeeId);
        BigDecimal salary = emp.getBaseSalary();
        LocalDate semesterStart = semester == 1
                ? LocalDate.of(year, 1, 1) : LocalDate.of(year, 7, 1);
        LocalDate semesterEnd = semester == 1
                ? LocalDate.of(year, 6, 30) : LocalDate.of(year, 12, 31);
        LocalDate hire = emp.getHireDate();
        LocalDate from = hire != null && hire.isAfter(semesterStart) ? hire : semesterStart;
        LocalDate to = emp.getTerminationDate() != null && emp.getTerminationDate().isBefore(semesterEnd)
                ? emp.getTerminationDate() : semesterEnd;
        long days = daysBetweenInclusive(from, to);
        BigDecimal bonus = salary.multiply(BigDecimal.valueOf(days))
                .divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);

        Long journalEntryId = postSimpleJE(
                "Prima de servicios " + year + "-S" + semester + " - " + emp.getFullName(),
                bonus,
                AccountingConcept.NOMINA_SALARIOS,
                AccountingConcept.NOMINA_CXP_EMPLEADOS,
                semesterEnd);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("employeeId", emp.getId());
        resp.put("employeeName", emp.getFullName());
        resp.put("year", year);
        resp.put("semester", semester);
        resp.put("daysWorked", days);
        resp.put("baseSalary", salary);
        resp.put("bonus", bonus);
        resp.put("journalEntryId", journalEntryId);
        resp.put("legalRef", "CST Art. 306 - 30 junio / 20 diciembre");

        auditPublisher.publishCreate(AuditModule.NOM, "ServiceBonusLiquidation", emp.getId(),
                "Prima de servicios liquidada empleado=" + emp.getFullName()
                        + " " + year + "-S" + semester + " monto=$" + bonus);
        return resp;
    }

    /**
     * HU-NOM-05 E3: liquidacion definitiva de contrato.
     *
     * @param terminationDate fecha de retiro
     * @param terminationType SIN_JUSTA_CAUSA | JUSTA_CAUSA | MUTUO_ACUERDO | RENUNCIA
     */
    @Transactional
    public Map<String, Object> liquidateTermination(Long employeeId, LocalDate terminationDate,
                                                     String terminationType) {
        Employee emp = loadEmployee(employeeId);
        BigDecimal salary = emp.getBaseSalary();
        LocalDate hire = emp.getHireDate() != null ? emp.getHireDate() : terminationDate;

        long totalDays = daysBetweenInclusive(hire, terminationDate);

        // Cesantias proporcionales (todo el periodo desde contratacion)
        BigDecimal severance = salary.multiply(BigDecimal.valueOf(totalDays))
                .divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);
        BigDecimal severanceInterest = severance.multiply(new BigDecimal("0.12"))
                .multiply(BigDecimal.valueOf(totalDays))
                .divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);

        // Prima proporcional al semestre actual
        int year = terminationDate.getYear();
        int semester = terminationDate.getMonthValue() <= 6 ? 1 : 2;
        LocalDate semesterStart = semester == 1
                ? LocalDate.of(year, 1, 1) : LocalDate.of(year, 7, 1);
        LocalDate primaFrom = hire.isAfter(semesterStart) ? hire : semesterStart;
        long primaDays = daysBetweenInclusive(primaFrom, terminationDate);
        BigDecimal bonus = salary.multiply(BigDecimal.valueOf(primaDays))
                .divide(new BigDecimal("360"), 2, RoundingMode.HALF_UP);

        // Vacaciones compensadas (15 dias por año)
        BigDecimal vacation = salary.multiply(BigDecimal.valueOf(totalDays))
                .divide(new BigDecimal("720"), 2, RoundingMode.HALF_UP);

        // Indemnizacion si aplica (CST Art. 64 - sin justa causa, contrato indefinido)
        // QA Nomina (2026-05-25) ERR-NOM-006: la indemnizacion salia SIEMPRE en 0
        // porque la condicion solo aceptaba contractType "INDEFINIDO", pero los
        // empleados sembrados/migrados usan "PERMANENT" (mismo concepto: contrato
        // a termino indefinido). Aceptamos ambos para que Art. 64 se calcule.
        BigDecimal severancePay = BigDecimal.ZERO;
        String contract = emp.getContractType() != null ? emp.getContractType().trim() : "";
        boolean indefiniteContract = "INDEFINIDO".equalsIgnoreCase(contract)
                || "PERMANENT".equalsIgnoreCase(contract)
                || "INDEFINITE".equalsIgnoreCase(contract);
        if ("SIN_JUSTA_CAUSA".equalsIgnoreCase(terminationType) && indefiniteContract) {
            long years = Math.max(1, totalDays / 360);
            BigDecimal daysPay;
            BigDecimal smlmv10 = new BigDecimal("14235000"); // ~10 SMLMV 2026
            if (salary.compareTo(smlmv10) < 0) {
                // 30 dias primer año + 20 dias por cada año adicional
                daysPay = BigDecimal.valueOf(30 + (years - 1) * 20);
            } else {
                daysPay = BigDecimal.valueOf(20 + (years - 1) * 15);
            }
            severancePay = salary.multiply(daysPay)
                    .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        }

        BigDecimal total = severance.add(severanceInterest).add(bonus).add(vacation).add(severancePay);

        Long journalEntryId = postSimpleJE(
                "Liquidación definitiva de contrato - " + emp.getFullName()
                        + " (" + terminationType + ")",
                total,
                AccountingConcept.NOMINA_SALARIOS,
                AccountingConcept.NOMINA_CXP_EMPLEADOS,
                terminationDate);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("employeeId", emp.getId());
        resp.put("employeeName", emp.getFullName());
        resp.put("terminationDate", terminationDate);
        resp.put("terminationType", terminationType);
        resp.put("totalDays", totalDays);
        resp.put("severance", severance);
        resp.put("severanceInterest", severanceInterest);
        resp.put("serviceBonus", bonus);
        resp.put("vacationCompensation", vacation);
        resp.put("severancePay", severancePay);
        resp.put("totalPayable", total);
        resp.put("journalEntryId", journalEntryId);
        resp.put("legalRef", "CST Art. 64 (indemnización), 249, 306, 186");

        auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.CREATE,
                AuditModule.NOM,
                com.sigcon.backend.audit.domain.model.enums.AuditSeverity.HIGH,
                "TerminationLiquidation", emp.getId(),
                "Liquidacion definitiva contrato empleado=" + emp.getFullName()
                        + " tipo=" + terminationType + " total=$" + total,
                null, null, journalEntryId);
        return resp;
    }

    // ======== Helpers ========

    private Employee loadEmployee(Long id) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        if (emp.getBaseSalary() == null || emp.getBaseSalary().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El empleado no tiene salario base configurado");
        }
        return emp;
    }

    private long daysBetweenInclusive(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) return 0;
        return Math.min(ChronoUnit.DAYS.between(from, to) + 1, 360);
    }

    /** Crea un JE simple D: debitConcept / C: creditConcept por el total indicado. */
    private Long postSimpleJE(String description, BigDecimal amount,
                                String debitConcept, String creditConcept,
                                LocalDate entryDate) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return null;
        Long debitAcc = accountMappingService.resolveOrThrow(debitConcept);
        Long creditAcc = accountMappingService.resolveOrThrow(creditConcept);
        // QA Nomina (2026-05-25) ERR-NOM-002: el JE no puede tener fecha futura.
        // Las cesantias se fechan a fin de anio (31-dic) y la prima a fin de
        // semestre; si se liquidan antes de esa fecha, JournalEntryService las
        // rechazaba con "No se permiten comprobantes con fecha futura". Clampeamos.
        LocalDate safeEntryDate = entryDate != null && entryDate.isAfter(LocalDate.now())
                ? LocalDate.now() : entryDate;
        CreateJournalEntryRequest req = CreateJournalEntryRequest.builder()
                .entryDate(safeEntryDate)
                .description(description)
                .sourceModule(JournalSourceModule.NOM)
                .lines(java.util.List.of(
                        CreateJournalEntryLineRequest.builder()
                                .accountingAccountId(debitAcc)
                                .debitAmount(amount)
                                .creditAmount(BigDecimal.ZERO)
                                .description("Debito - " + debitConcept)
                                .build(),
                        CreateJournalEntryLineRequest.builder()
                                .accountingAccountId(creditAcc)
                                .debitAmount(BigDecimal.ZERO)
                                .creditAmount(amount)
                                .description("Credito - " + creditConcept)
                                .build()
                ))
                .build();
        String user;
        try { user = userUtil.getUser().getUsername(); } catch (Exception ex) { user = "system"; }
        JournalEntryDTO dto = journalEntryService.createEntry(req, user);
        return dto.getId();
    }
}
