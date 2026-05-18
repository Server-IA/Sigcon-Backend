package com.sigcon.backend.lists_accounting.depretation_rules.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.security.core.Authentication;

import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.depretation_rules.application.CreateDepretationRuleRequest;
import com.sigcon.backend.lists_accounting.depretation_rules.application.DepretationRuleResponse;
import com.sigcon.backend.lists_accounting.depretation_rules.application.DescriptionStructuredDTO;
import com.sigcon.backend.lists_accounting.depretation_rules.application.UpdateDepretationRuleRequest;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.repository.DepretationRuleRepository;
import com.sigcon.backend.lists_accounting.depretation_rules.exception.DuplicateDepretationRuleException;
// import com.sigcon.backend.lists_accounting.depretation_rules.exception.IllegalArgumentException;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;


import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@lombok.extern.slf4j.Slf4j
public class DepretationRuleService {

    private final DepretationRuleRepository depretationRuleRepository;
    // QA Bloque BJ (2026-05-17): header estandar empresa+usuario+rol+totales
    private final com.sigcon.backend.utils.export.ReportContextResolver reportContextResolver;
    private final AccountingAccountRepository accountingAccountRepository;
    private final com.sigcon.backend.assets.assets.domain.repository.AssetsRepository assetsRepository;
    private final com.sigcon.backend.general.accounting.closing.domain.service.ClosingLockService closingLockService;
    private final AuditPublisher auditPublisher;

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

