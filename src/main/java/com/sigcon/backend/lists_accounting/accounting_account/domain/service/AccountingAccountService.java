package com.sigcon.backend.lists_accounting.accounting_account.domain.service;

import java.time.LocalDateTime;
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

    private final DataTableSpecificationBuilder<AccountingAccount> accountingAccountSpecificationBuilder = new DataTableSpecificationBuilder<>();

    /**
     * CFG-RF-06: Consultar cuentas contables existentes
     */
    public ResponseEntity<?> getAccountingAccounts(DataTableRequest request) {
        try {
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
                            .puc_id(accountingAccount.getPuc().getId())
                            .pucAccount(ChartOfAccountResponseDTO.builder()
                                    .id(accountingAccount.getPuc().getId())
                                    .code(accountingAccount.getPuc().getCode())
                                    .name(accountingAccount.getPuc().getName())
                                    .build())
                            .custom_name(accountingAccount.getCustomName())
                            .currencyType(accountingAccount.getCurrencyType() != null
                                    ? CurrencyTypeResponseDTO.builder()
                                            .id(accountingAccount.getCurrencyType().getId())
                                            .isoCode(accountingAccount.getCurrencyType().getIsoCode())
                                            .name(accountingAccount.getCurrencyType().getName())
                                            .build()
                                    : null)
                            .costCenter(accountingAccount.getCostCenter() != null
                                    ? CostCenterDTO.builder()
                                            .id(accountingAccount.getCostCenter().getId())
                                            .code(accountingAccount.getCostCenter().getCode())
                                            .name(accountingAccount.getCostCenter().getName())
                                            .build()
                                    : null)
                            .depreciation_rule_id(accountingAccount.getDepreciationRuleId())
                            .nature(accountingAccount.getNature())
                            .status(accountingAccount.getStatus())
                            .createdAt(accountingAccount.getCreatedAt())
                            .updatedAt(accountingAccount.getUpdatedAt())
                            .build()), request.getDraw()));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson
                    .getErrorRespondMessage(Optional.of("Error al consultar datos, intente nuevamente.")));
        }
    }

    /**
     * CFG-RF-05: Crear cuenta contable
     */
    public ResponseEntity<?> createAccountingAccount(CreateAccountingAccountRequest request,
            BindingResult bindingResult, Long userId, Long companyId) {
        try {

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // Validación: PUC existe
            if (!chartOfAccountRepository.findById(request.getPuc_id()).isPresent()) {
                throw new IllegalArgumentException("El identificador PUC asociado no está disponible o no existe");
            }

            // Validación: Nombre único por empresa
            if (accountingAccountRepository.existsByCustomNameAndCompanyIdAndDeletedAtIsNull(request.getCustom_name(),
                    companyId)) {
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
                    .puc(ChartOfAccount.builder().id(request.getPuc_id()).build())
                    .customName(request.getCustom_name())
                    .currencyType(currencyType)
                    .costCenter(costCenter)
                    .depreciationRuleId(request.getDepreciation_rule_id())
                    .nature(request.getNature())
                    .status(AccountStatus.ACTIVE)
                    .companyId(companyId)
                    .createdBy(userId)
                    .build();

            accountingAccountRepository.save(accountingAccount);

            // AUDITORÍA COMENTADA - NO IMPLEMENTAR AÚN
            // auditLogService.logCreation(accountingAccount, userId, "Cuentas Contables");

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Cuenta contable creada exitosamente"),
                            Optional.empty()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * CFG-RF-07: Editar cuenta contable
     */
    public ResponseEntity<?> updateAccountingAccount(UpdateAccountingAccountRequest request,
            BindingResult bindingResult, Long userId) {
        try {
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // Validación: La cuenta existe y no está eliminada
            AccountingAccount accountingAccount = accountingAccountRepository
                    .findByIdAndDeletedAtIsNull(request.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "La cuenta contable seleccionada no está disponible para edición"));

            // Validación: Nombre único (excluyendo el actual)
            if (accountingAccountRepository.existsByCustomNameAndCompanyIdAndIdNotAndDeletedAtIsNull(
                    request.getCustom_name(), accountingAccount.getCompanyId(), request.getId())) {
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

            // Actualizar campos
            accountingAccount.setPuc(ChartOfAccount.builder().id(request.getPuc_id()).build());
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

            accountingAccount.setDepreciationRuleId(request.getDepreciation_rule_id());

            accountingAccountRepository.save(accountingAccount);

            // AUDITORÍA COMENTADA - NO IMPLEMENTAR AÚN
            // auditLogService.logChanges(accountingAccount, request, userId, "Cuentas
            // Contables");

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La cuenta contable ha sido actualizada exitosamente"),
                            Optional.empty()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson
                    .getErrorRespondMessage(Optional.of("Error al guardar la información, intente nuevamente")));
        }
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
            // if (hasActiveDependencies(accountingAccount.getId())) {
            // throw new IllegalArgumentException("No se puede inactivar la cuenta contable,
            // porque está vinculada a registros activos. Retire las dependencias e intente
            // de nuevo");
            // }

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
}
