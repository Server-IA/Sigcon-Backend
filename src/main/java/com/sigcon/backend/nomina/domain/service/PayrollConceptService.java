package com.sigcon.backend.nomina.domain.service;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.nomina.application.CreatePayrollConceptRequest;
import com.sigcon.backend.nomina.application.PayrollConceptDTO;
import com.sigcon.backend.nomina.domain.model.PayrollConcept;
import com.sigcon.backend.nomina.domain.repository.PayrollConceptRepository;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * HU-NOM-02: gestion de conceptos de nomina con cuentas PUC.
 *
 * <p>Valida:
 * <ul>
 *   <li>Unicidad del code.</li>
 *   <li>Que las cuentas PUC debito/credito existan y esten ACTIVE (HU-NOM-02 E3).
 *       Mensaje exacto al rechazar una cuenta inactiva.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollConceptService {

    private final PayrollConceptRepository conceptRepository;
    private final AccountingAccountRepository accountRepository;
    private final AuditPublisher auditPublisher;

    @Transactional(readOnly = true)
    public ResponseEntity<?> list(String status, String type) {
        List<PayrollConcept> items;
        if (type != null && !type.isBlank()) {
            items = conceptRepository.findByConceptTypeAndStatusAndDeletedAtIsNullOrderByCode(
                    type.toUpperCase(), status != null ? status : "ACTIVE");
        } else if (status != null && !status.isBlank()) {
            items = conceptRepository.findByStatusAndDeletedAtIsNullOrderByConceptTypeAscCodeAsc(status);
        } else {
            items = conceptRepository.findAll();
        }
        List<PayrollConceptDTO> data = items.stream()
                .map(PayrollConceptDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(data);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getById(Long id) {
        PayrollConcept c = conceptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Concepto no encontrado"));
        return ResponseEntity.ok(PayrollConceptDTO.from(c));
    }

    @Transactional
    public ResponseEntity<?> create(CreatePayrollConceptRequest req) {
        if (conceptRepository.existsByCodeAndDeletedAtIsNull(req.getCode())) {
            throw new IllegalArgumentException("Ya existe un concepto con code " + req.getCode());
        }
        // HAL-02: coherencia del tipo de calculo (porcentaje vs valor fijo).
        validateCalculationCoherence(req);
        // HAL-03: cuentas PUC debito + credito obligatorias y activas.
        requireAccountsAssigned(req.getAccountingAccountDebitId(), req.getAccountingAccountCreditId());
        validateAccounts(req.getAccountingAccountDebitId(), req.getAccountingAccountCreditId());
        PayrollConcept c = PayrollConcept.builder()
                .code(req.getCode())
                .name(req.getName())
                .conceptType(req.getConceptType())
                .baseCalculation(req.getBaseCalculation())
                .percentage(req.getPercentage())
                .fixedAmount(req.getFixedAmount())
                .formulaExpression(req.getFormulaExpression())
                .accountingAccountDebitId(req.getAccountingAccountDebitId())
                .accountingAccountCreditId(req.getAccountingAccountCreditId())
                .legalReference(req.getLegalReference())
                .status(req.getStatus() != null ? req.getStatus() : "ACTIVE")
                .build();
        PayrollConcept saved = conceptRepository.save(c);
        auditPublisher.publishCreate(AuditModule.NOM, "PayrollConcept", saved.getId(),
                "Concepto de nomina creado: " + saved.getCode() + " - " + saved.getName()
                        + " (" + saved.getConceptType() + ")");
        return ResponseEntity.ok(PayrollConceptDTO.from(saved));
    }

    @Transactional
    public ResponseEntity<?> update(Long id, CreatePayrollConceptRequest req) {
        PayrollConcept c = conceptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Concepto no encontrado"));
        // HAL-02: coherencia del tipo de calculo (porcentaje vs valor fijo).
        validateCalculationCoherence(req);
        // HAL-03: cuentas PUC debito + credito obligatorias y activas.
        requireAccountsAssigned(req.getAccountingAccountDebitId(), req.getAccountingAccountCreditId());
        validateAccounts(req.getAccountingAccountDebitId(), req.getAccountingAccountCreditId());

        // QA Nomina (2026-05-25) ERR-NOM-003: capturar snapshot ANTERIOR para que la
        // auditoria registre "valor anterior -> valor nuevo" (la HU exige quien, valor
        // anterior, valor nuevo y fecha), no solo una descripcion generica. El campo
        // `code` es inmutable en update.
        String oldValues = snapshotJson(c);

        c.setName(req.getName());
        c.setConceptType(req.getConceptType());
        c.setBaseCalculation(req.getBaseCalculation());
        c.setPercentage(req.getPercentage());
        c.setFixedAmount(req.getFixedAmount());
        c.setFormulaExpression(req.getFormulaExpression());
        c.setAccountingAccountDebitId(req.getAccountingAccountDebitId());
        c.setAccountingAccountCreditId(req.getAccountingAccountCreditId());
        c.setLegalReference(req.getLegalReference());
        if (req.getStatus() != null) c.setStatus(req.getStatus());
        PayrollConcept saved = conceptRepository.save(c);

        String newValues = snapshotJson(saved);
        auditPublisher.publishUpdate(AuditModule.NOM, "PayrollConcept", saved.getId(),
                "Concepto de nomina actualizado: " + saved.getCode() + " - " + saved.getName(),
                oldValues, newValues);
        return ResponseEntity.ok(PayrollConceptDTO.from(saved));
    }

    /** ERR-NOM-003: snapshot JSON de los campos auditables del concepto. */
    private String snapshotJson(PayrollConcept c) {
        return String.format(
                "{\"name\":\"%s\",\"percentage\":%s,\"fixedAmount\":%s,\"status\":\"%s\","
                + "\"debitId\":%s,\"creditId\":%s}",
                jsonSafe(c.getName()), c.getPercentage(), c.getFixedAmount(), jsonSafe(c.getStatus()),
                c.getAccountingAccountDebitId(), c.getAccountingAccountCreditId());
    }

    private String jsonSafe(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "'");
    }

    @Transactional
    public ResponseEntity<?> delete(Long id) {
        PayrollConcept c = conceptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Concepto no encontrado"));
        conceptRepository.delete(c);
        auditPublisher.publishDelete(AuditModule.NOM, "PayrollConcept", c.getId(),
                "Concepto de nomina eliminado: " + c.getCode() + " - " + c.getName());
        return ResponseEntity.ok("Concepto eliminado");
    }

    /**
     * HAL-05/HAL-06 soporte: cambia SOLO el estado (ACTIVE/INACTIVE) de un concepto
     * sin exigir cuentas PUC ni coherencia de calculo. Necesario porque los conceptos
     * legales precargados pueden no tener cuentas asignadas y deben poder
     * activarse/inactivarse sin abrir el formulario completo.
     */
    @Transactional
    public ResponseEntity<?> changeStatus(Long id, String status) {
        if (status == null || !(status.equals("ACTIVE") || status.equals("INACTIVE"))) {
            throw new IllegalArgumentException("Estado invalido. Use ACTIVE o INACTIVE.");
        }
        PayrollConcept c = conceptRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Concepto no encontrado"));
        String old = c.getStatus();
        c.setStatus(status);
        PayrollConcept saved = conceptRepository.save(c);
        auditPublisher.publishUpdate(AuditModule.NOM, "PayrollConcept", saved.getId(),
                "Concepto " + saved.getCode() + " "
                        + ("ACTIVE".equals(status) ? "activado" : "inactivado"),
                "{\"status\":\"" + jsonSafe(old) + "\"}",
                "{\"status\":\"" + jsonSafe(status) + "\"}");
        return ResponseEntity.ok(PayrollConceptDTO.from(saved));
    }

    // ======== Helpers ========

    /**
     * HAL-02: valida que la configuracion del concepto sea coherente con su base de
     * calculo. Un concepto de valor fijo (FIXED) NO admite porcentaje; un concepto
     * porcentual (IBC/SALARY) requiere porcentaje valido (0-100) y no admite monto fijo.
     * CUSTOM no se restringe (formula libre).
     */
    private void validateCalculationCoherence(CreatePayrollConceptRequest req) {
        String base = req.getBaseCalculation() != null ? req.getBaseCalculation().toUpperCase() : "";
        java.math.BigDecimal pct = req.getPercentage();
        java.math.BigDecimal fixed = req.getFixedAmount();
        boolean hasPct = pct != null && pct.signum() != 0;
        boolean hasFixed = fixed != null && fixed.signum() != 0;

        if (hasPct && (pct.signum() < 0 || pct.compareTo(new java.math.BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("El porcentaje debe estar entre 0 y 100.");
        }
        switch (base) {
            case "FIXED" -> {
                if (hasPct) {
                    throw new IllegalArgumentException(
                            "El concepto '" + req.getCode() + "' es de valor fijo (base de calculo FIJO) "
                            + "y no admite porcentaje. El salario base y otros valores absolutos se "
                            + "definen por empleado, no como un porcentaje de una base.");
                }
            }
            case "IBC", "SALARY" -> {
                if (!hasPct) {
                    throw new IllegalArgumentException(
                            "El concepto '" + req.getCode() + "' usa base de calculo porcentual "
                            + "(IBC/Salario) y requiere un porcentaje valido entre 0 y 100.");
                }
                if (hasFixed) {
                    throw new IllegalArgumentException(
                            "El concepto '" + req.getCode() + "' es porcentual (IBC/Salario) y no debe "
                            + "tener monto fijo. Use porcentaje o cambie la base de calculo a FIJO.");
                }
            }
            default -> { /* CUSTOM u otros: formula libre, sin restriccion */ }
        }
    }

    /**
     * HAL-03: exige que el concepto tenga asignadas las cuentas PUC debito y credito.
     * (La activacion/inactivacion via changeStatus NO pasa por aqui.)
     */
    private void requireAccountsAssigned(Long debitId, Long creditId) {
        if (debitId == null || creditId == null) {
            throw new IllegalArgumentException(
                    "Debe asignar las cuentas PUC debito y credito (activas) al concepto antes de guardar. "
                    + "Seleccionelas en Listas Contables.");
        }
    }

    /**
     * HU-NOM-02 E3: valida que las cuentas PUC asignadas esten ACTIVE.
     * Si alguna esta INACTIVE, lanza error con el mensaje exacto del Excel.
     */
    private void validateAccounts(Long debitId, Long creditId) {
        checkAccountActive(debitId, "debito");
        checkAccountActive(creditId, "credito");
    }

    private void checkAccountActive(Long id, String role) {
        if (id == null) return;
        AccountingAccount a = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La cuenta PUC asignada al concepto (" + role + ") no existe"));
        if (a.getStatus() != AccountStatus.ACTIVE) {
            String pucCode = (a.getPucAccount() != null && a.getPucAccount().getCode() != null)
                    ? a.getPucAccount().getCode() : String.valueOf(a.getId());
            throw new IllegalArgumentException(
                    "La cuenta PUC [" + pucCode + "] asignada al concepto está inactiva. "
                    + "Active la cuenta en Listas Contables antes de usarla en conceptos de nómina");
        }
    }
}
