package com.sigcon.backend.lists_accounting.accounting_account.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.repository.ChartOfAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.application.CreateAccountingAccountRequest;
import com.sigcon.backend.lists_accounting.accounting_account.application.UpdateAccountingAccountRequest;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.cost_centers.domain.repository.CostCenterRepository;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.repository.DepretationRuleRepository;
import com.sigcon.backend.lists_accounting.ruler_tax.application.RuleTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.repository.RuleTaxRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class AccountingAccountService {

    private final AccountingAccountRepository accountingAccountRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final CostCenterRepository costCenterRepository;
    private final DepretationRuleRepository depretationRuleRepository;
    private final RuleTaxRepository ruleTaxRepository;
    /** CFG-08: validacion saldo != 0 al inactivar cuenta contable. */
    private final com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryLineRepository journalEntryLineRepository;

    private final DataTableSpecificationBuilder<AccountingAccount> accountingAccountSpecificationBuilder = new DataTableSpecificationBuilder<>();

    /**
     * CFG-RF-06: Consultar cuentas contables existentes
     */
    public ResponseEntity<?> getAccountingAccounts(DataTableRequest request) {
        // try {
            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                    ? Pageable.unpaged()
                    : PageRequest.of(page, safeLength);

            Specification<AccountingAccount> spec = accountingAccountSpecificationBuilder.build(request)
                    .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<AccountingAccount> accountingAccounts = accountingAccountRepository.findAll(spec, pageable);

            return ResponseEntity.ok(
                    DataTableResponse.from(accountingAccounts.map(accountingAccount -> AccountingAccountDTO.builder()
                            .id(accountingAccount.getId())
                            .puc_id(accountingAccount.getPucAccount().getId())
                            .pucAccount(
                                accountingAccount.getPucAccount() != null ?
                                    getChartOfAccountResponseDTO(accountingAccount.getPucAccount())
                                    : null
                            )
                            .customName(accountingAccount.getCustomName())
                            .currencyType(accountingAccount.getCurrencyType() != null
                                    ? getCurrencyTypeResponseDTO(accountingAccount.getCurrencyType())
                                    : null)
                            .costCenter(accountingAccount.getCostCenter() != null
                                    ? getCostCenterResponseDTO(accountingAccount.getCostCenter())
                                    : null)
                            .taxRules(
                                ruleTaxRepository.findByAccountingAccountId(accountingAccount.getId())
                                .stream().map(this::convertRuleTaxToDTO)
                                .toList()
                            )
                            // .taxRuleId(accountingAccount.getTaxRuleId())
                            .nature(accountingAccount.getNature())
                            .status(accountingAccount.getStatus())
                            .createdAt(accountingAccount.getCreatedAt())
                            .updatedAt(accountingAccount.getUpdatedAt())
                            .build()), request.getDraw()));

        // } catch (Exception e) {
        //     return ResponseEntity.badRequest().body(ErrorRespondJson
        //             .getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    /**
     * CFG-RF-05: Crear cuenta contable
     */
    public ResponseEntity<?> createAccountingAccount(CreateAccountingAccountRequest request,
            BindingResult bindingResult, Long userId, Long companyId) {

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // Validacion: PUC existe
            if (!chartOfAccountRepository.findById(request.getPuc_id()).isPresent()) {
                throw new IllegalArgumentException("El identificador PUC asociado no esta disponible o no existe");
            }

            // Validacion: Nombre unico (sin company, mono-empresa)
            if (accountingAccountRepository.existsByCustomNameAndDeletedAtIsNull(request.getCustom_name())) {
                throw new IllegalArgumentException("Nombre ya registrado");
            }

            // Validación: Moneda existe
            CurrencyType currencyType = currencyTypeRepository.findById(request.getCurrency_type_id())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El tipo de moneda seleccionado no está disponible o no existe"));

            // Resolver centro de costos (opcional)
            CostCenter costCenter = null;
            if (request.getCost_center_id() != null) {
                costCenter = costCenterRepository.findById(request.getCost_center_id())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "El centro de costos seleccionado no está disponible o no existe"));
            }

            AccountingAccount accountingAccount = AccountingAccount.builder()
                    .pucAccount(ChartOfAccount.builder().id(request.getPuc_id()).build())
                    .customName(request.getCustom_name())
                    .currencyType(currencyType)
                    .costCenter(costCenter)
                    .taxRuleId(request.getTax_rule_id())
                    .nature(request.getNature())
                    .status(AccountStatus.ACTIVE)
                    .createdBy(userId)
                    .build();

            accountingAccountRepository.save(accountingAccount);

            // AUDITORÍA COMENTADA - NO IMPLEMENTAR AÚN
            // auditLogService.logCreation(accountingAccount, userId, "Cuentas Contables");

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Cuenta contable creada exitosamente"),
                            Optional.empty()));
        // } catch (Exception e) {
        //     return ResponseEntity.badRequest()
        //             .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    /**
     * CFG-RF-07: Editar cuenta contable
     */
    public ResponseEntity<?> updateAccountingAccount(UpdateAccountingAccountRequest request,
            BindingResult bindingResult, Long userId) {
        // try {
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // Validación: La cuenta existe y no está eliminada
            AccountingAccount accountingAccount = accountingAccountRepository
                    .findByIdAndDeletedAtIsNull(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La cuenta contable seleccionada no está disponible para edición"));

            // Validacion: Nombre unico excluyendo el actual (sin company, mono-empresa)
            if (accountingAccountRepository.existsByCustomNameAndIdNotAndDeletedAtIsNull(
                    request.getCustom_name(), request.getId())) {
                throw new IllegalArgumentException("Duplicidad del nombre de la cuenta");
            }

            // Validación: PUC existe
            if (!chartOfAccountRepository.findById(request.getPuc_id()).isPresent()) {
                throw new IllegalArgumentException("El identificador PUC asociado no está disponible o no existe");
            }

            // Validación: Moneda existe
            CurrencyType currencyType = currencyTypeRepository.findById(request.getCurrency_type_id())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El tipo de moneda seleccionado no está disponible o no existe"));

            // CFG-08: si se esta INACTIVANDO la cuenta (ACTIVE -> INACTIVE) y el saldo
            // neto de la cuenta en asientos POSTED es distinto de cero, rechazar.
            // No se pueden inactivar cuentas con saldo porque perderiamos trazabilidad
            // contable (Decreto 2649/1993 + NIIF).
            if (accountingAccount.getStatus() == AccountStatus.ACTIVE
                    && request.getStatus() == AccountStatus.INACTIVE) {
                java.math.BigDecimal saldo = journalEntryLineRepository
                        .netBalanceByAccountingAccountId(accountingAccount.getId());
                if (saldo == null) saldo = java.math.BigDecimal.ZERO;
                if (saldo.compareTo(java.math.BigDecimal.ZERO) != 0) {
                    throw new IllegalArgumentException(String.format(
                            "No se puede inactivar la cuenta contable porque tiene saldo $%s. "
                            + "Registre los asientos de cierre o reclasifique el saldo antes de inactivarla.",
                            saldo.toPlainString()));
                }
            }

            // Actualizar campos
            accountingAccount.setPucAccount(ChartOfAccount.builder().id(request.getPuc_id()).build());
            accountingAccount.setCustomName(request.getCustom_name());
            accountingAccount.setCurrencyType(currencyType);
            accountingAccount.setNature(request.getNature());
            accountingAccount.setStatus(request.getStatus());

            // Campos opcionales
            if (request.getCost_center_id() != null) {
                CostCenter costCenter = costCenterRepository.findById(request.getCost_center_id())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "El centro de costos seleccionado no está disponible o no existe"));
                accountingAccount.setCostCenter(costCenter);
            } else {
                accountingAccount.setCostCenter(null);
            }

            accountingAccount.setTaxRuleId(request.getTax_rule_id());

            accountingAccountRepository.save(accountingAccount);

            // AUDITORÍA COMENTADA - NO IMPLEMENTAR AÚN
            // auditLogService.logChanges(accountingAccount, request, userId, "Cuentas
            // Contables");

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La cuenta contable ha sido actualizada exitosamente"),
                            Optional.empty()));

        // } catch (IllegalArgumentException e) {
        //     return ResponseEntity.badRequest()
        //             .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // } catch (Exception e) {
        //     return ResponseEntity.badRequest().body(ErrorRespondJson
        //             .getErrorRespondMessage(Optional.of("Error al guardar la información, intente nuevamente")));
        // }
    }

    /**
     * CFG-RF-08: Eliminar (Inactivar) cuenta contable
     */
    public ResponseEntity<?> deleteAccountingAccount(Long id, String reason, Long userId) {
        try {
            // Validación: La cuenta existe y no está eliminada
            AccountingAccount accountingAccount = accountingAccountRepository.findByIdAndDeletedAtIsNull(id)
                    .orElseThrow(() -> new IllegalArgumentException("La cuenta contable seleccionada no existe"));

            // TODO: Validación de dependencias activas (pendiente módulo de transacciones)
            // CFG-RF-08 Excepción 2: Si tiene dependencias activas

            String dependency = hasActiveDependencies(accountingAccount.getId());

            if (dependency != null) {
                throw new IllegalArgumentException(dependency);
            }

            // Validación: Motivo de eliminación requerido
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("Debe especificar el motivo de eliminación");
            }

            // Borrado Lógico: Marcar como eliminada e inactiva
            accountingAccount.setDeletedAt(LocalDateTime.now());
            accountingAccount.setStatus(AccountStatus.INACTIVE);
            accountingAccountRepository.save(accountingAccount);

            // AUDITORÍA COMENTADA - NO IMPLEMENTAR AÚN
            // CFG-RF-08 Paso 8: Registro en auditoría
            // auditLogService.logDeletion(
            // "Cuentas Contables", // Tabla
            // accountingAccount.getId(), // Identificador del registro
            // userId, // Identificador del usuario
            // reason, // Motivo
            // "Listas contables" // Módulo origen
            // );

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La cuenta contable fue eliminada exitosamente"),
                            Optional.empty()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Error al registrar la eliminación. Intente nuevamente más tarde")));
        }
    }

    private String hasActiveDependencies(Long accountingAccountId) {
        List<DepretationRule> depretationRules = depretationRuleRepository.findByAccountingAccount_Id(accountingAccountId);
        if (!depretationRules.isEmpty()) {
            return "No se puede inactivar la cuenta contable, porque está vinculada a registros de depreciación activos. Retire las dependencias e intente de nuevo";
        }
        return null;
    }

    private ChartOfAccountResponseDTO getChartOfAccountResponseDTO(ChartOfAccount chartOfAccount) {

        ChartOfAccount chartOfAccountResponseDTO = chartOfAccountRepository.findById(chartOfAccount.getId()).orElse(null);
        if (chartOfAccountResponseDTO != null) {
            return ChartOfAccountResponseDTO.builder()
                    .id(chartOfAccountResponseDTO.getId())
                    .code(chartOfAccountResponseDTO.getCode())
                    .name(chartOfAccountResponseDTO.getName())
                    .build();
        }
        return null;
    }

    private CurrencyTypeResponseDTO getCurrencyTypeResponseDTO(CurrencyType currencyType) {
        CurrencyType currencyTypeResponseDTO = currencyTypeRepository.findById(currencyType.getId()).orElse(null);
        if (currencyTypeResponseDTO != null) {
            return CurrencyTypeResponseDTO.builder()
                    .id(currencyTypeResponseDTO.getId())
                    .isoCode(currencyTypeResponseDTO.getIsoCode())
                    .name(currencyTypeResponseDTO.getName())
                    .build();
        }
        return null;
    }

    private CostCenterDTO getCostCenterResponseDTO(CostCenter costCenter) {
        CostCenter costCenterResponseDTO = costCenterRepository.findById(costCenter.getId()).orElse(null);
        if (costCenterResponseDTO != null) {
            return CostCenterDTO.builder()
                    .id(costCenterResponseDTO.getId())
                    .code(costCenterResponseDTO.getCode())
                    .name(costCenterResponseDTO.getName())
                    .build();
        }
        return null;
    }

    private RuleTaxDTO convertRuleTaxToDTO(TaxRulerEntity taxRulerEntity) {
        return RuleTaxDTO.builder()
            .id(taxRulerEntity.getId())
            .name(taxRulerEntity.getName())
            .percentage(taxRulerEntity.getPercentage())
            .description(taxRulerEntity.getDescription())
            .scope(taxRulerEntity.getScope())
            .dateStart(taxRulerEntity.getDateStart())
            .dateEnd(taxRulerEntity.getDateEnd())
            .typeRulerTax(taxRulerEntity.getTypeRulerTax())
            .statusRulerTax(taxRulerEntity.getStatus())
            .build();
    }
}
