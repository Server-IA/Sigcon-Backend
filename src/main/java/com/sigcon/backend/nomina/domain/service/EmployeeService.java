package com.sigcon.backend.nomina.domain.service;

import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.nomina.application.CreateEmployeeRequest;
import com.sigcon.backend.nomina.application.EmployeeDTO;
import com.sigcon.backend.nomina.application.EmployeeSalaryHistoryDTO;
import com.sigcon.backend.nomina.domain.model.Employee;
import com.sigcon.backend.nomina.domain.model.EmployeeSalaryHistory;
import com.sigcon.backend.nomina.domain.repository.EmployeeRepository;
import com.sigcon.backend.nomina.domain.repository.EmployeeSalaryHistoryRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.UserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HU-NOM-01: gestion de empleados de nomina.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>CRUD completo con validacion de unicidad por documento.</li>
 *   <li>Validar que el salario base sea mayor o igual al SMLV vigente (HU-NOM-01 E2).</li>
 *   <li>Registrar automaticamente el historial salarial en cada cambio de salario
 *       exigiendo un motivo (HU-NOM-01 E3).</li>
 * </ul>
 *
 * <p>El SMLV se lee del parametro {@code sigcon.nomina.smlv} (categoria NOMINA)
 * para ser parametrizable sin modificar codigo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSalaryHistoryRepository historyRepository;
    private final ParameterRepository parameterRepository;
    private final UserUtil userUtil;

    /** Builder reutilizable para busquedas DataTable. */
    private final DataTableSpecificationBuilder<Employee> specBuilder = new DataTableSpecificationBuilder<>();

    private static final String PARAM_SMLV = "sigcon.nomina.smlv";
    private static final BigDecimal DEFAULT_SMLV = new BigDecimal("1423500");

    // ======== CRUD ========

    /** Listar todos los empleados (sin paginar). */
    @Transactional(readOnly = true)
    public ResponseEntity<?> list() {
        List<EmployeeDTO> data = employeeRepository.findAll().stream()
                .map(EmployeeDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(data);
    }

    /** DataTable paginado para volumenes grandes. */
    @Transactional(readOnly = true)
    public ResponseEntity<?> search(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;
        Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);

        Specification<Employee> spec = specBuilder.build(request);
        Page<EmployeeDTO> data = employeeRepository.findAll(spec, pageable).map(EmployeeDTO::from);
        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getById(Long id) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        return ResponseEntity.ok(EmployeeDTO.from(e));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getSalaryHistory(Long id) {
        List<EmployeeSalaryHistoryDTO> data = historyRepository
                .findByEmployeeIdAndDeletedAtIsNullOrderByEffectiveDateDesc(id).stream()
                .map(EmployeeSalaryHistoryDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(data);
    }

    @Transactional
    public ResponseEntity<?> create(CreateEmployeeRequest req) {
        validateSmlv(req.getBaseSalary());

        // HU-NOM-01: unicidad por (documentType, documentNumber)
        if (employeeRepository.existsByDocumentTypeAndDocumentNumberAndDeletedAtIsNull(
                req.getDocumentType(), req.getDocumentNumber())) {
            throw new IllegalArgumentException(
                    "Ya existe un empleado con documento " + req.getDocumentType() + " "
                    + req.getDocumentNumber());
        }

        Employee e = Employee.builder()
                .thirdPartyId(req.getThirdPartyId())
                .documentType(req.getDocumentType())
                .documentNumber(req.getDocumentNumber())
                .fullName(req.getFullName())
                .position(req.getPosition())
                .contractType(req.getContractType())
                .baseSalary(req.getBaseSalary())
                .hireDate(req.getHireDate())
                .eps(req.getEps())
                .pensionFund(req.getPensionFund())
                .arl(req.getArl())
                .compensationBox(req.getCompensationBox())
                .costCenterId(req.getCostCenterId())
                .status("ACTIVE")
                .build();
        e = employeeRepository.save(e);
        return ResponseEntity.ok(EmployeeDTO.from(e));
    }

    @Transactional
    public ResponseEntity<?> update(Long id, CreateEmployeeRequest req) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        validateSmlv(req.getBaseSalary());

        // HU-NOM-01 E3: si cambia baseSalary, exigir motivo y persistir historial
        if (req.getBaseSalary() != null
                && e.getBaseSalary() != null
                && req.getBaseSalary().compareTo(e.getBaseSalary()) != 0) {
            if (req.getSalaryChangeReason() == null || req.getSalaryChangeReason().isBlank()) {
                throw new IllegalArgumentException(
                        "Se requiere un motivo para registrar el cambio de salario (HU-NOM-01 E3)");
            }
            String changedBy;
            try {
                changedBy = userUtil.getUser().getUsername();
            } catch (Exception ex) {
                changedBy = "system";
            }
            historyRepository.save(EmployeeSalaryHistory.builder()
                    .employeeId(e.getId())
                    .previousSalary(e.getBaseSalary())
                    .newSalary(req.getBaseSalary())
                    .effectiveDate(LocalDate.now())
                    .reason(req.getSalaryChangeReason())
                    .changedBy(changedBy)
                    .build());
        }

        e.setThirdPartyId(req.getThirdPartyId());
        e.setDocumentType(req.getDocumentType());
        e.setDocumentNumber(req.getDocumentNumber());
        e.setFullName(req.getFullName());
        e.setPosition(req.getPosition());
        e.setContractType(req.getContractType());
        e.setBaseSalary(req.getBaseSalary());
        e.setHireDate(req.getHireDate());
        e.setEps(req.getEps());
        e.setPensionFund(req.getPensionFund());
        e.setArl(req.getArl());
        e.setCompensationBox(req.getCompensationBox());
        e.setCostCenterId(req.getCostCenterId());
        return ResponseEntity.ok(EmployeeDTO.from(employeeRepository.save(e)));
    }

    @Transactional
    public ResponseEntity<?> delete(Long id) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado"));
        employeeRepository.delete(e);
        return ResponseEntity.ok("Empleado eliminado");
    }

    // ======== Helpers ========

    /** HU-NOM-01 E2: valida que el salario sea &ge; SMLV vigente. */
    private void validateSmlv(BigDecimal salary) {
        if (salary == null) {
            throw new IllegalArgumentException("baseSalary es obligatorio");
        }
        BigDecimal smlv = currentSmlv();
        if (salary.compareTo(smlv) < 0) {
            throw new IllegalArgumentException(String.format(
                    "El salario base $%s es inferior al SMLV vigente $%s. "
                    + "El salario minimo legal vigente no puede incumplirse (CST Art. 145)",
                    salary.toPlainString(), smlv.toPlainString()));
        }
    }

    /** Lee SMLV del parametro configurable. Fallback al default del 2026 si no existe. */
    private BigDecimal currentSmlv() {
        return parameterRepository.findByNameAndDeletedAtIsNull(PARAM_SMLV)
                .map(p -> {
                    try { return new BigDecimal(p.getValue()); }
                    catch (NumberFormatException ex) {
                        log.warn("Valor de {} no es numerico: {}", PARAM_SMLV, p.getValue());
                        return DEFAULT_SMLV;
                    }
                })
                .orElse(DEFAULT_SMLV);
    }
}
