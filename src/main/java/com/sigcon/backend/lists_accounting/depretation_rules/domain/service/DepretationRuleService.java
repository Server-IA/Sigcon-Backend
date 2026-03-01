package com.sigcon.backend.lists_accounting.depretation_rules.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.security.core.Authentication;

import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.depretation_rules.application.CreateDepretationRuleRequest;
import com.sigcon.backend.lists_accounting.depretation_rules.application.DepretationRuleResponse;
import com.sigcon.backend.lists_accounting.depretation_rules.application.DescriptionStructuredDTO;
import com.sigcon.backend.lists_accounting.depretation_rules.application.UpdateDepretationRuleRequest;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.repository.DepretationRuleRepository;
import com.sigcon.backend.lists_accounting.depretation_rules.exception.DuplicateDepretationRuleException;
import com.sigcon.backend.lists_accounting.depretation_rules.exception.InvalidDepretationRuleException;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;


import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DepretationRuleService {

    private final DepretationRuleRepository depretationRuleRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final UserRepository userRepository;

    /**
     * CFG-RF-13: Crear nueva regla de depreciación
     */
    public ResponseEntity<?> createDepretationRule(
            CreateDepretationRuleRequest request,
            BindingResult bindingResult) {

        try {
            // 1. Validar errores de bean validation
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // 2. Validar duplicados (método + cuenta + vigencia)
            validateNoDuplicates(
                    request.getDepretationType(),
                    request.getAccountingAccountId(),
                    request.getEffectiveDate()
            );

            // 3. Validar vida útil según tipo de depreciación
            validateUsefulLifeByType(request.getDepretationType(), request.getUsefulLifeYears());

            // 4. Validar cuenta contable existe y está activa
            validateAccountingAccountExists(request.getAccountingAccountId());

            // 5. Obtener usuario del contexto (esto se utilizaria cuando se avance a auditoria)
            //Long userId = getAuthenticatedUserId();

            // 6. Mapear request a entity
            String description = buildDescription(request.getDescriptionStructured());

            DepretationRule rule = DepretationRule.builder()
                    .name(request.getName())
                    .depretationType(request.getDepretationType())
                    .status(DepretationStatus.ACTIVE) // por defecto al crear
                    .accountingAccountId(request.getAccountingAccountId())
                    .depretationRate(request.getDepretationRate())
                    .usefulLifeYears(request.getUsefulLifeYears())
                    .residualValue(request.getResidualValue())
                    .effectiveDate(request.getEffectiveDate())
                    .descriptionStructured(description)
                    // .createdById(userId) // Para auditoría
                    .build();

            // 7. Guardar en BD
            DepretationRule savedRule = depretationRuleRepository.save(rule);

            // 8. Registrar en auditoría (cuando se haga auditoria)
            /*
            auditService.registerAudit(
                "depretation_rules",
                savedRule.getId(),
                userId,
                "CREATE",
                "DEPRECIATION_RULES"
            );
            */

            // 9. Mapear entity a response
            DepretationRuleResponse response = mapToResponse(savedRule);

            // 10. Retornar respuesta exitosa
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("La regla de depreciación ha sido registrada exitosamente"),
                            Optional.of(response)
                    ));

        } catch (DuplicateDepretationRuleException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (InvalidDepretationRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al guardar la regla. Intente nuevamente")));
        }
    }

    /**
     * Construir descripción estructurada como texto largo
     */
     private String buildDescription(DescriptionStructuredDTO dto) {
                StringBuilder sb = new StringBuilder(); 
                sb.append("Base de calculo: ").append(dto.getCalculationBase());
                sb.append(" | Parametros: ").append(dto.getParameters());
                if(dto.getException() != null && !dto.getException().isBlank()){
                        sb.append(" | Exepciones: ").append(dto.getException()); 
                }
                sb.append(" | Norma aplicable: ").append(dto.getApplicableNorm()); 
                return sb.toString();
        }

        /**
     * Validar que NO exista duplicado: método + cuenta + vigencia
     */

    private void validateNoDuplicates(
            DepretationType depretationType,
            Long accountingAccountId,
            java.time.LocalDate effectiveDate) {

        boolean exists = depretationRuleRepository
                .existsByDepretationTypeAndAccountingAccountIdAndEffectiveDate(
                        depretationType,
                        accountingAccountId,
                        effectiveDate
                );

        if (exists) {
            throw new DuplicateDepretationRuleException(
                    "Regla duplicada, ya existe una regla con esos parámetros"
            );
        }
    }

    /**
     * Validar vida útil compatible con tipo de depreciación
     */
    private void validateUsefulLifeByType(DepretationType depretationType, Integer usefulLife) {
        if (usefulLife <= 0) {
            throw new InvalidDepretationRuleException(
                    "Vida útil no válida para el método seleccionado"
            );
        }

        // Validaciones específicas por tipo
        switch (depretationType) {
            case LINEAR:
                if (usefulLife > 50) {
                    throw new InvalidDepretationRuleException(
                            "Para depreciación lineal, la vida útil debe ser <= 50 años"
                    );
                }
                break;
            case DECREASING: 
                if(usefulLife > 25 ) {
                    throw new InvalidDepretationRuleException(
                            "Para depreciación decreciente, la vida útil debe ser <= 25 años"
                    );
                }
                break;
            case ACCELERATED:
            	if(usefulLife > 20) {
                    throw new InvalidDepretationRuleException(
                            "Para depreciación acelerada, la vida útil debe ser <= 20 años"
                    );
                }
                break;
            case MINIMUN_USEFUL_LIFE:
                if (usefulLife > 10) {
                    throw new InvalidDepretationRuleException(
                            "Para vida útil mínima, la vida útil debe ser <= 10 años"
                    );
                }
                break;
            case PRODUCTION_UNITS:
                //no tiene un Limite de vida util fijo, depende de las unidades de produccion estimadas
                break;
        }
    }

    /**
     * Validar que cuenta contable existe y está activa
     */
    
    private void validateAccountingAccountExists(Long accountingAccountId) {
        AccountingAccount account = accountingAccountRepository.findByIdAndDeletedAtIsNull(accountingAccountId)
                .orElseThrow(() -> new InvalidDepretationRuleException(
                        "La cuenta contable no existe"
                ));

        if (!account.getStatus().equals(AccountStatus.ACTIVE)) {
            throw new InvalidDepretationRuleException(
                    "La cuenta contable debe estar activa"
            );
        }
    }
    

    /**
     * Obtener usuario autenticado
     */
    
    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new InvalidDepretationRuleException("Usuario no autenticado");
        }
        
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new InvalidDepretationRuleException("Usuario no encontrado"));
        
        return user.getId();
    }

    /**
     * Mapear DepretationRule a Response
     */
    private DepretationRuleResponse mapToResponse(DepretationRule rule) {

        AccountingAccount account = accountingAccountRepository
                .findByIdAndDeletedAtIsNull(rule.getAccountingAccountId())
                .orElse(null);
        
        AccountingAccountDTO accountDTO = account != null ? AccountingAccountDTO.builder()
                .id(account.getId())
                .custom_name(account.getCustomName())
                .base_currency(account.getBaseCurrency())
                .nature(account.getNature())
                .status(account.getStatus())
                .build(): null;

        return DepretationRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .depretationType(rule.getDepretationType())
                .accountingAccountId(rule.getAccountingAccountId())
                .accountingAccountDTO(accountDTO)
                .depretationRate(rule.getDepretationRate())
                .usefulLifeYears(rule.getUsefulLifeYears())
                .residualValue(rule.getResidualValue())
                .effectiveDate(rule.getEffectiveDate())
                .descriptionStructured(rule.getDescriptionStructured())
                .status(rule.getStatus())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .deletedAt(rule.getDeletedAt())
                // .createdById(rule.getCreatedById()) // Auditoría
                .build();
    }

    /**
     * CFG-RF-14: Consultar reglas de depreciación existentes (HU-14)
     */
    public ResponseEntity<?> getDepretationRulesPaged(com.sigcon.backend.utils.DataTableRequest request) {
        try {
                
            if(request == null) {
                request = new com.sigcon.backend.utils.DataTableRequest();
            }

            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                    ? Pageable.unpaged()
                    : PageRequest.of(page, safeLength);

            Specification<DepretationRule> spec = new com.sigcon.backend.utils.DataTableSpecificationBuilder<DepretationRule>()
                    .build(request)
                    .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

            Page<DepretationRule> rules = depretationRuleRepository.findAll(spec, pageable);

             if(rules.isEmpty()) {
                return ResponseEntity.ok(
                    ErrorRespondJson.getErrorRespondMessage(
            Optional.of("No se encontraron reglas de depreciación con los filtros aplicados")
            )
            );
            }

            return ResponseEntity.ok(
                    com.sigcon.backend.utils.DataTableResponse.from(
                            rules.map(this::mapToResponse),
                            request.getDraw()
                    )
            ); 

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * CFG-RF-15: Editar regla de depreciación existente (HU-15)
     */
    public ResponseEntity<?> updateDepretationRule(
            UpdateDepretationRuleRequest request,
            BindingResult bindingResult) {

        try {
            // 1. Validar errores de bean validation
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // 2. Verificar que la regla existe
            DepretationRule rule = depretationRuleRepository.findById(request.getId())
                    .orElseThrow(() -> new InvalidDepretationRuleException(
                            "No se puede editar una regla eliminada."
                    ));
                    if(rule.getDeletedAt() != null) {
                        throw new InvalidDepretationRuleException(
                                "No se puede editar una regla eliminada."
                        );
                    }

            // 3. Validar vida útil compatible con tipo
            validateUsefulLifeByType(request.getDepretationType(), request.getUsefulLifeYears());

            // 4. Validar que la tasa esté en rango (adicional)
            if (request.getDepretationRate().compareTo(java.math.BigDecimal.ZERO) < 0 ||
                request.getDepretationRate().compareTo(new java.math.BigDecimal("100")) > 0) {
                throw new InvalidDepretationRuleException(
                        "La tasa de depreciación está fuera del rango permitido."
                );
            }

            // 5. Actualizar campos editables
            rule.setName(request.getName());
            rule.setDepretationType(request.getDepretationType());
            rule.setDepretationRate(request.getDepretationRate());
            rule.setUsefulLifeYears(request.getUsefulLifeYears());
            rule.setResidualValue(request.getResidualValue());
            rule.setStatus(request.getStatus());

            // 6. Guardar cambios
            DepretationRule updatedRule = depretationRuleRepository.save(rule);

            // 7. Registrar en auditoría (comentado)
            /*
            auditService.registerAudit(
                "depretation_rules",
                "name, depretationType, depretationRate, usefulLifeYears, residualValue, status",
                updatedRule.getId(),
                "UPDATE",
                "DEPRECIATION_RULES"
            );
            */

            // 8. Mapear respuesta
            DepretationRuleResponse response = mapToResponse(updatedRule);

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Regla de depreciación actualizada exitosamente"),
                            Optional.of(response)
                    )
            );

        } catch (InvalidDepretationRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al guardar los cambios. Intente nuevamente")));
        }
    }

    /**
     * CFG-RF-16: Eliminar regla de depreciación (eliminación lógica) (HU-16)
     */
    public ResponseEntity<?> deleteDepretationRule(Long id, String reason) {
        try {
            // 1. Verificar que la regla existe
            DepretationRule rule = depretationRuleRepository.findById(id)
                    .orElseThrow(() -> new InvalidDepretationRuleException(
                            "La regla seleccionada no existe o está eliminada."
                    ));

            // 2. Validar que el motivo sea obligatorio
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("El motivo de eliminación es obligatorio.")));
            }

            // 3. Validar que no esté ya eliminada
            if (rule.getDeletedAt() != null) {
                throw new InvalidDepretationRuleException(
                        "La regla seleccionada ya fue eliminada o está inactiva."
                );
            }

            // 4. Marcarlo como eliminado 
            rule.setDeletedAt(LocalDateTime.now());
            rule.setStatus(DepretationStatus.INACTIVE); // Opcional: marcar como inactiva también
            depretationRuleRepository.save(rule);

            // 5. Registrar en auditoría (comentado por el momento)
            /*
            auditService.registerAudit(
                "depretation_rules",
                rule.getId(),
                userId,
                "DELETE",
                "DEPRECIATION_RULES",
                Optional.of(reason)
            );
            */

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Regla de depreciación eliminada correctamente"),
                            Optional.empty()
                    )
            );

        } catch (InvalidDepretationRuleException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al eliminar la regla. Intente nuevamente")));
        }
    }
}