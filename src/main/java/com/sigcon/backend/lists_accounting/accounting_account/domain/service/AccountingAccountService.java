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

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.repository.ChartOfAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountFilterRequest;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.application.CreateAccountingAccountRequest;
import com.sigcon.backend.lists_accounting.accounting_account.application.UpdateAccountingAccountRequest;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
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

    private final DataTableSpecificationBuilder<AccountingAccount> accountingAccountSpecificationBuilder =
        new DataTableSpecificationBuilder<>();

    /**
     * CFG-RF-06: Consultar cuentas contables existentes
     */
    public ResponseEntity<?> getAccountingAccounts(DataTableRequest request, AccountFilterRequest filters){
        try{
            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            // Validar rango de paginación [10, 20, 50, 100], default 10
            int safeLength = (length == 10 || length == 20 || length == 50 || length == 100) 
                ? length 
                : 10;
            
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            // Construir especificación con filtros + borrado lógico
            Specification<AccountingAccount> spec = accountingAccountSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            // Aplicar filtros personalizados si existen
            if (filters != null) {
                spec = spec.and((root, query, cb) -> {
                    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                    
                    if (filters.getCustom_name() != null && !filters.getCustom_name().isEmpty()) {
                        predicates.add(cb.like(cb.lower(root.get("customName")), 
                            "%" + filters.getCustom_name().toLowerCase() + "%"));
                    }
                    
                    if (filters.getBase_currency() != null && !filters.getBase_currency().isEmpty()) {
                        predicates.add(cb.equal(root.get("baseCurrency"), filters.getBase_currency()));
                    }
                    
                    if (filters.getNature() != null) {
                        predicates.add(cb.equal(root.get("nature"), filters.getNature()));
                    }
                    
                    if (filters.getStatus() != null) {
                        predicates.add(cb.equal(root.get("status"), filters.getStatus()));
                    }
                    
                    if (filters.getPuc_id() != null) {
                        predicates.add(cb.equal(root.get("puc").get("id"), filters.getPuc_id()));
                    }
                    
                    if (filters.getCost_center_id() != null) {
                        predicates.add(cb.equal(root.get("costCenter").get("id"), filters.getCost_center_id()));
                    }
                    
                    if (filters.getDepreciation_rule_id() != null) {
                        predicates.add(cb.equal(root.get("depreciationRule").get("id"), filters.getDepreciation_rule_id()));
                    }
                    
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                });
            }

            Page<AccountingAccount> accountingAccounts = accountingAccountRepository.findAll(spec, pageable);

            return ResponseEntity.ok(
                DataTableResponse.from(accountingAccounts.map(accountingAccount -> 
                    AccountingAccountDTO.builder()
                        .id(accountingAccount.getId())
                        .puc_id(accountingAccount.getPuc().getId())
                        .puc_code(accountingAccount.getPuc().getCode())
                        .custom_name(accountingAccount.getCustomName())
                        .base_currency(accountingAccount.getBaseCurrency())
                        .nature(accountingAccount.getNature())
                        .status(accountingAccount.getStatus())
                        .createdAt(accountingAccount.getCreatedAt())
                        .updatedAt(accountingAccount.getUpdatedAt())
                        .build()), request.getDraw())
            );

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al consultar datos, intente nuevamente.")));
        }
    }

    /**
     * CFG-RF-05: Crear cuenta contable
     */
    public ResponseEntity<?> createAccountingAccount(CreateAccountingAccountRequest request, BindingResult bindingResult, Long userId, Long companyId) {
        try{

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // Validación: PUC existe
            if (!chartOfAccountRepository.findById(request.getPuc_id()).isPresent()) {
                throw new IllegalArgumentException("El identificador PUC asociado no está disponible o no existe");
            }

            // Validación: Nombre único por empresa
            if (accountingAccountRepository.existsByCustomNameAndCompanyIdAndDeletedAtIsNull(request.getCustom_name(), companyId)) {
                throw new IllegalArgumentException("Nombre ya registrado");
            }

            // Validación: Moneda base válida (validación básica de enum)
            try {
                com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.BaseCurrency.valueOf(request.getBase_currency());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Moneda base de la cuenta no válida");
            }

            AccountingAccount accountingAccount = AccountingAccount.builder()
                .puc(ChartOfAccount.builder().id(request.getPuc_id()).build())
                .customName(request.getCustom_name())
                .baseCurrency(request.getBase_currency())
                .nature(request.getNature())
                .status(AccountStatus.ACTIVE)
                .companyId(companyId)
                .createdBy(userId)
                .build();

            AccountingAccount savedAccountingAccount = accountingAccountRepository.save(accountingAccount);

            // AUDITORÍA COMENTADA - NO IMPLEMENTAR AÚN
            // auditLogService.logCreation(savedAccountingAccount, userId, "Cuentas Contables");

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Cuenta contable creada exitosamente"), Optional.of(savedAccountingAccount)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * CFG-RF-07: Editar cuenta contable
     */
    public ResponseEntity<?> updateAccountingAccount(UpdateAccountingAccountRequest request, BindingResult bindingResult, Long userId){
        try{
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // Validación: La cuenta existe y no está eliminada
            AccountingAccount accountingAccount = accountingAccountRepository.findByIdAndDeletedAtIsNull(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("La cuenta contable seleccionada no está disponible para edición"));

            // Validación: Nombre único (excluyendo el actual)
            if (accountingAccountRepository.existsByCustomNameAndCompanyIdAndIdNotAndDeletedAtIsNull(
                request.getCustom_name(), accountingAccount.getCompanyId(), request.getId())) {
                throw new IllegalArgumentException("Duplicidad del nombre de la cuenta");
            }

            // Validación: PUC existe
            if (!chartOfAccountRepository.findById(request.getPuc_id()).isPresent()) {
                throw new IllegalArgumentException("El identificador PUC asociado no está disponible o no existe");
            }

            // Validación: Moneda base válida
            try {
                com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.BaseCurrency.valueOf(request.getBase_currency());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("La moneda seleccionada no está disponible");
            }

            // TODO: Validación de transacciones asociadas (pendiente)
            // Si la cuenta tiene transacciones, no permitir cambiar:
            // - Nombre personalizado
            // - Estado (no pasar a inactivo)
            // - PUC asociado
            // - Centro de costos
            // - Regla de depreciación
            // if (hasActiveTransactions(accountingAccount.getId())) {
            //     throw new IllegalArgumentException("No se puede modificar el campo, ya que la cuenta contable está asociada a transacciones registradas en el sistema.");
            // }

            // Actualizar campos
            accountingAccount.setPuc(ChartOfAccount.builder().id(request.getPuc_id()).build());
            accountingAccount.setCustomName(request.getCustom_name());
            accountingAccount.setBaseCurrency(request.getBase_currency());
            accountingAccount.setNature(request.getNature());
            accountingAccount.setStatus(request.getStatus());
            
            // Campos opcionales (stub - pendiente implementación real)
            if (request.getCost_center_id() != null) {
                // accountingAccount.setCostCenter(CostCenter.builder().id(request.getCost_center_id()).build());
            }
            if (request.getDepreciation_rule_id() != null) {
                // accountingAccount.setDepreciationRule(DepreciationRule.builder().id(request.getDepreciation_rule_id()).build());
            }

            AccountingAccount updatedAccountingAccount = accountingAccountRepository.save(accountingAccount);

            // AUDITORÍA COMENTADA - NO IMPLEMENTAR AÚN
            // auditLogService.logChanges(accountingAccount, request, userId, "Cuentas Contables");

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("La cuenta contable ha sido actualizada exitosamente"), Optional.of(updatedAccountingAccount)));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al guardar la información, intente nuevamente")));
        }
    }

    /**
     * CFG-RF-08: Eliminar (Inactivar) cuenta contable
     */
    public ResponseEntity<?> deleteAccountingAccount(Long id, String reason, Long userId){
        try{
            // Validación: La cuenta existe y no está eliminada
            AccountingAccount accountingAccount = accountingAccountRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta contable seleccionada no existe"));

            // TODO: Validación de dependencias activas (pendiente módulo de transacciones)
            // CFG-RF-08 Excepción 2: Si tiene dependencias activas
            // if (hasActiveDependencies(accountingAccount.getId())) {
            //     throw new IllegalArgumentException("No se puede inactivar la cuenta contable, porque está vinculada a registros activos. Retire las dependencias e intente de nuevo");
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
            //     "Cuentas Contables",           // Tabla
            //     accountingAccount.getId(),     // Identificador del registro
            //     userId,                        // Identificador del usuario
            //     reason,                        // Motivo
            //     "Listas contables"             // Módulo origen
            // );

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("La cuenta contable fue eliminada exitosamente"), 
                    Optional.empty()
                )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("Error al registrar la eliminación. Intente nuevamente más tarde")
                ));
        }
    }
}
