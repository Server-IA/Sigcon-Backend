package com.sigcon.backend.banks.cash_management.domain.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;


import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.banks.cash_management.application.AuditStub;
import com.sigcon.backend.banks.cash_management.application.CashResponse;
import com.sigcon.backend.banks.cash_management.application.ChangeCashStatusRequest;
import com.sigcon.backend.banks.cash_management.application.CreateCashRequest;
import com.sigcon.backend.banks.cash_management.application.FinancialMovementsStub;
import com.sigcon.backend.banks.cash_management.application.UpdateCashRequest;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashStatus;
import com.sigcon.backend.banks.cash_management.domain.repository.CashRepository;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.cost_centers.domain.repository.CostCenterRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CashService {

     private final CashRepository cashRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final CostCenterRepository costCenterRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final FinancialMovementsStub financialMovementsStub;
    private final AuditStub auditStub;

    /*
     * BNK-RF-10 — Flujo crear: Registrar una nueva caja de efectivo.
     */
    public ResponseEntity<?> createCash(CreateCashRequest request, BindingResult bindingResult) {

        // 1. Validar errores de bean validation
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // 2. Validar unicidad del código de caja (BNK-ERR-060)
        if (cashRepository.existsByCashCodeAndDeletedAtIsNull(request.getCashCode())) {
            throw new IllegalArgumentException(
                    "BNK-ERR-060: Código de caja duplicado.");
        }

        // 3. Resolver y validar moneda (BNK-ERR-065)
        CurrencyType currency = currencyTypeRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "BNK-ERR-065: Moneda inválida o inactiva."));

        // 4. Resolver y validar cuenta contable (BNK-ERR-064)
        AccountingAccount accountingAccount = accountingAccountRepository
                .findByIdAndDeletedAtIsNull(request.getAccountingAccountId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "BNK-ERR-064: Cuenta contable no válida para caja/libro."));

        // 5. Resolver y validar responsable principal (BNK-ERR-061)
        ThirdParty principalResponsible = resolveEmployee(request.getPrincipalResponsibleId());

        // 6. Resolver responsable suplente (opcional) (BNK-ERR-061)
        ThirdParty alternateResponsible = null;
        if (request.getAlternateResponsibleId() != null) {
            alternateResponsible = resolveEmployee(request.getAlternateResponsibleId());
        }

        // 7. Resolver centro de costos (opcional)
        CostCenter costCenter = null;
        if (request.getCostCenterId() != null) {
            costCenter = costCenterRepository.findByIdAndDeletedAtIsNull(request.getCostCenterId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El centro de costos seleccionado no existe o fue eliminado."));
        }

        // 8. Validar reglas de negocio de límites (BNK-ERR-062)
        validateLimits(request.getMaxLimit(), request.getMinLimit());

        // 9. Validar regla de autorización (BNK-ERR-068)
        validateAuthorization(request.getRequiresAuthorization(), request.getMaxAmountWithoutAuthorization());

        // 10. Construir y persistir la caja
        Cash cash = Cash.builder()
                .cashCode(request.getCashCode().trim())
                .cashName(request.getCashName().trim())
                .cashType(request.getCashType())
                .description(request.getDescription())
                .physicalLocation(request.getPhysicalLocation().trim())
                .principalResponsible(principalResponsible)
                .alternateResponsible(alternateResponsible)
                .operationSchedule(request.getOperationSchedule())
                .currency(currency)
                .initialBalance(request.getInitialBalanace())
                .currentBalance(request.getInitialBalanace())
                .initialBalanceDate(request.getInitialBalanceDay())
                .cashCreationDate(request.getCashCreationDate())
                .maxLimit(request.getMaxLimit())
                .minLimit(request.getMinLimit())
                .requiresAuthorization(request.getRequiresAuthorization())
                .maxAmountWithoutAuthorization(request.getMaxAmountWithoutAuthorization())
                .notifyLimit(request.getNotifyLimit())
                .auditFrequency(request.getAuditFrequency())
                .accountingAccount(accountingAccount)
                .costCenter(costCenter)
                .accountingBook(request.getAccountingBook())
                .build();

        cashRepository.save(cash);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Caja creada exitosamente."),
                        Optional.of(mapToResponse(cash))));
    }

    /*
     * BNK-RF-10 — Flujo editar: Actualizar una caja de efectivo existente.
     */
    public ResponseEntity<?> updateCash(Long id, UpdateCashRequest request, BindingResult bindingResult) {

        // 1. Validar errores de bean validation
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // 2. Verificar que la caja existe (BNK-ERR-074)
        Cash cash = getCashOrThrow(id);

        // 3. Validar unicidad del código excluyendo la caja actual (BNK-ERR-060)
        if (cashRepository.existsByCashCodeAndIdNotAndDeletedAtIsNull(request.getCashCode(), id)) {
            throw new IllegalArgumentException(
                    "BNK-ERR-060: Código de caja duplicado.");
        }

        // 4. Verificar si la caja tiene movimientos — cambios sensibles requieren motivo
        boolean hasMovements = financialMovementsStub.hasMovements(id);
        if (hasMovements) {
            if (request.getChangeReason() == null || request.getChangeReason().trim().length() < 10) {
                throw new IllegalArgumentException(
                        "BNK-ERR-070: Motivo de cambio requerido para esta modificación (mínimo 10 caracteres).");
            }
        }

        // 5. Resolver y validar moneda (BNK-ERR-065)
        CurrencyType currency = currencyTypeRepository.findById(request.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "BNK-ERR-065: Moneda inválida o inactiva."));

        // 6. Resolver y validar cuenta contable (BNK-ERR-064)
        AccountingAccount accountingAccount = accountingAccountRepository
                .findByIdAndDeletedAtIsNull(request.getAccountingAccountId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "BNK-ERR-064: Cuenta contable no válida para caja/libro."));

        // 7. Resolver y validar responsable principal (BNK-ERR-061)
        ThirdParty principalResponsible = resolveEmployee(request.getPrincipalResponsibleId());

        // 8. Resolver responsable suplente (opcional)
        ThirdParty alternateResponsible = null;
        if (request.getAlternateResponsibleId() != null) {
            alternateResponsible = resolveEmployee(request.getAlternateResponsibleId());
        }

        // 9. Resolver centro de costos (opcional)
        CostCenter costCenter = null;
        if (request.getCostCenterId() != null) {
            costCenter = costCenterRepository.findByIdAndDeletedAtIsNull(request.getCostCenterId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El centro de costos seleccionado no existe o fue eliminado."));
        }

        // 10. Validar reglas de negocio de límites (BNK-ERR-062)
        validateLimits(request.getMaxLimit(), request.getMinLimit());

        // 11. Validar regla de autorización (BNK-ERR-068)
        validateAuthorization(request.getRequiresAuthorization(), request.getMaxAmountWithoutAuthorization());

        // 12. Actualizar campos
        cash.setCashCode(request.getCashCode().trim());
        cash.setCashName(request.getCashName().trim());
        cash.setCashType(request.getCashType());
        cash.setDescription(request.getDescription());
        cash.setPhysicalLocation(request.getPhysicalLocation().trim());
        cash.setPrincipalResponsible(principalResponsible);
        cash.setAlternateResponsible(alternateResponsible);
        cash.setOperationSchedule(request.getOperationSchedule());
        cash.setCurrency(currency);
        cash.setInitialBalance(request.getInitialBalanace());
        cash.setInitialBalanceDate(request.getInitialBalanceDay());
        cash.setCashCreationDate(request.getCashCreationDate());
        cash.setMaxLimit(request.getMaxLimit());
        cash.setMinLimit(request.getMinLimit());
        cash.setRequiresAuthorization(request.getRequiresAuthorization());
        cash.setMaxAmountWithoutAuthorization(request.getMaxAmountWithoutAuthorization());
        cash.setNotifyLimit(request.getNotifyLimit());
        cash.setAuditFrequency(request.getAuditFrequency());
        cash.setAccountingAccount(accountingAccount);
        cash.setCostCenter(costCenter);
        cash.setAccountingBook(request.getAccountingBook());

        cashRepository.save(cash);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Caja actualizada exitosamente."),
                        Optional.of(mapToResponse(cash))));
    }

    /*
     * BNK-RF-11 — Eliminar físicamente o desactivar una caja de efectivo.
     */
    public ResponseEntity<?> deleteCash(Long id, String confirmation, String reason) {

        // 1. Verificar que la caja existe (BNK-ERR-074)
        Cash cash = getCashOrThrow(id);

        // 2. Validar motivo obligatorio (BNK-ERR-075)
        if (reason == null || reason.trim().length() < 40) {
            throw new IllegalArgumentException(
                    "BNK-ERR-075: Motivo de eliminación/desactivación requerido (mínimo 40 caracteres).");
        }

        // 3. Verificar dependencias con stubs
        boolean hasMovements = financialMovementsStub.hasMovements(id);
        boolean hasOpenAudits = auditStub.hasOpenAudits(id);
        long movementsCount = financialMovementsStub.countMovements(id);
        long auditsCount = auditStub.countAudits(id);

        // 4. Flujo alternativo: si hay dependencias, desactivar en lugar de eliminar
        if (hasMovements || hasOpenAudits) {
            cash.setCashStatus(CashStatus.INACTIVE);
            cashRepository.save(cash);

            String detail = String.format(
                    "%d movimientos registrados, %d arqueos asociados.",
                    movementsCount, auditsCount);

            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("Caja desactivada exitosamente. No se puede eliminar: " + detail),
                            Optional.of(mapToResponse(cash))));
        }

        // 5. Flujo principal: validar confirmación reforzada (BNK-ERR-076)
        if (!"ELIMINAR".equals(confirmation)) {
            throw new IllegalArgumentException(
                    "BNK-ERR-076: Confirmación reforzada fallida - palabra clave incorrecta.");
        }

        // 6. Eliminación física
        cashRepository.delete(cash);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Caja eliminada exitosamente."),
                        Optional.empty()));
    }

    /*
     * BNK-RF-12 — Cambiar estado de una caja de efectivo.
     */
    public ResponseEntity<?> changeCashStatus(Long id, ChangeCashStatusRequest request, BindingResult bindingResult) {

        // 1. Validar errores de bean validation
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // 2. Verificar que la caja existe
        Cash cash = getCashOrThrow(id);

        CashStatus currentStatus = cash.getCashStatus();
        CashStatus targetStatus = request.getStatus();

        // 3. Validar transición de estado permitida (BNK-ERR-079)
        validateStatusTransition(currentStatus, targetStatus);

        // 4. Validar motivo obligatorio para INACTIVE y CLOSED (BNK-ERR-083)
        if (targetStatus == CashStatus.INACTIVE || targetStatus == CashStatus.CLOSED) {
            if (request.getReason() == null || request.getReason().trim().length() < 10) {
                throw new IllegalArgumentException(
                        "BNK-ERR-083: Motivo requerido para este cambio de estado (mínimo 10 caracteres).");
            }
        }

        // 5. Validaciones específicas para cierre (BNK-ERR-081, BNK-ERR-082, BNK-ERR-080)
        if (targetStatus == CashStatus.CLOSED) {

            // 5a. Saldo debe ser cero
            if (cash.getCurrentBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                        "BNK-ERR-081: No se puede cerrar caja con saldo diferente de cero.");
            }

            // 5b. Sin arqueos abiertos
            if (auditStub.hasOpenAudits(id)) {
                throw new IllegalArgumentException(
                        "BNK-ERR-082: No se puede cerrar caja con arqueos abiertos.");
            }

            // 5c. Fecha de cierre obligatoria y no futura
            if (request.getClosingDate() == null) {
                throw new IllegalArgumentException(
                        "BNK-ERR-080: Fecha de cierre inválida. La fecha de cierre es obligatoria para cerrar una caja.");
            }
            if (request.getClosingDate().isAfter(LocalDate.now())) {
                throw new IllegalArgumentException(
                        "BNK-ERR-080: Fecha de cierre inválida. La fecha de cierre no puede ser futura.");
            }

            cash.setClosingDate(request.getClosingDate());
        }

        // 6. Aplicar nuevo estado
        cash.setCashStatus(targetStatus);
        cashRepository.save(cash);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Estado de caja actualizado exitosamente."),
                        Optional.of(mapToResponse(cash))));
    }

    /*
     * BNK-RF-13 — Consultar cajas con filtros y paginación.
     */
    public ResponseEntity<?> getCashes(DataTableRequest request) {

        if (request == null) {
            request = new DataTableRequest();
        }

        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 10 : length;
        int page = start / safeLength;

        org.springframework.data.domain.Pageable pageable = length == -1
                ? org.springframework.data.domain.Pageable.unpaged()
                : org.springframework.data.domain.PageRequest.of(page, safeLength);

        org.springframework.data.jpa.domain.Specification<Cash> spec = 
                new DataTableSpecificationBuilder<Cash>()
                .build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

       org.springframework.data.domain.Page<Cash> cashes = 
                   cashRepository.findAll(spec, pageable);

        return ResponseEntity.ok(
                DataTableResponse.from(
                        cashes.map(this::mapToResponse),
                        request.getDraw()));
    }

    /*
     * BNK-RF-13 — Consultar detalle de una caja por ID.
     */
    public ResponseEntity<?> getCashById(Long id) {

        Cash cash = getCashOrThrow(id);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Detalle de caja obtenido exitosamente."),
                        Optional.of(mapToResponse(cash))));
    }

    // =========================================================================
    // MÉTODOS PRIVADOS
    // =========================================================================

    /**
     * Buscar caja vigente por ID o lanzar excepción si no existe.
     */
    private Cash getCashOrThrow(Long id) {
        return cashRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "BNK-ERR-074: Caja no encontrada."));
    }

    /**
     * Validar que un ThirdParty existe y tiene rol EMPLEADO activo.
     * BNK-ERR-061: Responsable inexistente o inactivo.
     */
    private ThirdParty resolveEmployee(Long responsibleId) {
        ThirdParty thirdParty = thirdPartyRepository.findById(responsibleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "BNK-ERR-061: Responsable inexistente o inactivo."));

        boolean hasEmployeeRole = thirdParty.getRoles().stream()
                .anyMatch(role -> "EMPLEADO".equalsIgnoreCase(role.getName()));

        if (!hasEmployeeRole) {
            throw new IllegalArgumentException(
                    "BNK-ERR-061: Responsable inexistente o inactivo.");
        }

        return thirdParty;
    }

    /**
     * Validar reglas de límites máximo y mínimo.
     * BNK-ERR-062: Límites inconsistentes.
     */
    private void validateLimits(BigDecimal maxLimit, BigDecimal minLimit) {
        if (maxLimit != null && maxLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "BNK-ERR-062: Límites inconsistentes (máximo ≤ mínimo o valores negativos).");
        }
        if (minLimit != null && minLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "BNK-ERR-062: Límites inconsistentes (máximo ≤ mínimo o valores negativos).");
        }
        if (maxLimit != null && minLimit != null && maxLimit.compareTo(minLimit) <= 0) {
            throw new IllegalArgumentException(
                    "BNK-ERR-062: Límites inconsistentes (máximo ≤ mínimo o valores negativos).");
        }
    }

    /**
     * Validar regla de autorización.
     * BNK-ERR-068: Monto máximo sin autorización requerido cuando se activa autorización.
     */
    private void validateAuthorization(Boolean requiresAuthorization, BigDecimal maxAmountWithoutAuthorization) {
        if (Boolean.TRUE.equals(requiresAuthorization) && maxAmountWithoutAuthorization == null) {
            throw new IllegalArgumentException(
                    "BNK-ERR-068: Monto máximo sin autorización requerido cuando se activa autorización.");
        }
    }

    /**
     * Validar transiciones de estado permitidas según BNK-RF-12.
     * ACTIVE ↔ INACTIVE
     * ACTIVE/INACTIVE → CLOSED (irreversible)
     * BNK-ERR-079: Transición de estado no permitida.
     */
    private void validateStatusTransition(CashStatus current, CashStatus target) {
        if (current == target) {
            throw new IllegalArgumentException(
                    "BNK-ERR-078: Operación no permitida por estado actual.");
        }
        if (current == CashStatus.CLOSED) {
            throw new IllegalArgumentException(
                    "BNK-ERR-079: Transición de estado no permitida. Una caja cerrada no puede cambiar de estado.");
        }
    }

    /**
     * Mapear entidad Cash a CashResponse (detalle completo).
     */
    private CashResponse mapToResponse(Cash cash) {

        ThirdParty principalResponsible = cash.getPrincipalResponsible() != null
                ? thirdPartyRepository.findById(cash.getPrincipalResponsible().getId())
                        .orElse(null)
                : null;

        ThirdParty alternateResponsible = cash.getAlternateResponsible() != null
                ? thirdPartyRepository.findById(cash.getAlternateResponsible().getId())
                        .orElse(null)
                : null;

        AccountingAccount accountingAccount = cash.getAccountingAccount() != null
                ? accountingAccountRepository.findByIdAndDeletedAtIsNull(
                        cash.getAccountingAccount().getId())
                        .orElse(null)
                : null;

        CurrencyType currency = cash.getCurrency() != null
                ? currencyTypeRepository.findById(cash.getCurrency().getId())
                        .orElse(null)
                : null;

        CostCenter costCenter = cash.getCostCenter() != null
                ? costCenterRepository.findByIdAndDeletedAtIsNull(cash.getCostCenter().getId())
                        .orElse(null)
                : null;

        return CashResponse.builder()
                .id(cash.getId())
                .cashCode(cash.getCashCode())
                .cahsName(cash.getCashName())
                .cashType(cash.getCashType())
                .cashStatus(cash.getCashStatus())
                .description(cash.getDescription())
                .physicalLocation(cash.getPhysicalLocation())
                .principalResponsibleId(principalResponsible != null
                        ? principalResponsible.getId() : null)
                .principalResponsible(principalResponsible != null
                        ? ThirdPartyDTO.builder()
                                .id(principalResponsible.getId())
                                .thirdPartyCode(principalResponsible.getThirdPartyCode())
                                .nit(principalResponsible.getNit())
                                .dv(principalResponsible.getDv())
                                .businessName(principalResponsible.getBusinessName())
                                .blockingReason(principalResponsible.getBlockingReason())
                                .creditLimit(principalResponsible.getCreditLimit())
                                .paymentTerms(principalResponsible.getPaymentTerms())
                                .marketSegment(principalResponsible.getMarketSegment())
                                .createdAt(principalResponsible.getCreatedAt())
                                .updatedAt(principalResponsible.getUpdatedAt())
                                .build()
                        : null)
                .alternateResponsibleId(alternateResponsible != null
                        ? alternateResponsible.getId() : null)
                .alternateResponsible(alternateResponsible != null
                        ? ThirdPartyDTO.builder()
                                .id(alternateResponsible.getId())
                                .thirdPartyCode(alternateResponsible.getThirdPartyCode())
                                .nit(alternateResponsible.getNit())
                                .dv(alternateResponsible.getDv())
                                .businessName(alternateResponsible.getBusinessName())
                                .blockingReason(alternateResponsible.getBlockingReason())
                                .creditLimit(alternateResponsible.getCreditLimit())
                                .paymentTerms(alternateResponsible.getPaymentTerms())
                                .marketSegment(alternateResponsible.getMarketSegment())
                                .createdAt(alternateResponsible.getCreatedAt())
                                .updatedAt(alternateResponsible.getUpdatedAt())
                                .build()
                        : null)
                .operationSchedule(cash.getOperationSchedule())
                .currencyId(currency != null ? currency.getId() : null)
                .currency(currency != null
                        ? CurrencyTypeResponseDTO.builder()
                                .id(currency.getId())
                                .isoCode(currency.getIsoCode())
                                .name(currency.getName())
                                .build()
                        : null)
                .initialBalanace(cash.getInitialBalance())
                .currentBalance(cash.getCurrentBalance())
                .initialBalanceDay(cash.getInitialBalanceDate())
                .cashCreationDate(cash.getCashCreationDate())
                .maxLimit(cash.getMaxLimit())
                .minLimit(cash.getMinLimit())
                .requiresAuthorization(cash.getRequiresAuthorization())
                .maxAmountWithoutAuthorization(cash.getMaxAmountWithoutAuthorization())
                .notifyLimit(cash.getNotifyLimit())
                .auditFrequency(cash.getAuditFrequency())
                .accountingAccountId(accountingAccount != null
                        ? accountingAccount.getId() : null)
                .accountingAccount(accountingAccount != null
                        ? AccountingAccountDTO.builder()
                                .id(accountingAccount.getId())
                                .customName(accountingAccount.getCustomName())
                                .nature(accountingAccount.getNature())
                                .status(accountingAccount.getStatus())
                                .build()
                        : null)
                .costCenterId(costCenter != null ? costCenter.getId() : null)
                .costCenter(costCenter != null
                        ? CostCenterDTO.builder()
                                .id(costCenter.getId())
                                .code(costCenter.getCode())
                                .name(costCenter.getName())
                                .build()
                        : null)
                .accountingBook(cash.getAccountingBook())
                .closingDate(cash.getClosingDate())
                .createdAt(cash.getCreatedAt())
                .updatedAt(cash.getUpdatedAt())
                .deletedAt(cash.getDeletedAt())
                .build();
    }
}