            // 2. CFG-RF-13 E4: validar duplicados (metodo + cuenta + vigencia).
            //    Antes solo el UK de BD bloqueaba el INSERT, generando el mensaje
            //    confuso "la empresa tiene registros asociados". Ahora rechazamos
            //    explicitamente con un texto legible que coincide con la HU.
            boolean duplicate = depretationRuleRepository
                    .existsByDepretationTypeAndAccountingAccountIdAndEffectiveDate(
                            request.getDepretationType(),
                            request.getAccountingAccountId(),
                            request.getEffectiveDate());
            if (duplicate) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                        "Regla duplicada, ya existe una regla de depreciación con esos parámetros "
                        + "(método, cuenta contable y fecha de vigencia)")));
            }

            // HU-CFG-RF-13 E? (Bloque AP, 2026-05-04): nombre unico per-tenant.
            // Antes el seed V9-Z6 + creaciones manuales generaban filas con el mismo
            // nombre (ej. "OFICINA QA1" duplicada). El listado quedaba con multiples
            // filas con identico nombre, confundiendo al usuario y dificultando
            // la trazabilidad legal.
            if (request.getName() != null
                    && depretationRuleRepository.existsByNameAndDeletedAtIsNull(request.getName().trim())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                        "Ya existe una regla de depreciación con ese nombre. Ingrese uno diferente.")));
            }

            // 3. Validar vida útil según tipo de depreciación
            validateUsefulLifeByType(request.getDepretationType(), request.getUsefulLifeYears());

            // 4. Validar cuenta contable existe y está activa
            AccountingAccount accountingAccount = validateAccountingAccountExists(request.getAccountingAccountId());

            // 5. Obtener usuario del contexto (esto se utilizaria cuando se avance a auditoria)
            //Long userId = getAuthenticatedUserId();

            // 6. Mapear request a entity
            String description = buildDescription(request.getDescriptionStructured());

            DepretationRule rule = DepretationRule.builder()
                    .name(request.getName())
                    .depretationType(request.getDepretationType())
                    .accountingAccount(accountingAccount)
                    .depretationRate(request.getDepretationRate())
                    .usefulLifeYears(request.getUsefulLifeYears())
                    .residualValue(request.getResidualValue())
                    .effectiveDate(request.getEffectiveDate())
                    .descriptionStructured(description)
                    // .createdById(userId) // Para auditoría
                    .build();

            // 7. Guardar en BD
            DepretationRule savedRule = depretationRuleRepository.save(rule);
            auditPublisher.publishCreate(AuditModule.CFG, "DepretationRule", rule.getId(), "DepretationRule creado id=" + rule.getId());

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

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException tie) {
            throw tie;
        } catch (Exception e) {
            // HU-CFG-RF-13 E8: error tecnico no controlado → mensaje contextual.
            log.error("Error tecnico al crear regla de depreciacion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Sin Conexion, no se pudo conectar con el servidor despues de reintentar, verifique su conexion e intente nuevamente.")));
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
            throw new IllegalArgumentException(
                    "Vida útil no válida para el método seleccionado"
            );
        }

        // Validaciones específicas por tipo
        switch (depretationType) {
            case LINEAR:
                if (usefulLife > 50) {
                    throw new IllegalArgumentException(
                            "Para depreciación lineal, la vida útil debe ser <= 50 años"
                    );
                }
                break;
            case DECREASING: 
                if(usefulLife > 25 ) {
                    throw new IllegalArgumentException(
                            "Para depreciación decreciente, la vida útil debe ser <= 25 años"
                    );
                }
                break;
            case ACCELERATED:
            	if(usefulLife > 20) {
                    throw new IllegalArgumentException(
                            "Para depreciación acelerada, la vida útil debe ser <= 20 años"
                    );
                }
                break;
            case MINIMUN_USEFUL_LIFE:
                if (usefulLife > 10) {
                    throw new IllegalArgumentException(
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
    
    private AccountingAccount validateAccountingAccountExists(Long accountingAccountId) {
        return accountingAccountRepository.findByIdAndDeletedAtIsNull(accountingAccountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La cuenta contable no existe"
                ));
    }
    

    /**
     * Obtener usuario autenticado
     */
    
    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalArgumentException("Usuario no autenticado");
        }
        
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        return user.getId();
    }

    /**
     * Mapear DepretationRule a Response
     */
    private DepretationRuleResponse mapToResponse(DepretationRule rule) {

        return DepretationRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .depretationType(rule.getDepretationType())
                .accountingAccountId(rule.getAccountingAccount().getId())
                .accountingAccountDTO(AccountingAccountDTO.builder()
                        .id(rule.getAccountingAccount().getId())
                        .customName(rule.getAccountingAccount().getCustomName())
                        .nature(rule.getAccountingAccount().getNature())
                        .status(rule.getAccountingAccount().getStatus())
                        .build())
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

        //      if(rules.isEmpty()) {
        //         return ResponseEntity.ok(
        //             ErrorRespondJson.getErrorRespondMessage(
        //     Optional.of("No se encontraron reglas de depreciación con los filtros aplicados")
        //     )
        //     );
        //     }

            return ResponseEntity.ok(
                    com.sigcon.backend.utils.DataTableResponse.from(
                            rules.map(this::mapToResponse),
                            request.getDraw()
                    )
            );
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException tie) {
            throw tie;
        } catch (Exception e) {
            // HU-CFG-RF-14 E6: error tecnico al cargar datos
            log.error("Error tecnico al consultar reglas de depreciacion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Sin Conexion, no se pudo conectar con el servidor despues de reintentar, verifique su conexion e intente nuevamente.")));
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
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No se puede editar una regla eliminada."
                    ));
                    if(rule.getDeletedAt() != null) {
                        throw new IllegalArgumentException(
                                "No se puede editar una regla eliminada."
                        );
                    }

            // HU-CFG-RF-15 E5: bloqueo si existe cierre contable EN CURSO sobre la
            // cuenta contable asociada a la regla. ClosingLockService es un puente
            // hacia CG: hoy retorna siempre false (CG no tiene IN_PROGRESS aun).
            // Cuando CG implemente cierre por etapas, esta validacion se activa
            // automaticamente sin tocar este service. PENDIENTE A REVISAR en sprint CG.
            if (closingLockService.isClosingInProgressFor(
                    rule.getAccountingAccount() != null ? rule.getAccountingAccount().getId() : null,
                    rule.getEffectiveDate())) {
                throw new IllegalStateException(
                        "No se puede modificar una regla vinculada a un cierre contable en curso.");
            }

            // 3. Validar vida útil compatible con tipo
            validateUsefulLifeByType(request.getDepretationType(), request.getUsefulLifeYears());

            // HU-CFG-RF-15 E3: validar duplicidad post-update.
            // Si el usuario cambia el tipo de depreciacion a uno que ya existe
            // para la misma cuenta+vigencia, bloquear con mensaje literal HU.
            // accountingAccountId y effectiveDate son inmutables → comparar con los actuales.
            if (request.getDepretationType() != rule.getDepretationType()) {
                boolean dup = depretationRuleRepository
                        .existsByDepretationTypeAndAccountingAccountIdAndEffectiveDateAndIdNotAndDeletedAtIsNull(
                                request.getDepretationType(),
                                rule.getAccountingAccount().getId(),
                                rule.getEffectiveDate(),
                                rule.getId());
                if (dup) {
                    throw new IllegalArgumentException(
                            "Regla duplicada, ya existe una regla de depreciación con esos parámetros (método, cuenta contable y fecha de vigencia)."
                    );
                }
            }

            // 4. Validar que la tasa esté en rango (adicional)
            if (request.getDepretationRate().compareTo(java.math.BigDecimal.ZERO) < 0 ||
                request.getDepretationRate().compareTo(new java.math.BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                        "La tasa de depreciación está fuera del rango permitido."
                );
            }

            // HU-CFG-RF-15 E? (Bloque AP, 2026-05-04): nombre unico al editar
            // (excluyendo el id actual). Antes el listado podia quedar con
            // multiples reglas con el mismo nombre.
            if (request.getName() != null
                    && depretationRuleRepository.existsByNameAndIdNotAndDeletedAtIsNull(
                            request.getName().trim(), rule.getId())) {
                throw new IllegalArgumentException(
                        "Ya existe una regla de depreciación con ese nombre. Ingrese uno diferente.");
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
            auditPublisher.publishUpdate(AuditModule.CFG, "DepretationRule", rule.getId(), "DepretationRule actualizado id=" + rule.getId());

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
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException tie) {
            throw tie;
        } catch (Exception e) {
            // HU-CFG-RF-15 E8: error tecnico al guardar cambios
            log.error("Error tecnico al actualizar regla de depreciacion id={}", request.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Sin Conexion, no se pudo conectar con el servidor despues de reintentar, verifique su conexion e intente nuevamente.")));
        }
    }

    /**
     * CFG-RF-16: Eliminar regla de depreciación (eliminación lógica) (HU-16)
     */
    public ResponseEntity<?> deleteDepretationRule(Long id, String reason) {
        try {
            // 1. Verificar que la regla existe
            DepretationRule rule = depretationRuleRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(
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
                throw new IllegalArgumentException(
                        "La regla seleccionada ya fue eliminada o está inactiva."
                );
            }

            // HU-CFG-RF-16 E3: validar dependencias - activos en uso por la regla
            String dependency = hasActiveDependencies(rule.getId());
            if (dependency != null) {
                throw new IllegalArgumentException(dependency);
            }

            // 4. Marcarlo como eliminado
            rule.setDeletedAt(LocalDateTime.now());
            rule.setStatus(DepretationStatus.INACTIVE);
            depretationRuleRepository.save(rule);
            // HU-CFG-RF-16 E4: persistir motivo en descripcion del audit log
            auditPublisher.publishDelete(AuditModule.CFG, "DepretationRule", rule.getId(),
                    "DepretationRule eliminado id=" + rule.getId() + " | motivo=" + reason.trim());

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
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException tie) {
            throw tie;
        } catch (Exception e) {
            // HU-CFG-RF-16 E6: error tecnico al eliminar
            log.error("Error tecnico al eliminar regla de depreciacion id={}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(
                            "Sin Conexion, no se pudo conectar con el servidor despues de reintentar, verifique su conexion e intente nuevamente.")));
        }
    }

    /**
     * HU-CFG-RF-13 E7: exporta el listado completo (no paginado) de reglas
     * de depreciacion en CSV o XLSX. Respeta tenant filter.
     */
    public byte[] exportAll(String format) {
        java.util.List<DepretationRule> all = depretationRuleRepository.findAll().stream()
                .filter(r -> r.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toList());
        java.util.List<String> headers = java.util.List.of(
                "Id", "Nombre", "Tipo depreciacion", "Cuenta contable",
                "Tasa %", "Vida util (anios)", "Valor residual",
                "Vigencia desde", "Estado");
        java.util.List<java.util.function.Function<DepretationRule, Object>> cols = java.util.List.of(
                DepretationRule::getId,
                DepretationRule::getName,
                r -> r.getDepretationType() != null ? r.getDepretationType().name() : "",
                r -> r.getAccountingAccount() != null ? r.getAccountingAccount().getCustomName() : "",
                DepretationRule::getDepretationRate,
                DepretationRule::getUsefulLifeYears,
                DepretationRule::getResidualValue,
                DepretationRule::getEffectiveDate,
                r -> r.getStatus() != null ? r.getStatus().name() : "");
        if ("xlsx".equalsIgnoreCase(format)) {
            // QA Bloque BJ (2026-05-17): header estandar empresa+usuario+rol+totales
            com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                    .baseContext("Reglas de Depreciacion")
                    .addFilter("Total registros", String.valueOf(all.size()))
                    .build();
            return com.sigcon.backend.utils.export.SimpleTableExporter
                    .toXlsx("Reglas depreciacion", headers, cols, all, ctx);
        }
        com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Reglas de Depreciacion")
                .addFilter("Total registros", String.valueOf(all.size()))
                .build();
        return com.sigcon.backend.utils.export.SimpleTableExporter
                .toCsv(headers, cols, all, ctx);
    }

    /**
     * HU-CFG-RF-16 E3: valida que la regla de depreciacion NO tenga activos
     * en uso. Retorna null si esta libre, o el mensaje literal de la HU si
     * tiene activos asociados.
     *
     * @param depretationRuleId id de la regla a validar
     * @return null si no hay dependencias, mensaje de error si las hay
     */
    private String hasActiveDependencies(Long depretationRuleId) {
        long count = assetsRepository.countByDepretationRuleIdAndDeletedAtIsNull(depretationRuleId);
        if (count > 0) {
            return "No se puede eliminar una regla asociada a activos en uso.";
        }
        return null;
    }
}