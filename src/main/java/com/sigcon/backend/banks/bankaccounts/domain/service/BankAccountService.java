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
import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;

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

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MASK_VISIBLE_DIGITS = 4;

    private final BankAccountRepository bankAccountRepository;
    private final BankRepository bankRepository;
    private final BankBranchRepository bankBranchRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final CompanyRepository companyRepository;
    private final CostCenterRepository costCenterRepository;
    private final CheckbookRepository checkbookRepository;

    private final UserUtil userUtil;

    private final DataTableSpecificationBuilder<BankAccount> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

    @Transactional
    public ResponseEntity<?> create(CreateBankAccountRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        Bank bank = getBankOrThrow(request.getBankId());
        CurrencyType currencyType = getCurrencyTypeOrThrow(request.getCurrencyTypeId());
        AccountingAccount accountingAccount = getAccountingAccountOrThrow(request.getAccountingAccountId());
        User user = userUtil.getUser();

        validateAccountingAccountForBanks(accountingAccount);
        validateBankActive(bank);
        validateCurrencyActive(currencyType);

        BankBranch bankBranch = request.getBankBranchId() != null ? getBankBranchOrThrow(request.getBankBranchId()) : null;
        CostCenter costCenter = request.getCostCenterId() != null ? getCostCenterOrThrow(request.getCostCenterId()) : null;

        if (Boolean.TRUE.equals(request.getAllowsOverdraft()) && (request.getCreditLimit() == null || request.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0)) {
            return error("BNK-ERR-005", "Límite de crédito requerido cuando se activa sobregiro");
        }
        if (Boolean.TRUE.equals(request.getNotifyLowBalance()) && (request.getMinimumBalance() == null || request.getMinimumBalance().compareTo(BigDecimal.ZERO) < 0)) {
            return error("BNK-ERR-004", "Saldo mínimo requerido cuando se activan alertas de saldo bajo");
        }
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
                .company(user.getCompany())
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

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria creada exitosamente."),
                        Optional.of(toDto(entity))
                )
        );
    }

    public ResponseEntity<?> findAllPaged(DataTableRequest request) {
        DataTableRequest safeRequest = normalizeDataTableRequest(request);
        User user = userUtil.getUser();
        // validateDataTableRequest(safeRequest);

        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 20 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);

        Specification<BankAccount> spec = dataTableSpecificationBuilder.build(safeRequest)
                .and((root, query, cb) -> cb.equal(root.get("company"), user.getCompany()))
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<BankAccount> pageResult = bankAccountRepository.findAll(spec, pageable);

        if (pageResult.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("BNK-ERR-032: No se encontraron cuentas bancarias con los resultados con los criterios especificados.")));
        }

        DataTableResponse<BankAccountDTO> response = DataTableResponse.from(pageResult.map(this::toDto), safeRequest.getDraw());
        return ResponseEntity.ok(response);
    }

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

    @Transactional
    public ResponseEntity<?> update(Long id, UpdateBankAccountRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }

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

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria actualizada exitosamente."),
                        Optional.of(toDto(account))
                )
        );
    }

    @Transactional
    public ResponseEntity<?> delete(Long id, String motivo) {
        if (!StringUtils.hasText(motivo) || motivo.trim().length() < 5) {
            return error("BNK-ERR-018", "Motivo de eliminación/desactivación requerido (mínimo 5 caracteres)");
        }

        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }

        long chequerasCount = checkbookRepository.countByBankAccount_Id(id);
        if (chequerasCount > 0) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("BNK-ERR-017: No se puede eliminar: existen " + chequerasCount + " chequera(s) asociada(s). Se recomienda desactivar la cuenta."))
            );
        }

        account.setDeletedAt(LocalDateTime.now());
        bankAccountRepository.save(account);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cuenta bancaria eliminada exitosamente."),
                        Optional.empty()
                )
        );
    }

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

    @Transactional
    public ResponseEntity<?> changeStatus(Long id, BankAccountStatus newStatus, String motivo, LocalDate closingDate) {
        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }

        if (newStatus != BankAccountStatus.ACTIVA && (!StringUtils.hasText(motivo) || motivo.trim().length() < 10)) {
            return error("BNK-ERR-027", "Motivo requerido para este cambio de estado (mínimo 10 caracteres)");
        }

        if (newStatus == BankAccountStatus.CERRADA) {
            if (closingDate == null) {
                return error("BNK-ERR-023", "Fecha de cierre inválida");
            }
            if (closingDate.isAfter(LocalDate.now())) {
                return error("BNK-ERR-023", "Fecha de cierre no puede ser futura");
            }
            if (account.getInitialBalance().compareTo(BigDecimal.ZERO) != 0) {
                return error("BNK-ERR-024", "No se puede cerrar cuenta con saldo diferente de cero");
            }
            long chequerasCount = checkbookRepository.countByBankAccount_Id(id);
            if (chequerasCount > 0) {
                return error("BNK-ERR-026", "No se puede cerrar cuenta con chequeras activas");
            }
            account.setClosingDate(closingDate);
        }

        account.setStatus(newStatus);
        account.setUpdatedBy(getCurrentUserId());
        bankAccountRepository.save(account);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Estado de cuenta actualizado exitosamente."),
                        Optional.of(toDto(account))
                )
        );
    }

    @Transactional
    public ResponseEntity<?> updateLastReconciliationDate(Long id, UpdateLastReconciliationRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        User user = userUtil.getUser();
        BankAccount account = getBankAccountOrThrow(id);
        if (account.getDeletedAt() != null) {
            return error("BNK-ERR-029", "Cuenta no encontrada");
        }
        if (!account.getCompany().getId().equals(user.getCompany().getId())) {
            throw new IllegalArgumentException("No tiene acceso a esta cuenta bancaria.");
        }
        if (request.getLastReconciliationDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de ultima conciliacion no puede ser futura.");
        }

        account.setLastReconciliationDate(request.getLastReconciliationDate());
        account.setUpdatedBy(getCurrentUserId());
        bankAccountRepository.save(account);

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

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()) return "****";
        if (accountNumber.length() <= MASK_VISIBLE_DIGITS) return "****" + accountNumber;
        return "****" + accountNumber.substring(accountNumber.length() - MASK_VISIBLE_DIGITS);
    }

    private BankAccountDTO toDto(BankAccount e) {

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

    private Company getCompanyOrThrow(Long id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada."));
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
