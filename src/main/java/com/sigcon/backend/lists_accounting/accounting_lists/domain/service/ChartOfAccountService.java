package com.sigcon.backend.lists_accounting.accounting_lists.domain.service;

import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.CreateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.DeleteChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.UpdateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.repository.ChartOfAccountRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final DataTableSpecificationBuilder<ChartOfAccount> chartOfAccountSpecificationBuilder =
            new DataTableSpecificationBuilder<>();

    @Transactional
    public void createChartOfAccount(CreateChartOfAccountDTO request) {
        String code = normalizeCode(request.getCode());
        String name = normalizeName(request.getName());

        validateMandatoryCreateFields(request);
        validateAccountClass(request.getAccountClass());
        validateAccountLevel(request.getLevel());
        validateCodeByLevel(code, request.getLevel());

        AccountNature resolvedNature = validateAccountNature(request.getAccountClass(), request.getNature());

/*         if (chartOfAccountRepository.existsAnyByCode(code)) {
            throw new IllegalArgumentException("Codigo oficial ya registrado");
        }

        if (chartOfAccountRepository.existsAnyByName(name)) {
            throw new IllegalArgumentException("Nombre ya registrado");
        } */

        ChartOfAccount account = ChartOfAccount.builder()
                .code(code)
                .name(name)
                .accountClass(request.getAccountClass())
                .accountLevel(request.getLevel())
                .accountNature(resolvedNature)
                .build();

        chartOfAccountRepository.save(account);
    }

    public DataTableResponse<ChartOfAccountResponseDTO> searchChartOfAccounts(DataTableRequest request) {
        DataTableRequest safeRequest = normalizeDataTableRequest(request == null ? new DataTableRequest() : request);

        int draw = Math.max(0, safeRequest.getDraw());
        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 10 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<ChartOfAccount> spec = chartOfAccountSpecificationBuilder.build(safeRequest);

        Page<ChartOfAccount> result = chartOfAccountRepository.findAll(spec, pageable);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No existen cuentas con estos criterios");
        }

        return DataTableResponse.from(result.map(this::toResponseDTO), draw);
    }

    @Transactional
    public void updateChartOfAccount(UpdateChartOfAccountDTO request, Long id) {
        validateMandatoryUpdateFields(request);

        ChartOfAccount account = chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta PUC seleccionada no esta disponible para edicion"));

        String targetCode = normalizeCode(request.getCode());
        String targetName = normalizeName(request.getName());
/*         String currentCode = normalizeCode(account.getCode());
        String currentName = normalizeName(account.getName()); */

        validateAccountClass(request.getAccountClass());
        validateAccountLevel(request.getLevel());
        validateCodeByLevel(targetCode, request.getLevel());

        AccountNature resolvedNature = validateAccountNature(request.getAccountClass(), request.getNature());

/*         boolean hasActiveDependencies = hasActiveDependencies(account); */

/*         if (!targetCode.equals(account.getCode()) && hasActiveDependencies) {
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
        } */

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

        if (account.getDeletedAt() != null) {
            throw new IllegalStateException("La cuenta seleccionada del catalogo PUC no existe");
        }

        if (account.getStatus() != AccountStatus.INACTIVE) {
            throw new IllegalStateException("La cuenta esta activa, debe estar en estado inactiva para poder ser eliminada");
        }

/*         if (hasActiveDependencies(account)) {
            throw new IllegalStateException("No se puede inactivar la cuenta del catalogo PUC, porque esta vinculada a registros activos. Retire las dependencias e intente de nuevo");
        } */

        account.setDeletedAt(LocalDateTime.now());

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
        AccountLevel expectedLevelByCode = resolveLevelByLength(codeLength);

        if (expectedLevelByCode == null) {
            throw new IllegalArgumentException("Codigo invalido: la jerarquia PUC Colombiana solo permite 1, 2, 4 o 6 digitos");
        }

        if (expectedLevelByCode != level) {
            throw new IllegalArgumentException("Jerarquia de cuenta invalida: el codigo " + code
                    + " (" + codeLength + " digitos) corresponde al nivel " + levelToDisplay(expectedLevelByCode)
                    + " pero se recibio nivel " + levelToDisplay(level) + ".");
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

    private String levelToDisplay(AccountLevel level) {
        if (level == null) {
            return "Invalido";
        }

        return switch (level) {
            case CLASS -> "Clase";
            case GROUP -> "Grupo";
            case ACCOUNT -> "Cuenta";
            case SUBACCOUNT -> "Subcuenta";
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

/*     private boolean hasActiveDependencies(ChartOfAccount account) {
        String prefix = account.getCode();
        return chartOfAccountRepository.existsActiveChildrenByCodePrefix(prefix, account.getId());
    } */

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

    private DataTableRequest normalizeDataTableRequest(DataTableRequest request) {
        if (request.getColumns() == null) {
            request.setColumns(new ArrayList<>());
            return request;
        }

        List<DataTableRequest.DataTableColumn> normalizedColumns = request.getColumns().stream()
                .map(column -> {
                    if (column == null || !StringUtils.hasText(column.getData())) {
                        return column;
                    }
                    column.setData(mapDataTableColumn(column.getData().trim()));
                    return column;
                })
                .toList();

        request.setColumns(normalizedColumns);
        return request;
    }

    private String mapDataTableColumn(String columnName) {
        return switch (columnName) {
            case "level" -> "accountLevel";
            case "nature" -> "accountNature";
            default -> columnName;
        };
    }
}

