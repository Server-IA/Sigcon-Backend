package com.sigcon.backend.lists_accounting.ruler_tax.domain.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.application.AssignAccountingAccountToRulerTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.application.CreateRuleTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.application.RuleTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.application.UpdateRuleTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerAccount;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.StatusRulerTax;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.repository.RuleTaxRepository;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.repository.TaxRulerAccountRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RuleTaxService {

    private final RuleTaxRepository ruleTaxRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final TaxRulerAccountRepository taxRulerAccountRepository;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<TaxRulerEntity> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();
    
    /**
     * Crea una nueva regla tributaria (IVA o retencion) vinculada a una cuenta contable PUC.
     * Valida coherencia de fechas de vigencia y soporta campos UVT para retenciones con tope minimo.
     *
     * @param createRuleTaxDTO datos de la regla (nombre, porcentaje, tipo TAX/WITHHOLDING, vigencia, UVT)
     * @param bindingResult    resultado de validacion de campos obligatorios
     * @return ResponseEntity con la regla creada o errores de validacion
     */
    public ResponseEntity<?> create(CreateRuleTaxDTO createRuleTaxDTO, BindingResult bindingResult) {

        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        if(createRuleTaxDTO.getDateStart().isAfter(createRuleTaxDTO.getDateEnd())) {
            throw new RuntimeException("La fecha inicio no puede ser mayor a la fecha fin");
        }

        // HU-CFG-RF-09 E3: nombre debe ser unico en la empresa actual. Antes el
        // service permitia crear N reglas con el mismo nombre/tipo/tarifa, lo que
        // genera duplicados que rompen liquidaciones y reportes tributarios.
        if (createRuleTaxDTO.getName() != null
                && ruleTaxRepository.existsByNameAndDeletedAtIsNull(createRuleTaxDTO.getName().trim())) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Nombre ya registrado")));
        }

        AccountingAccount accountingAccount = accountingAccountRepository.findById(createRuleTaxDTO.getAccountingAccountId())
        .orElseThrow(() -> new RuntimeException("Cuenta contable no encontrada"));

        TaxRulerEntity taxRulerEntity = TaxRulerEntity.builder()
            .name(createRuleTaxDTO.getName())
            .percentage(createRuleTaxDTO.getPercentage())
            .description(createRuleTaxDTO.getDescription())
            .scope(createRuleTaxDTO.getScope())
            .dateStart(createRuleTaxDTO.getDateStart())
            .dateEnd(createRuleTaxDTO.getDateEnd())
            .typeRulerTax(createRuleTaxDTO.getTypeRulerTax())
            .accountingAccount(accountingAccount)
            .minAmountUvt(createRuleTaxDTO.getMinAmountUvt())
            .uvtValueYear(createRuleTaxDTO.getUvtValueYear())
            .status(StatusRulerTax.ACTIVE)
            .build();

            ruleTaxRepository.save(taxRulerEntity);
            auditPublisher.publishCreate(AuditModule.CFG, "RuleTax", taxRulerEntity.getId(), "RuleTax creado id=" + taxRulerEntity.getId());
        
        RuleTaxDTO ruleTaxDTO = convertToDTO(taxRulerEntity);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
            Optional.of("La Creacion ha sido exitosa"),
            Optional.of(ruleTaxDTO)
        ));

    }

    /**
     * Lista reglas tributarias con paginacion y filtros DataTable.
     * Incluye datos de la cuenta contable PUC asociada a cada regla.
     *
     * @param request parametros de paginacion, busqueda y orden del DataTable
     * @return ResponseEntity con DataTableResponse de reglas tributarias
     */
    public ResponseEntity<?> findAllPaged(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();

        int safeLength = length <= 0 ? 10 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
            ? Pageable.unpaged()
            : PageRequest.of(page, safeLength);

        Specification<TaxRulerEntity> spec = dataTableSpecificationBuilder.build(request);

        Page<TaxRulerEntity> taxRulerEntities = ruleTaxRepository.findAll(spec, pageable);

        System.out.println("request ruler tax: " + request);

        return ResponseEntity.ok(DataTableResponse.from(taxRulerEntities.map(this::convertToDTO), request.getDraw()));
    }

    /**
     * Actualiza una regla tributaria existente.
     * Valida coherencia de fechas y existencia de la cuenta contable asociada.
     * Permite cambiar nombre, porcentaje, alcance, vigencia, tipo, estado y valores UVT.
     *
     * @param id               ID de la regla tributaria a actualizar
     * @param updateRuleTaxDTO datos actualizados de la regla
     * @param bindingResult    resultado de validacion de campos obligatorios
     * @return ResponseEntity con la regla actualizada o errores de validacion
     */
    public ResponseEntity<?> updateRuleTax(Long id, UpdateRuleTaxDTO updateRuleTaxDTO, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        if(updateRuleTaxDTO.getDateStart().isAfter(updateRuleTaxDTO.getDateEnd())) {
            throw new RuntimeException("La fecha inicio no puede ser mayor a la fecha fin");
        }

        // HU-CFG-RF-11 E3: validar unicidad de nombre excluyendo el id actual.
        if (updateRuleTaxDTO.getName() != null
                && ruleTaxRepository.existsByNameAndIdNotAndDeletedAtIsNull(
                        updateRuleTaxDTO.getName().trim(), id)) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of("Nombre ya registrado")));
        }

        AccountingAccount accountingAccount = accountingAccountRepository.findById(updateRuleTaxDTO.getAccountingAccountId())
        .orElseThrow(() -> new RuntimeException("Cuenta contable no encontrada"));

        TaxRulerEntity taxRulerEntity = ruleTaxRepository.findById(id).orElseThrow(() -> new RuntimeException("Regla de impuesto no encontrada"));

        taxRulerEntity.setName(updateRuleTaxDTO.getName());
        taxRulerEntity.setPercentage(updateRuleTaxDTO.getPercentage());
        taxRulerEntity.setDescription(updateRuleTaxDTO.getDescription());
        taxRulerEntity.setScope(updateRuleTaxDTO.getScope());
        taxRulerEntity.setDateStart(updateRuleTaxDTO.getDateStart());
        taxRulerEntity.setDateEnd(updateRuleTaxDTO.getDateEnd());
        taxRulerEntity.setTypeRulerTax(updateRuleTaxDTO.getTypeRulerTax());
        taxRulerEntity.setStatus(updateRuleTaxDTO.getStatusRulerTax());
        taxRulerEntity.setAccountingAccount(accountingAccount);
        taxRulerEntity.setMinAmountUvt(updateRuleTaxDTO.getMinAmountUvt());
        taxRulerEntity.setUvtValueYear(updateRuleTaxDTO.getUvtValueYear());
        
        ruleTaxRepository.save(taxRulerEntity);
        auditPublisher.publishUpdate(AuditModule.CFG, "RuleTax", taxRulerEntity.getId(), "RuleTax actualizado id=" + taxRulerEntity.getId());

        RuleTaxDTO ruleTaxDTO = convertToDTO(taxRulerEntity);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
            Optional.of("El registro fue actualizado correctamente"),
            Optional.of(ruleTaxDTO)
        ));
    }

    /**
     * HU-CFG-RF-12: Eliminar regla tributaria (soft delete).
     *
     * Cumple los siguientes escenarios de la HU:
     *   E3: bloquea si hay cuentas contables que usan la regla (dep activa).
     *   E5: exige motivo (`reason`) >= 10 chars (antes el endpoint no lo pedia).
     *   E7: el motivo se persiste en la descripcion del audit log para trazabilidad.
     */
    public ResponseEntity<?> deleteRuleTax(Long id, String reason) {
        try {
            // HU-CFG-RF-12 E5: motivo obligatorio.
            if (reason == null || reason.trim().length() < 10) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                        "Debe especificar el motivo de eliminación (mínimo 10 caracteres)")));
            }

            TaxRulerEntity taxRulerEntity = ruleTaxRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Regla de impuesto no encontrada"));

            // HU-CFG-RF-12 E3: validar dependencias activas (cuentas que la usan).
            long usedBy = accountingAccountRepository.countByTaxRuleIdAndDeletedAtIsNull(id);
            if (usedBy > 0) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(
                        "No puede inactivarse la regla porque está vinculada a procesos vigentes "
                        + "(" + usedBy + " cuenta(s) contable(s) la usan).")));
            }

            // Soft delete en vez de eliminación física
            taxRulerEntity.setDeletedAt(java.time.LocalDateTime.now());
            taxRulerEntity.setStatus(com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.StatusRulerTax.INACTIVE);
            ruleTaxRepository.save(taxRulerEntity);
            // HU-CFG-RF-12 E7: motivo registrado en la descripcion del audit log.
            auditPublisher.publishDelete(AuditModule.CFG, "RuleTax", taxRulerEntity.getId(),
                "RuleTax eliminado id=" + taxRulerEntity.getId()
                + " | nombre=" + taxRulerEntity.getName()
                + " | motivo=" + reason.trim());

            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("La regla tributaria ha sido eliminada exitosamente"),
                Optional.empty()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * HU-CFG-RF-09 E8: exporta el listado completo (no paginado) en CSV o XLSX.
     * Respeta el tenant filter activo (multi-tenant). Incluye todos los campos
     * relevantes: nombre, tipo, tarifa, alcance, vigencia, cuenta contable, status.
     *
     * @param format "csv" o "xlsx"
     * @return bytes del archivo generado
     */
    public byte[] exportAll(String format) {
        java.util.List<TaxRulerEntity> all = ruleTaxRepository.findAll().stream()
                .filter(t -> t.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toList());
        java.util.List<String> headers = java.util.List.of(
                "Id", "Nombre", "Tipo", "Tarifa %", "Alcance",
                "Inicio vigencia", "Fin vigencia", "Cuenta contable", "Estado");
        java.util.List<java.util.function.Function<TaxRulerEntity, Object>> cols = java.util.List.of(
                TaxRulerEntity::getId,
                TaxRulerEntity::getName,
                t -> t.getTypeRulerTax() != null ? t.getTypeRulerTax().name() : "",
                TaxRulerEntity::getPercentage,
                TaxRulerEntity::getScope,
                TaxRulerEntity::getDateStart,
                TaxRulerEntity::getDateEnd,
                t -> t.getAccountingAccount() != null ? t.getAccountingAccount().getCustomName() : "",
                t -> t.getStatus() != null ? t.getStatus().name() : "");
        if ("xlsx".equalsIgnoreCase(format)) {
            return com.sigcon.backend.utils.export.SimpleTableExporter
                    .toXlsx("Reglas tributarias", headers, cols, all);
        }
        return com.sigcon.backend.utils.export.SimpleTableExporter
                .toCsv(headers, cols, all);
    }

    private RuleTaxDTO convertToDTO(TaxRulerEntity taxRulerEntity) {
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
            .minAmountUvt(taxRulerEntity.getMinAmountUvt())
            .uvtValueYear(taxRulerEntity.getUvtValueYear())
            .accountingAccount(AccountingAccountDTO.builder()
                .id(taxRulerEntity.getAccountingAccount().getId())
                .customName(taxRulerEntity.getAccountingAccount().getCustomName())
                .pucAccount(ChartOfAccountResponseDTO.builder()
                    .id(taxRulerEntity.getAccountingAccount().getPucAccount().getId())
                    .name(taxRulerEntity.getAccountingAccount().getPucAccount().getName())
                    .code(taxRulerEntity.getAccountingAccount().getPucAccount().getCode())
                    .build())
                .build())
            .build();
    }

    /**
     * Asigna cuentas contables adicionales a una regla tributaria (relacion muchos a muchos).
     * Elimina todas las asignaciones previas y reasigna segun la lista enviada (reemplazo completo).
     * Estas cuentas se usan para aplicar la regla en el motor tributario de facturas.
     *
     * @param taxDto        contiene el ID de la regla y los IDs de cuentas contables a asignar
     * @param bindingResult resultado de validacion de campos obligatorios
     * @return ResponseEntity con mensaje de exito o errores de validacion
     */
    @Transactional
    public ResponseEntity<?> assignAccountingAccountToRulerTax(AssignAccountingAccountToRulerTaxDTO taxDto, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        TaxRulerEntity taxRulerEntity = ruleTaxRepository.findById(taxDto.getRulerTaxId())
        .orElseThrow(() -> new RuntimeException("Regla de impuesto no encontrada"));

        // Que elimine todos y reasigna segun los seleccionados
        taxRulerAccountRepository.deleteAllByTaxRulerId(taxRulerEntity.getId());
        
        for(Long accountingAccountId : taxDto.getAccountingAccountIds()) {
            AccountingAccount accountingAccount = accountingAccountRepository.findById(accountingAccountId)
            .orElseThrow(() -> new RuntimeException("Cuenta contable no encontrada"));
            TaxRulerAccount taxRulerAccount = new TaxRulerAccount();
            taxRulerAccount.setTaxRuler(taxRulerEntity);
            taxRulerAccount.setAccountingAccount(accountingAccount);
            taxRulerAccountRepository.save(taxRulerAccount);
        }


        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
            Optional.of("Cuenta contable asignada correctamente"),
            Optional.empty()
        ));
    }

}
