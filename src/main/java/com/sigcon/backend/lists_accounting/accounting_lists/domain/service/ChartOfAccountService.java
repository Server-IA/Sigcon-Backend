package com.sigcon.backend.lists_accounting.accounting_lists.domain.service;

import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.CreateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.DeleteChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.UpdateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.ViewChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountDeleted;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.repository.ChartOfAccountRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartOfAccountService {

    private static final int DEFAULT_LENGTH = 10;
    private static final int MAX_UNPAGED_LENGTH = 1000;

    private final ChartOfAccountRepository chartOfAccountRepository;

    @Transactional
    public void createChartOfAccount(CreateChartOfAccountDTO request) {
        String code = normalizeCode(request.getCode());
        String name = normalizeName(request.getName());

        validateMandatoryCreateFields(request);
        validateAccountClass(request.getAccountClass());
        validateAccountLevel(request.getLevel());
        validateCodeByLevel(code, request.getLevel());

        AccountNature resolvedNature = validateAccountNature(request.getAccountClass(), request.getNature());

        if (chartOfAccountRepository.existsAnyByCode(code)) {
            throw new IllegalArgumentException("Codigo oficial ya registrado");
        }

        if (chartOfAccountRepository.existsAnyByName(name)) {
            throw new IllegalArgumentException("Nombre ya registrado");
        }

        ChartOfAccount account = ChartOfAccount.builder()
                .code(code)
                .name(name)
                .accountClass(request.getAccountClass())
                .accountLevel(request.getLevel())
                .accountNature(resolvedNature)
                .build();

        chartOfAccountRepository.save(account);
    }

    public Page<ChartOfAccountResponseDTO> searchChartOfAccounts(ViewChartOfAccountDTO request, Pageable pageable) {

        if (chartOfAccountRepository.count() == 0) {
            throw new IllegalStateException("No existen cuentas registradas en el catalogo PUC");
        }

        Page<ChartOfAccount> result = chartOfAccountRepository.searchChartOfAccounts(
                normalizeFilter(request.getCode()),
                normalizeFilter(request.getName()),
                request.getAccountClass(),
                request.getLevel(),
                request.getNature(),
                request.getStatus(),
                pageable
        );

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No existen cuentas con estos criterios");
        }

        return result.map(this::toResponseDTO);
    }

    public DataTableResponse<ChartOfAccountResponseDTO> searchChartOfAccounts(DataTableRequest request) {
        DataTableRequest safeRequest = request == null ? new DataTableRequest() : request;

        ViewChartOfAccountDTO filters = buildFiltersFromDataTable(safeRequest);

        int draw = Math.max(0, safeRequest.getDraw());
        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();

        if (length == -1) {
            List<ChartOfAccountResponseDTO> allData = chartOfAccountRepository.searchChartOfAccounts(
                            normalizeFilter(filters.getCode()),
                            normalizeFilter(filters.getName()),
                            filters.getAccountClass(),
                            filters.getLevel(),
                            filters.getNature(),
                            filters.getStatus(),
                            Pageable.unpaged()
                    ).stream()
                    .map(this::toResponseDTO)
                    .limit(MAX_UNPAGED_LENGTH)
                    .toList();

            if (allData.isEmpty()) {
                throw new IllegalArgumentException("No existen cuentas con estos criterios");
            }

            return DataTableResponse.from(allData, draw);
        }

        int safeLength = length > 0 ? length : DEFAULT_LENGTH;
        int page = start / safeLength;

        Page<ChartOfAccountResponseDTO> pageResult = searchChartOfAccounts(filters, PageRequest.of(page, safeLength));
        return DataTableResponse.from(pageResult, draw);
    }

    @Transactional
    public void updateChartOfAccount(UpdateChartOfAccountDTO request, Long id) {
        validateMandatoryUpdateFields(request);

        ChartOfAccount account = chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta PUC seleccionada no esta disponible para edicion"));

        String targetCode = normalizeCode(request.getCode());
        String targetName = normalizeName(request.getName());

        validateAccountClass(request.getAccountClass());
        validateAccountLevel(request.getLevel());
        validateCodeByLevel(targetCode, request.getLevel());

        AccountNature resolvedNature = validateAccountNature(request.getAccountClass(), request.getNature());

        boolean hasActiveDependencies = hasActiveDependencies(account);

        if (!targetCode.equals(account.getCode()) && hasActiveDependencies) {
            throw new IllegalStateException("No se puede modificar el campo, ya que la regla cuenta PUC esta asociada a transacciones registradas en el sistema.");
        }

        if (request.getStatus() == AccountStatus.INACTIVE && hasActiveDependencies) {
            throw new IllegalStateException("No se puede modificar el campo, ya que la regla cuenta PUC esta asociada a transacciones registradas en el sistema.");
        }

        if (chartOfAccountRepository.existsAnyByCodeAndIdNot(targetCode, id)) {
            throw new IllegalArgumentException("Codigo oficial ya registrado");
        }

        if (chartOfAccountRepository.existsAnyByNameAndIdNot(targetName, id)) {
            throw new IllegalArgumentException("Duplicidad del nombre de la cuenta");
        }

        account.setCode(targetCode);
        account.setName(targetName);
        account.setAccountClass(request.getAccountClass());
        account.setAccountLevel(request.getLevel());
        account.setAccountNature(resolvedNature);
        account.setStatus(request.getStatus());

        chartOfAccountRepository.save(account);
    }

    @Transactional
    public void deleteChartOfAccount(Long id, DeleteChartOfAccountDTO request) {
        ChartOfAccount account = chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta seleccionada del catalogo PUC no existe"));

        if (account.getIsDeleted() == AccountDeleted.DELETED) {
            throw new IllegalStateException("La cuenta seleccionada del catalogo PUC no existe");
        }

        if (account.getStatus() != AccountStatus.INACTIVE) {
            throw new IllegalStateException("La cuenta esta activa, debe estar en estado inactiva para poder ser eliminada");
        }

        if (hasActiveDependencies(account)) {
            throw new IllegalStateException("No se puede inactivar la cuenta del catalogo PUC, porque esta vinculada a registros activos. Retire las dependencias e intente de nuevo");
        }

        account.setIsDeleted(AccountDeleted.DELETED);
        account.setDeletedReason(request.getReason().trim());
        chartOfAccountRepository.save(account);
    }

    public AccountClass validateAccountClass(AccountClass accountClass) {
        if (accountClass == null) {
            throw new IllegalArgumentException("Clase de la cuenta no valida");
        }
        return accountClass;
    }

    public AccountNature validateAccountNature(AccountClass accountClass, AccountNature accountNature) {
        if (accountClass == null || accountNature == null) {
            throw new IllegalArgumentException("Naturaleza de la cuenta no valida");
        }

        if (!accountClass.matchesNature(accountNature)) {
            throw new IllegalStateException("La naturaleza de la cuenta es invalida");
        }

        return accountNature;
    }

    public void validateAccountLevel(AccountLevel accountLevel) {
        if (accountLevel == null) {
            throw new IllegalArgumentException("Jerarquia de la cuenta no valida, por favor seleccione una jerarquia valida");
        }
    }

    private void validateCodeByLevel(String code, AccountLevel level) {
        int codeLength = code.length();

        int expectedLength = switch (level) {
            case CLASS -> 1;
            case GROUP -> 2;
            case ACCOUNT -> 4;
            case SUBACCOUNT -> 6;
        };

        if (codeLength != expectedLength) {
            String expectedLevelByCode = levelToEnglish(resolveLevelByLength(codeLength));
            String receivedLevel = levelToEnglish(level);

            throw new IllegalArgumentException("Jerarquía de cuenta inválida: basado en el código " + code
                    + ", se esperaba nivel " + expectedLevelByCode
                    + " pero se recibió nivel " + receivedLevel
                    + " (" + codeLength + " dígitos; " + receivedLevel + " requiere " + expectedLength + " dígitos).");
        }
    }

    private AccountLevel resolveLevelByLength(int codeLength) {
        return switch (codeLength) {
            case 1 -> AccountLevel.CLASS;
            case 2 -> AccountLevel.GROUP;
            case 4 -> AccountLevel.ACCOUNT;
            case 6 -> AccountLevel.SUBACCOUNT;
            default -> null;
        };
    }

    private String levelToEnglish(AccountLevel level) {
        if (level == null) {
            return "Invalid";
        }

        return switch (level) {
            case CLASS -> "Class";
            case GROUP -> "Group";
            case ACCOUNT -> "Account";
            case SUBACCOUNT -> "Subaccount";
        };
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Por favor diligencie todos los campos obligatorios");
        }
        return code.trim();
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Por favor diligencie todos los campos obligatorios");
        }
        return name.trim();
    }

    private String normalizeFilter(String value) {
        return value == null ? null : value.trim();
    }

    private void validateMandatoryCreateFields(CreateChartOfAccountDTO request) {
        if (request.getAccountClass() == null || request.getLevel() == null || request.getNature() == null) {
            throw new IllegalArgumentException("Por favor diligencie todos los campos obligatorios");
        }
    }

    private void validateMandatoryUpdateFields(UpdateChartOfAccountDTO request) {
        if (!StringUtils.hasText(request.getCode())
                || !StringUtils.hasText(request.getName())
                || request.getAccountClass() == null
                || request.getLevel() == null
                || request.getNature() == null
                || request.getStatus() == null) {
            throw new IllegalArgumentException("No se puede actualizar la informacion. Por favor complete todos los campos obligatorios antes de continuar.");
        }
    }

    private boolean hasActiveDependencies(ChartOfAccount account) {
        String prefix = account.getCode();
        return chartOfAccountRepository.existsActiveChildrenByCodePrefix(prefix, account.getId());
    }

    private ChartOfAccountResponseDTO toResponseDTO(ChartOfAccount account) {
        return ChartOfAccountResponseDTO.builder()
                .id(account.getId())
                .code(account.getCode())
                .name(account.getName())
                .accountClass(account.getAccountClass())
                .level(account.getAccountLevel())
                .nature(account.getAccountNature())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .deletedAt(account.getDeletedAt())
                .build();
    }

    private ViewChartOfAccountDTO buildFiltersFromDataTable(DataTableRequest request) {
        ViewChartOfAccountDTO filters = new ViewChartOfAccountDTO();

        if (request.getColumns() != null) {
            request.getColumns().forEach(column -> {
                if (column == null || column.getSearch() == null || !StringUtils.hasText(column.getData())) {
                    return;
                }

                String value = column.getSearch().getValue();
                if (!StringUtils.hasText(value)) {
                    return;
                }

                String data = column.getData().trim();
                String normalizedValue = value.trim();

                switch (data) {
                    case "code" -> filters.setCode(normalizedValue);
                    case "name" -> filters.setName(normalizedValue);
                    case "accountClass" -> filters.setAccountClass(parseEnum(AccountClass.class, normalizedValue));
                    case "level" -> filters.setLevel(parseEnum(AccountLevel.class, normalizedValue));
                    case "nature" -> filters.setNature(parseEnum(AccountNature.class, normalizedValue));
                    case "status" -> filters.setStatus(parseEnum(AccountStatus.class, normalizedValue));
                    default -> {
                    }
                }
            });
        }

        if (request.getSearch() != null && StringUtils.hasText(request.getSearch().getValue())) {
            String global = request.getSearch().getValue().trim();
            if (!StringUtils.hasText(filters.getCode())) {
                filters.setCode(global);
            }
            if (!StringUtils.hasText(filters.getName())) {
                filters.setName(global);
            }
        }

        return filters;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String rawValue) {
        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Por favor siga el formato de los filtros");
        }
    }
}

