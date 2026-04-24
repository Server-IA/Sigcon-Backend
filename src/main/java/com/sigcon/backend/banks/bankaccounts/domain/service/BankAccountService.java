package com.sigcon.backend.banks.bankaccounts.domain.service;

import com.sigcon.backend.banks.bankaccounts.application.*;
import com.sigcon.backend.banks.financialmovements.application.UpdateLastReconciliationRequest;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus;
import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountType;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.banks.application.BankBranchDTO;
import com.sigcon.backend.banks.banks.application.BankDTO;
import com.sigcon.backend.banks.banks.domain.model.Bank;
import com.sigcon.backend.banks.banks.domain.model.BankBranch;
import com.sigcon.backend.banks.banks.domain.repository.BankBranchRepository;
import com.sigcon.backend.banks.banks.domain.repository.BankRepository;
import com.sigcon.backend.banks.checkbooks.domain.repository.CheckbookRepository;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.repository.ChartOfAccountRepository;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.cost_centers.domain.repository.CostCenterRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Servicio de gestion de cuentas bancarias.
 * <p>
 * Implementa las operaciones CRUD y cambios de estado para cuentas bancarias
 * segun las historias de usuario del modulo BNK. Incluye validaciones de negocio
 * como unicidad de codigo, restricciones de tipo de cuenta, transiciones de estado
 * irreversibles (cierre) y verificacion de dependencias antes de eliminar.
 * </p>
 *
 * @see com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount
 * @see com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {

    /** Tamano maximo de pagina para consultas DataTable (proteccion contra abuso). */
    private static final int MAX_PAGE_SIZE = 100;

    /** Cantidad de digitos visibles al enmascarar el numero de cuenta (los ultimos N). */
    private static final int MASK_VISIBLE_DIGITS = 4;

    private final BankAccountRepository bankAccountRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final CostCenterRepository costCenterRepository;
    private final CheckbookRepository checkbookRepository;
    private final AuditPublisher auditPublisher;

    private final UserUtil userUtil;

    private final DataTableSpecificationBuilder<BankAccount> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Crea una nueva cuenta bancaria validando reglas de negocio y datos obligatorios.
     * <p>
     * Validaciones aplicadas:
     * <ul>
     *   <li>La cuenta contable asociada debe ser de clase ACTIVO (normativa PUC colombiana)</li>
     *   <li>El banco y la moneda deben estar activos (no eliminados)</li>
     *   <li>Si se habilita sobregiro, el limite de credito debe ser positivo</li>
     *   <li>Las tarjetas de credito no pueden manejar chequera</li>
     *   <li>El saldo inicial no puede ser negativo ni la fecha de apertura futura</li>
     * </ul>
     * </p>
     *
     * @param request      datos de la cuenta bancaria a crear
     * @param bindingResult resultado de validacion de campos (@NotNull, @NotBlank, etc.)
     * @return ResponseEntity con la cuenta creada (BankAccountDTO) o error de validacion
     * @throws IllegalArgumentException si las entidades relacionadas no existen o estan inactivas
     */
    @Transactional
    public ResponseEntity<?> create(CreateBankAccountRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // Resolver entidades relacionadas obligatorias
        Bank bank = getBankOrThrow(request.getBankId());
        CurrencyType currencyType = getCurrencyTypeOrThrow(request.getCurrencyTypeId());
        AccountingAccount accountingAccount = getAccountingAccountOrThrow(request.getAccountingAccountId());
        User user = userUtil.getUser();

        // Validar que la cuenta contable sea de clase ACTIVO (segun PUC colombiano, cuentas bancarias son activo)
        validateAccountingAccountForBanks(accountingAccount);
        validateBankActive(bank);
        validateCurrencyActive(currencyType);

        // Sucursal y centro de costo son opcionales
        BankBranch bankBranch = request.getBankBranchId() != null ? getBankBranchOrThrow(request.getBankBranchId()) : null;
        CostCenter costCenter = request.getCostCenterId() != null ? getCostCenterOrThrow(request.getCostCenterId()) : null;

        // Regla de negocio: sobregiro requiere limite de credito positivo
        if (Boolean.TRUE.equals(request.getAllowsOverdraft()) && (request.getCreditLimit() == null || request.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0)) {
            return error("BNK-ERR-005", "Límite de crédito requerido cuando se activa sobregiro");
        }
        // Regla de negocio: alertas de saldo bajo requieren saldo minimo definido
        if (Boolean.TRUE.equals(request.getNotifyLowBalance()) && (request.getMinimumBalance() == null || request.getMinimumBalance().compareTo(BigDecimal.ZERO) < 0)) {
            return error("BNK-ERR-004", "Saldo mínimo requerido cuando se activan alertas de saldo bajo");
        }
        // Regla de negocio: tarjetas de credito no manejan chequera (instrumento incompatible)
        if (BankAccountType.TARJETA_CREDITO.equals(request.getAccountType()) && Boolean.TRUE.equals(request.getHandlesCheckbook())) {
            return error("BNK-ERR-006", "No se permite chequera para tarjetas de crédito");
        }
        if (request.getInitialBalance().compareTo(BigDecimal.ZERO) < 0) {
            return error("BNK-ERR-007", "Saldo inicial no puede ser negativo");
        }
        if (request.getOpeningDate() != null && request.getOpeningDate().isAfter(LocalDate.now())) {
            return error("BNK-ERR-008", "Fecha de apertura no puede ser futura");
        }

        BankAccount entity = BankAccount.builder()
                .code(request.getCode().trim())
                .accountNumber(request.getAccountNumber().trim())
                .accountName(request.getAccountName().trim())
                .accountType(request.getAccountType())
                .bank(bank)
                .currencyType(currencyType)
                .initialBalance(request.getInitialBalance())
                .accountingAccount(accountingAccount)
                .bankBranch(bankBranch)
                .accountExecutive(emptyToNull(request.getAccountExecutive()))
                // .bankPhone(emptyToNull(request.getBankPhone()))
                .description(emptyToNull(request.getDescription()))
                .openingDate(request.getOpeningDate())
                .allowsOverdraft(Boolean.TRUE.equals(request.getAllowsOverdraft()))
                .creditLimit(request.getCreditLimit())
                .notifyLowBalance(Boolean.TRUE.equals(request.getNotifyLowBalance()))
                .minimumBalance(request.getMinimumBalance())
                .handlesCheckbook(Boolean.TRUE.equals(request.getHandlesCheckbook()))
                .costCenter(costCenter)
                .bookId(request.getBookId())
                .status(BankAccountStatus.ACTIVA)
                .createdBy(getCurrentUserId())
                .build();

        bankAccountRepository.save(entity);
        auditPublisher.publishCreate(AuditModule.BNK, "BankAccount", entity.getId(), "BankAccount creado id=" + entity.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria creada exitosamente."),
                        Optional.of(toDto(entity))
                )
        );
    }

    /**
     * Consulta paginada de cuentas bancarias con filtros dinamicos (DataTable).
     * <p>
     * Excluye automaticamente registros con soft delete (deletedAt != null).
     * Soporta busqueda global y ordenamiento por columnas mapeadas.
     * </p>
     *
     * @param request parametros de paginacion, busqueda y ordenamiento del DataTable
     * @return ResponseEntity con DataTableResponse paginado de BankAccountDTO
     */
    public ResponseEntity<?> findAllPaged(DataTableRequest request) {
        DataTableRequest safeRequest = normalizeDataTableRequest(request);
        // validateDataTableRequest(safeRequest);

        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 20 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);

        Specification<BankAccount> spec = dataTableSpecificationBuilder.build(safeRequest)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<BankAccount> pageResult = bankAccountRepository.findAll(spec, pageable);

        DataTableResponse<BankAccountDTO> response = DataTableResponse.from(pageResult.map(this::toDto), safeRequest.getDraw());
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el detalle completo de una cuenta bancaria por su ID.
     *
     * @param id identificador de la cuenta bancaria
     * @return ResponseEntity con BankAccountDTO o error si no existe o fue eliminada
     */
    public ResponseEntity<?> getDetail(Long id) {
        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("BNK-ERR-029: Cuenta no encontrada.")));
        }
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Información detallada obtenida correctamente."),
                        Optional.of(toDto(account))
                )
        );
    }

    /**
     * Actualiza los datos editables de una cuenta bancaria existente.
     * <p>
     * Solo se pueden modificar campos no estructurales (nombre, ejecutivo, descripcion,
     * configuracion de sobregiro, saldo minimo y centro de costo). El codigo, numero de
     * cuenta, banco y moneda no se modifican despues de la creacion.
     * </p>
     *
     * @param id            identificador de la cuenta a actualizar
     * @param request       datos actualizados de la cuenta
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con la cuenta actualizada o error de validacion
     */
    @Transactional
    public ResponseEntity<?> update(Long id, UpdateBankAccountRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }

        // Mismas reglas de negocio que en creacion para sobregiro y alertas
        if (Boolean.TRUE.equals(request.getAllowsOverdraft()) && (request.getCreditLimit() == null || request.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0)) {
            return error("BNK-ERR-013", "Límite de crédito requerido cuando se activa sobregiro");
        }
        if (Boolean.TRUE.equals(request.getNotifyLowBalance()) && (request.getMinimumBalance() == null || request.getMinimumBalance().compareTo(BigDecimal.ZERO) < 0)) {
            return error("BNK-ERR-004", "Saldo mínimo requerido cuando se activan alertas de saldo bajo");
        }

        account.setAccountName(request.getAccountName().trim());
        account.setAccountExecutive(emptyToNull(request.getAccountExecutive()));
        // account.setBankPhone(emptyToNull(request.getBankPhone()));
        account.setDescription(emptyToNull(request.getDescription()));
        account.setAllowsOverdraft(Boolean.TRUE.equals(request.getAllowsOverdraft()));
        account.setCreditLimit(request.getCreditLimit());
        account.setNotifyLowBalance(Boolean.TRUE.equals(request.getNotifyLowBalance()));
        account.setMinimumBalance(request.getMinimumBalance());
        account.setUpdatedBy(getCurrentUserId());

        if (request.getCostCenterId() != null) {
            account.setCostCenter(getCostCenterOrThrow(request.getCostCenterId()));
        } else {
            account.setCostCenter(null);
        }

        bankAccountRepository.save(account);
        auditPublisher.publishUpdate(AuditModule.BNK, "BankAccount", account.getId(), "BankAccount actualizado id=" + account.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria actualizada exitosamente."),
                        Optional.of(toDto(account))
                )
        );
    }

    /**
     * Elimina (soft delete) una cuenta bancaria si no tiene dependencias activas.
     * <p>
     * Antes de eliminar, verifica que no existan chequeras asociadas. Si las hay,
     * se recomienda desactivar en lugar de eliminar. Requiere motivo obligatorio
     * de al menos 5 caracteres para trazabilidad.
     * </p>
     *
     * @param id     identificador de la cuenta a eliminar
     * @param motivo justificacion de la eliminacion (minimo 5 caracteres)
     * @return ResponseEntity con confirmacion de eliminacion o error por dependencias
     */
    @Transactional
    public ResponseEntity<?> delete(Long id, String motivo) {
        if (!StringUtils.hasText(motivo) || motivo.trim().length() < 5) {
            return error("BNK-ERR-018", "Motivo de eliminación/desactivación requerido (mínimo 5 caracteres)");
        }

        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }

        // Verificar dependencias: no se puede eliminar si tiene chequeras asociadas
        long chequerasCount = checkbookRepository.countByBankAccount_Id(id);
        if (chequerasCount > 0) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("BNK-ERR-017: No se puede eliminar: existen " + chequerasCount + " chequera(s) asociada(s). Se recomienda desactivar la cuenta."))
            );
        }

        account.setDeletedAt(LocalDateTime.now());
        bankAccountRepository.save(account);
        auditPublisher.publishDelete(AuditModule.BNK, "BankAccount", account.getId(), "BankAccount eliminado id=" + account.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria eliminada exitosamente."),
                        Optional.empty()
                )
        );
    }

    /**
     * Desactiva una cuenta bancaria cambiando su estado a INACTIVA.
     * <p>
     * A diferencia de la eliminacion, la desactivacion no requiere validar dependencias
     * y es reversible mediante un cambio de estado posterior.
     * </p>
     *
     * @param id     identificador de la cuenta a desactivar
     * @param motivo justificacion de la desactivacion (minimo 5 caracteres)
     * @return ResponseEntity con la cuenta desactivada o error de validacion
     */
    @Transactional
    public ResponseEntity<?> deactivate(Long id, String motivo) {
        if (!StringUtils.hasText(motivo) || motivo.trim().length() < 5) {
            return error("BNK-ERR-018", "Motivo de eliminación/desactivación requerido (mínimo 5 caracteres)");
        }

        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }

        account.setStatus(BankAccountStatus.INACTIVA);
        account.setUpdatedBy(getCurrentUserId());
        bankAccountRepository.save(account);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria desactivada exitosamente."),
                        Optional.of(toDto(account))
                )
        );
    }

    /**
     * Cambia el estado de una cuenta bancaria aplicando reglas de transicion.
     * <p>
     * Transiciones permitidas: ACTIVA ↔ INACTIVA, ACTIVA/INACTIVA → CERRADA.
     * El cierre es <b>irreversible</b>: una cuenta CERRADA no puede volver a otro estado.
     * Para cerrar se requiere: saldo cero, sin chequeras activas y fecha de cierre valida.
     * </p>
     *
     * @param id          identificador de la cuenta
     * @param newStatus   nuevo estado deseado
     * @param motivo      justificacion del cambio (obligatorio si no es reactivacion, minimo 10 caracteres)
     * @param closingDate fecha de cierre (obligatoria solo si newStatus es CERRADA)
     * @return ResponseEntity con la cuenta actualizada o error de validacion
     */
    @Transactional
    public ResponseEntity<?> changeStatus(Long id, BankAccountStatus newStatus, String motivo, LocalDate closingDate) {
        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }

        // El cierre es irreversible: una cuenta cerrada no puede cambiar de estado
        if (account.getStatus() == BankAccountStatus.CERRADA) {
            return error("BNK-ERR-028", "Una cuenta cerrada no puede cambiar de estado. El cierre es irreversible.");
        }

        if (newStatus != BankAccountStatus.ACTIVA && (!StringUtils.hasText(motivo) || motivo.trim().length() < 10)) {
            return error("BNK-ERR-027", "Motivo requerido para este cambio de estado (mínimo 10 caracteres)");
        }

        // Validaciones especificas para cierre de cuenta
        if (newStatus == BankAccountStatus.CERRADA) {
            if (closingDate == null) {
                return error("BNK-ERR-023", "Fecha de cierre inválida");
            }
            if (closingDate.isAfter(LocalDate.now())) {
                return error("BNK-ERR-023", "Fecha de cierre no puede ser futura");
            }
            // Regla contable: no se puede cerrar una cuenta con saldo pendiente
            if (account.getInitialBalance().compareTo(BigDecimal.ZERO) != 0) {
                return error("BNK-ERR-024", "No se puede cerrar cuenta con saldo diferente de cero");
            }
            // Verificar que no haya chequeras activas antes de cerrar
            long chequerasCount = checkbookRepository.countByBankAccount_Id(id);
            if (chequerasCount > 0) {
                return error("BNK-ERR-026", "No se puede cerrar cuenta con chequeras activas");
            }
            account.setClosingDate(closingDate);
        }

        account.setStatus(newStatus);
        account.setUpdatedBy(getCurrentUserId());
        bankAccountRepository.save(account);
        auditPublisher.publishUpdate(AuditModule.BNK, "BankAccount", account.getId(), "BankAccount actualizado id=" + account.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Estado de cuenta actualizado exitosamente."),
                        Optional.of(toDto(account))
                )
        );
    }

    /**
     * Actualiza la fecha de ultima conciliacion bancaria de una cuenta.
     * <p>
     * Esta fecha se usa como referencia para saber hasta cuando se han conciliado
     * los movimientos de la cuenta con los extractos bancarios.
     * </p>
     *
     * @param id            identificador de la cuenta bancaria
     * @param request       contiene la nueva fecha de ultima conciliacion
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con la cuenta actualizada o error si la fecha es futura
     * @throws IllegalArgumentException si la fecha de conciliacion es futura
     */
    @Transactional
    public ResponseEntity<?> updateLastReconciliationDate(Long id, UpdateLastReconciliationRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }
        if (request.getLastReconciliationDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de ultima conciliacion no puede ser futura.");
        }

        account.setLastReconciliationDate(request.getLastReconciliationDate());
        account.setUpdatedBy(getCurrentUserId());
        bankAccountRepository.save(account);
        auditPublisher.publishUpdate(AuditModule.BNK, "BankAccount", account.getId(), "BankAccount actualizado id=" + account.getId());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Fecha de ultima conciliacion actualizada."),
                        Optional.of(toDto(account))
                )
        );
    }

    // private void validateCreateBusinessRules(CreateBankAccountRequest request) {
    //     if (bankAccountRepository.existsByCompanyIdAndCodeAndDeletedAtIsNull(request.getCompanyId(), request.getCode().trim())) {
    //         throw new IllegalArgumentException("BNK-ERR-004: El código de cuenta ya existe para esta empresa.");
    //     }
    //     if (bankAccountRepository.existsByBankIdAndAccountNumberAndDeletedAtIsNull(request.getBankId(), request.getAccountNumber().trim())) {
    //         throw new IllegalArgumentException("BNK-ERR-001: Número de cuenta ya registrado en este banco.");
    //     }
    // }

    /**
     * Valida que la cuenta contable asociada sea de clase ACTIVO.
     * Segun el PUC colombiano, las cuentas bancarias solo pueden vincularse
     * a cuentas de la clase 1 (Activo).
     */
    private void validateAccountingAccountForBanks(AccountingAccount accountingAccount) {
        if (accountingAccount.getPucAccount().getAccountClass() != AccountClass.ASSET) {
            throw new IllegalArgumentException("BNK-ERR-002: Cuenta contable no válida para bancos (debe ser clase ACTIVO).");
        }
    }

    private void validateBankActive(Bank bank) {
        if (bank.getDeletedAt() != null) {
            throw new IllegalArgumentException("BNK-ERR-004: Banco no disponible.");
        }
    }

    private void validateCurrencyActive(CurrencyType currency) {
        if (currency.getDeletedAt() != null) {
            throw new IllegalArgumentException("BNK-ERR-003: Moneda no válida o inactiva.");
        }
    }

    /**
     * Enmascara el numero de cuenta dejando visibles solo los ultimos N digitos.
     * Ejemplo: "123456789" → "****6789". Proteccion basica de datos sensibles.
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()) return "****";
        if (accountNumber.length() <= MASK_VISIBLE_DIGITS) return "****" + accountNumber;
        return "****" + accountNumber.substring(accountNumber.length() - MASK_VISIBLE_DIGITS);
    }

    /**
     * Convierte una entidad BankAccount a su DTO de respuesta.
     * Incluye DTOs anidados para banco, sucursal, moneda, cuenta contable y centro de costo.
     */
    private BankAccountDTO toDto(BankAccount e) {
        // Flag para controlar si se enmascara el numero de cuenta (deshabilitado temporalmente)
        boolean used = false;

        return BankAccountDTO.builder()
                .id(e.getId())
                .code(e.getCode())
                .accountNumberMasked(used ? maskAccountNumber(e.getAccountNumber()) : e.getAccountNumber())
                .accountName(e.getAccountName())
                .accountType(e.getAccountType())
                .bankDTO(e.getBank() != null ? BankDTO.builder()
                    .id(e.getBank().getId())
                    .name(e.getBank().getName())
                    .build()
                    : null)
                .bankBranchDTO(e.getBankBranch() != null ? BankBranchDTO.builder()
                    .id(e.getBankBranch().getId())
                    .address(e.getBankBranch().getAddress())
                    .build()
                    : null)
                .currencyTypeDTO(e.getCurrencyType() != null ? CurrencyTypeResponseDTO.builder()
                    .id(e.getCurrencyType().getId())
                    .isoCode(e.getCurrencyType().getIsoCode())
                    .build()
                    : null)
                .initialBalance(e.getInitialBalance())
                .accountingAccountDTO(e.getAccountingAccount() != null ? AccountingAccountDTO.builder()
                    .id(e.getAccountingAccount().getId())
                    .customName(e.getAccountingAccount().getCustomName())
                    .build()
                    : null)
                .costCenterDTO(e.getCostCenter() != null ? CostCenterDTO.builder()
                    .id(e.getCostCenter().getId())
                    .name(e.getCostCenter().getName())
                    .build()
                    : null)
                .accountExecutive(e.getAccountExecutive() != null ? e.getAccountExecutive() : null)
                .status(e.getStatus())
                .openingDate(e.getOpeningDate())
                .lastReconciliationDate(e.getLastReconciliationDate())
                .createdAt(e.getCreatedAt())
                .deletedAt(e.getDeletedAt())
                .build();
    }

    // private BankAccountDetailDTO toDetailDto(BankAccount e) {
    //     return BankAccountDetailDTO.builder()
    //             .id(e.getId())
    //             .code(e.getCode())
    //             .accountNumberMasked(maskAccountNumber(e.getAccountNumber()))
    //             .accountName(e.getAccountName())
    //             .accountType(e.getAccountType())
    //             .bankId(e.getBank() != null ? e.getBank().getId() : null)
    //             .bankName(e.getBank() != null ? e.getBank().getName() : null)
    //             .currencyTypeId(e.getCurrencyType() != null ? e.getCurrencyType().getId() : null)
    //             .currencyCode(e.getCurrencyType() != null ? e.getCurrencyType().getIsoCode() : null)
    //             .initialBalance(e.getInitialBalance())
    //             .chartOfAccountId(e.getChartOfAccount() != null ? e.getChartOfAccount().getId() : null)
    //             .chartOfAccountCode(e.getChartOfAccount() != null ? e.getChartOfAccount().getCode() : null)
    //             .chartOfAccountName(e.getChartOfAccount() != null ? e.getChartOfAccount().getName() : null)
    //             .companyId(e.getCompany() != null ? e.getCompany().getId() : null)
    //             .companyName(e.getCompany() != null ? e.getCompany().getName() : null)
    //             .status(e.getStatus())
    //             .openingDate(e.getOpeningDate())
    //             .createdAt(e.getCreatedAt())
    //             .updatedAt(e.getUpdatedAt())
    //             .deletedAt(e.getDeletedAt())
    //             .bankBranchId(e.getBankBranch() != null ? e.getBankBranch().getId() : null)
    //             .branchName(e.getBranchName())
    //             .accountExecutive(e.getAccountExecutive())
    //             .bankPhone(e.getBankPhone())
    //             .description(e.getDescription())
    //             .allowsOverdraft(e.getAllowsOverdraft())
    //             .creditLimit(e.getCreditLimit())
    //             .notifyLowBalance(e.getNotifyLowBalance())
    //             .minimumBalance(e.getMinimumBalance())
    //             .handlesCheckbook(e.getHandlesCheckbook())
    //             .costCenterId(e.getCostCenter() != null ? e.getCostCenter().getId() : null)
    //             .bookId(e.getBookId())
    //             .closingDate(e.getClosingDate())
    //             .build();
    // }

    private Bank getBankOrThrow(Long id) {
        return bankRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-004: Banco no encontrado."));
    }

    private BankBranch getBankBranchOrThrow(Long id) {
        return bankBranchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada."));
    }

    private AccountingAccount getAccountingAccountOrThrow(Long id) {
        return accountingAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-002: Cuenta contable no encontrada."));
    }

    private CurrencyType getCurrencyTypeOrThrow(Long id) {
        return currencyTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-003: Moneda no encontrada."));
    }

    private CostCenter getCostCenterOrThrow(Long id) {
        return costCenterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Centro de costo no encontrado."));
    }

    private BankAccount getBankAccountOrThrow(Long id) {
        return bankAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-029: Cuenta no encontrada."));
    }

    private String emptyToNull(String v) {
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.sigcon.backend.parametrization.users.domain.model.User user) {
                return user.getId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ResponseEntity<?> error(String code, String message) {
        return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(code + ": " + message))
        );
    }

    private DataTableRequest normalizeDataTableRequest(DataTableRequest request) {
        DataTableRequest safe = request != null ? request : new DataTableRequest();
        if (safe.getLength() == 0) safe.setLength(20);
        if (safe.getColumns() == null) safe.setColumns(new ArrayList<>());
        if (safe.getSearch() == null) safe.setSearch(new DataTableRequest.DataTableSearch("", false));

        List<DataTableRequest.DataTableColumn> normalized = safe.getColumns().stream()
                .map(col -> {
                    if (col != null && StringUtils.hasText(col.getData())) {
                        col.setData(mapDataTableColumn(col.getData().trim()));
                    }
                    return col;
                })
                .toList();
        safe.setColumns(normalized);

        // Si hay búsqueda global pero sin columnas definidas, agregar columnas por defecto para buscar
        if (StringUtils.hasText(safe.getSearch().getValue()) && safe.getColumns().stream()
                .allMatch(c -> c == null || !StringUtils.hasText(c.getData()))) {
            safe.setColumns(getDefaultSearchableColumns());
        }

        return safe;
    }

    private List<DataTableRequest.DataTableColumn> getDefaultSearchableColumns() {
        List<String> defaultFields = List.of("code", "accountNumber", "accountName", "bank.name", "company.name",
                "chartOfAccount.code", "chartOfAccount.name", "currencyType.isoCode", "status");
        return defaultFields.stream()
                .map(field -> {
                    DataTableRequest.DataTableColumn col = new DataTableRequest.DataTableColumn();
                    col.setData(field);
                    col.setSearchable(true);
                    col.setOrderable(true);
                    col.setSearch(new DataTableRequest.DataTableSearch("", false));
                    return col;
                })
                .toList();
    }

    private String mapDataTableColumn(String col) {
        return switch (col) {
            case "accountNumberMasked" -> "accountNumber";
            case "bankName" -> "bank.name";
            case "bankId" -> "bank.id";
            case "currencyCode" -> "currencyType.isoCode";
            case "currencyTypeId" -> "currencyType.id";
            case "chartOfAccountCode" -> "chartOfAccount.code";
            case "chartOfAccountName" -> "chartOfAccount.name";
            case "chartOfAccountId" -> "chartOfAccount.id";
            case "companyName" -> "company.name";
            case "companyId" -> "company.id";
            case "branchName" -> "branchName";
            case "accountExecutive" -> "accountExecutive";
            case "bankPhone" -> "bankPhone";
            case "description" -> "description";
            default -> col;
        };
    }

    private void validateDataTableRequest(DataTableRequest request) {
        if (request.getLength() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("BNK-ERR-033: Tamaño de página no válido (máximo 100).");
        }
        Set<String> allowedFields = Set.of(
                "id", "code", "accountNumber", "accountName", "accountType",
                "bank.id", "bank.name", "currencyType.id", "currencyType.isoCode",
                "chartOfAccount.id", "chartOfAccount.code", "chartOfAccount.name",
                "company.id", "company.name", "status", "openingDate", "createdAt", "updatedAt",
                "branchName", "accountExecutive", "bankPhone", "description", "initialBalance"
        );
        for (DataTableRequest.DataTableColumn col : request.getColumns()) {
            if (col == null || col.getData() == null || col.getData().isBlank()) {
                continue;
            }
            if (!allowedFields.contains(col.getData())) {
                throw new IllegalArgumentException("BNK-ERR-030: Campo de ordenamiento no válido: " + col.getData());
            }
        }
    }
}
