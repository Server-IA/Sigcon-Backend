package com.sigcon.backend.assets.assets.domain.service;

import com.sigcon.backend.assets.assets.application.CreateAssetsDTO;
import com.sigcon.backend.assets.assets.application.UpdateAssetsDTO;
import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetClassification;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets.domain.repository.AssetChartOfAccountBridgeRepository;
import com.sigcon.backend.assets.assets.domain.repository.AssetThirdPartyBridgeRepository;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyRoleCatalog;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssetsService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ROLE_SUPERADMIN = "ROLE_SUPERADMIN";
    private static final String PERM_CREATE_ASSET = "PERM_CREATE_ASSET";
    private static final String PERM_UPDATE_ASSET = "PERM_UPDATE_ASSET";

    private static final Set<String> ALLOWED_DATA_TABLE_FIELDS = Set.of(
            "id",
            "assetCode",
            "assetName",
            "classification",
            "assetType",
            "chartOfAccount.code",
            "chartOfAccount.name",
            "supplier.id",
            "supplier.businessName",
            "acquisitionValue",
            "acquisitionDate",
            "status",
            "createdAt",
            "updatedAt"
    );

    private final AssetsRepository assetsRepository;
    private final AssetThirdPartyBridgeRepository thirdPartyRepository;
    private final AssetChartOfAccountBridgeRepository chartOfAccountRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final DataTableSpecificationBuilder<Assets> dataTableSpecificationBuilder =
            new DataTableSpecificationBuilder<>();

    @Transactional
    public ViewAssetsDTO create(CreateAssetsDTO request) {
        validateAssetPermission(PERM_CREATE_ASSET);
        validateAccountingPeriodIsOpen();

        ThirdParty supplier = resolveSupplier(request.getSupplierId());
        ChartOfAccount chartOfAccount = resolveChartOfAccount(request.getAccountingCode(), false);
        AccountingAccount accountingAccount = resolveAccountingAccount(request.getAccountingAccountId());

        validateAssetClassification(request.getClassification(), request.getUsefulLifeMonths());
        validateAccountsPayableDependency(
                request.getAccountsPayableReferenceId(),
                supplier.getId(),
                request.getPaymentTerms()
        );
        validateBankCashDependency(request.getBankCashReferenceId());

        String currentUser = resolveCurrentUsername();

        Assets asset = Assets.builder()
                .assetCode(generateAssetCode())
                .assetName(request.getName().trim())
                .description(normalizeOptionalText(request.getDescription()))
                .classification(request.getClassification())
                .assetType(request.getType())
                .chartOfAccount(chartOfAccount)
                .supplier(supplier)
                .acquisitionValue(request.getAcquisitionValue())
                .acquisitionDate(request.getAcquisitionDate())
                .usefulLifeMonths(request.getUsefulLifeMonths())
                .depreciationMethod(request.getDepreciationMethod())
                .paymentTerms(request.getPaymentTerms().trim())
                .accountsPayableReferenceId(request.getAccountsPayableReferenceId())
                .bankCashReferenceId(request.getBankCashReferenceId())
                .accountingAccount(accountingAccount)
                .status(request.getStatus())
                .observations(normalizeOptionalText(request.getObservations()))
                .createdBy(currentUser)
                .updatedBy(currentUser)
                .build();

        Assets savedAsset = assetsRepository.save(asset);
        return toViewDTO(savedAsset);
    }

    public DataTableResponse<ViewAssetsDTO> findAllPaged(DataTableRequest request) {
        DataTableRequest safeRequest = normalizeDataTableRequest(request);
        validateDataTableRequest(safeRequest);

        int draw = Math.max(0, safeRequest.getDraw());
        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 20 : length > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : length ;
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<Assets> specification = dataTableSpecificationBuilder.build(safeRequest);

        Page<Assets> assetsPage = assetsRepository.findAll(specification, pageable);
        if (assetsPage.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron activos con los criterios de busqueda especificados.");
        }

        return DataTableResponse.from(assetsPage.map(this::toViewDTO), draw);
    }

    public ViewAssetsDTO getById(Long id) {
        Assets asset = assetsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El activo seleccionado no existe."));
        return toViewDTO(asset);
    }

    @Transactional
    public ViewAssetsDTO update(Long id, UpdateAssetsDTO request) {
        validateAssetPermission(PERM_UPDATE_ASSET);
        validateAccountingPeriodIsOpen();

        Assets existingAsset = assetsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El activo seleccionado no existe."));

        if (existingAsset.getStatus() == AssetStatus.DECOMMISSIONED
                || existingAsset.getStatus() == AssetStatus.TRANSFERRED) {
            throw new IllegalStateException("Los datos ingresados no cumplen las politicas contables.");
        }

        ThirdParty supplier = resolveSupplier(request.getSupplierId());
        ChartOfAccount chartOfAccount = resolveChartOfAccount(request.getAccountingCode(), true);
        AccountingAccount accountingAccount = resolveAccountingAccount(request.getAccountingAccountId());

        validateAssetClassification(request.getClassification(), request.getUsefulLifeMonths());
        validateAccountsPayableDependency(
                request.getAccountsPayableReferenceId(),
                supplier.getId(),
                request.getPaymentTerms()
        );
        validateBankCashDependency(request.getBankCashReferenceId());

        String normalizedDescription = normalizeOptionalText(request.getDescription());
        String normalizedObservations = normalizeOptionalText(request.getObservations());
        String normalizedPaymentTerms = request.getPaymentTerms().trim();
        String currentUser = resolveCurrentUsername();

        existingAsset.setAssetName(request.getName().trim());
        existingAsset.setDescription(normalizedDescription);
        existingAsset.setClassification(request.getClassification());
        existingAsset.setAssetType(request.getType());
        existingAsset.setChartOfAccount(chartOfAccount);
        existingAsset.setSupplier(supplier);
        existingAsset.setAcquisitionValue(request.getAcquisitionValue());
        existingAsset.setAcquisitionDate(request.getAcquisitionDate());
        existingAsset.setUsefulLifeMonths(request.getUsefulLifeMonths());
        existingAsset.setDepreciationMethod(request.getDepreciationMethod());
        existingAsset.setPaymentTerms(normalizedPaymentTerms);
        existingAsset.setAccountsPayableReferenceId(request.getAccountsPayableReferenceId());
        existingAsset.setBankCashReferenceId(request.getBankCashReferenceId());
        existingAsset.setAccountingAccount(accountingAccount);
        existingAsset.setStatus(request.getStatus());
        existingAsset.setObservations(normalizedObservations);
        existingAsset.setUpdatedBy(currentUser);

        Assets savedAsset = assetsRepository.save(existingAsset);
        return toViewDTO(savedAsset);
    }

    private void validateAccountingPeriodIsOpen() {
        boolean accountingPeriodOpen = true;

        // TODO Integrar validacion real del periodo contable cuando este disponible
        // el modulo de periodos contables.
        if (!accountingPeriodOpen) {
            throw new IllegalStateException("Periodo contable cerrado");
        }
    }

    private void validateAssetClassification(AssetClassification classification, Integer usefulLifeMonths) {
        if (classification == AssetClassification.CURRENT && usefulLifeMonths != null && usefulLifeMonths > 12) {
            throw new IllegalArgumentException("Los datos ingresados no cumplen las politicas contables.");
        }

        if (classification == AssetClassification.NON_CURRENT && usefulLifeMonths != null && usefulLifeMonths <= 12) {
            throw new IllegalArgumentException("Los datos ingresados no cumplen las politicas contables.");
        }
    }

    private ThirdParty resolveSupplier(Long supplierId) {
        ThirdParty supplier = thirdPartyRepository.findByIdAndDeletedAtIsNull(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Proveedor no registrado"));

        if (!isActiveThirdParty(supplier) || !isSupplierRoleAssigned(supplier)) {
            throw new IllegalArgumentException("Proveedor no registrado");
        }

        return supplier;
    }

    private ChartOfAccount resolveChartOfAccount(String accountingCode, boolean updateFlow) {
        String normalizedCode = normalizeAccountingCode(accountingCode);

        ChartOfAccount chartOfAccount = chartOfAccountRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta contable no existe"));

        if (chartOfAccount.getStatus() != com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountStatus.ACTIVE
                || chartOfAccount.getAccountClass() != AccountClass.ASSET) {
            if (updateFlow) {
                throw new IllegalArgumentException("Codigo contable invalido o no pertenece al grupo de activos permitidos.");
            }
            throw new IllegalArgumentException("Codigo contable no valido o no pertenece al grupo de activos.");
        }

        return chartOfAccount;
    }

    private AccountingAccount resolveAccountingAccount(Long accountingAccountId) {
        AccountingAccount accountingAccount = accountingAccountRepository.findById(accountingAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta contable no existe"));

        if (accountingAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Cuenta contable no existe");
        }

        return accountingAccount;
    }

    private void validateAccountsPayableDependency(Long accountsPayableReferenceId, Long supplierId, String paymentTerms) {
        // TODO Integrar validacion con modulo de Cuentas por Pagar:
        // 1) Verificar que el termino de pago provenga de cuentas por pagar activas.
        // 2) Verificar que el termino este asociado al proveedor.
        // 3) Validar la deuda/condicion vigente antes de registrar/editar activos.
        if (accountsPayableReferenceId != null && accountsPayableReferenceId <= 0) {
            throw new IllegalArgumentException("Los datos ingresados no cumplen las politicas contables.");
        }
    }

    private void validateBankCashDependency(Long bankCashReferenceId) {
        // TODO Integrar validacion con modulo Bancos/Cajas cuando este disponible.
        if (bankCashReferenceId != null && bankCashReferenceId <= 0) {
            throw new IllegalArgumentException("Los datos ingresados no cumplen las politicas contables.");
        }
    }

    private void validateAssetPermission(String requiredPermission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        boolean authorized = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(authority ->
                        requiredPermission.equalsIgnoreCase(authority)
                                || ROLE_SUPERADMIN.equalsIgnoreCase(authority)
                );

        if (!authorized) {
            throw new IllegalStateException("Acceso no autorizado para gestionar activos.");
        }
    }

    private String resolveCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "sistema";
        }
        return StringUtils.hasText(authentication.getName()) ? authentication.getName() : "sistema";
    }

    private String generateAssetCode() {
        int year = Year.now().getValue();
        long sequence = assetsRepository.count() + 1;
        String candidate;

        do {
            candidate = String.format("ACT%d%06d", year, sequence++);
        } while (assetsRepository.existsByAssetCode(candidate));

        return candidate;
    }

    private String normalizeAccountingCode(String accountingCode) {
        if (!StringUtils.hasText(accountingCode)) {
            throw new IllegalArgumentException("Faltan datos requeridos");
        }
        return accountingCode.trim();
    }

    private String normalizeOptionalText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private boolean isActiveThirdParty(ThirdParty thirdParty) {
        if (thirdParty.getStatus() == null || !StringUtils.hasText(thirdParty.getStatus().getName())) {
            return false;
        }

        String statusName = thirdParty.getStatus().getName().trim();
        return "ACTIVO".equalsIgnoreCase(statusName) || "ACTIVE".equalsIgnoreCase(statusName);
    }

    private boolean isSupplierRoleAssigned(ThirdParty thirdParty) {
        if (thirdParty.getRoles() == null || thirdParty.getRoles().isEmpty()) {
            return false;
        }

        return thirdParty.getRoles().stream()
                .map(ThirdPartyRoleCatalog::getName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(roleName -> "PROVEEDOR".equalsIgnoreCase(roleName) || "SUPPLIER".equalsIgnoreCase(roleName));
    }

    private DataTableRequest normalizeDataTableRequest(DataTableRequest request) {
        DataTableRequest safeRequest = request != null ? request : new DataTableRequest();

        if (safeRequest.getLength() == 0) {
            safeRequest.setLength(20);
        }

        if (safeRequest.getColumns() == null) {
            safeRequest.setColumns(new ArrayList<>());
        }

        if (safeRequest.getSearch() == null) {
            safeRequest.setSearch(new DataTableRequest.DataTableSearch("", false));
        }

        List<DataTableRequest.DataTableColumn> normalizedColumns = safeRequest.getColumns().stream()
                .map(column -> {
                    if (column == null || !StringUtils.hasText(column.getData())) {
                        return column;
                    }
                    column.setData(mapDataTableColumn(column.getData().trim()));
                    return column;
                })
                .toList();

        safeRequest.setColumns(normalizedColumns);
        return safeRequest;
    }

    private String mapDataTableColumn(String columnName) {
        return switch (columnName) {
            case "assetCode" -> "assetCode";
            case "name" -> "assetName";
            case "classification" -> "classification";
            case "type" -> "assetType";
            case "accountingCode" -> "chartOfAccount.code";
            case "accountingName" -> "chartOfAccount.name";
            case "supplierId" -> "supplier.id";
            case "supplierName" -> "supplier.businessName";
            case "status" -> "status";
            default -> columnName;
        };
    }

    private void validateDataTableRequest(DataTableRequest request) {
        if (request.getLength() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Parametros de paginacion invalidos. Limite maximo: 100 registros.");
        }

        for (DataTableRequest.DataTableColumn column : request.getColumns()) {
            if (column == null || !StringUtils.hasText(column.getData())) {
                continue;
            }
            if (!ALLOWED_DATA_TABLE_FIELDS.contains(column.getData())) {
                throw new IllegalArgumentException("Campo de ordenamiento no valido.");
            }
        }
    }

    private ViewAssetsDTO toViewDTO(Assets asset) {
        return ViewAssetsDTO.builder()
                .id(asset.getId())
                .assetCode(asset.getAssetCode())
                .name(asset.getAssetName())
                .description(asset.getDescription())
                .classification(asset.getClassification())
                .type(asset.getAssetType())
                .chartOfAccountId(asset.getChartOfAccount() != null ? asset.getChartOfAccount().getId() : null)
                .accountingCode(asset.getChartOfAccount() != null ? asset.getChartOfAccount().getCode() : null)
                .accountingName(asset.getChartOfAccount() != null ? asset.getChartOfAccount().getName() : null)
                .supplierId(asset.getSupplier() != null ? asset.getSupplier().getId() : null)
                .supplierName(asset.getSupplier() != null ? asset.getSupplier().getBusinessName() : null)
                .acquisitionValue(asset.getAcquisitionValue())
                .acquisitionDate(asset.getAcquisitionDate())
                .usefulLifeMonths(asset.getUsefulLifeMonths())
                .depreciationMethod(asset.getDepreciationMethod())
                .paymentTerms(asset.getPaymentTerms())
                .accountsPayableReferenceId(asset.getAccountsPayableReferenceId())
                .bankCashReferenceId(asset.getBankCashReferenceId())
                .accountingAccountId(asset.getAccountingAccount() != null ? asset.getAccountingAccount().getId() : null)
                .accountingAccountName(asset.getAccountingAccount() != null ? asset.getAccountingAccount().getCustomName() : null)
                .status(asset.getStatus())
                .observations(asset.getObservations())
                .createdBy(asset.getCreatedBy())
                .updatedBy(asset.getUpdatedBy())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
