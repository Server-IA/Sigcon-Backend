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
        validateAccounts(req.getAccountingAccountDebitId(), req.getAccountingAccountCreditId());
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
        auditPublisher.publishUpdate(AuditModule.NOM, "PayrollConcept", saved.getId(),
                "Concepto de nomina actualizado: " + saved.getCode() + " - " + saved.getName());
        return ResponseEntity.ok(PayrollConceptDTO.from(saved));
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

    // ======== Helpers ========

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
