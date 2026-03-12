package com.sigcon.backend.assets.assets.domain.service;

import com.sigcon.backend.assets.assets.application.CreateAssetsDTO;
import com.sigcon.backend.assets.assets.application.UpdateAssetsDTO;
import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetClassification;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
//import com.sigcon.backend.assets.assets.domain.repository.AssetChartOfAccountBridgeRepository;
import com.sigcon.backend.assets.assets.domain.repository.AssetThirdPartyBridgeRepository;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.depretation_rules.application.DepretationRuleDTO;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.DepretationRule;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.repository.DepretationRuleRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.resources.domain.model.Country;
import com.sigcon.backend.parametrization.resources.domain.model.Municipality;
import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;
import com.sigcon.backend.parametrization.resources.domain.model.Withholding;
import com.sigcon.backend.third_parties.third_parties.application.ThirdContactDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyRoleCatalogDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyStatusCatalogDTO;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdContact;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyRoleCatalog;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyStatusCatalog;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdPartyWithholdingAssignment;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetsService {

    private final AssetsRepository assetsRepository;
    private final AssetThirdPartyBridgeRepository thirdPartyRepository;
    private final DepretationRuleRepository depretationRuleRepository;

  //  private final AssetChartOfAccountBridgeRepository chartOfAccountRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final DataTableSpecificationBuilder<Assets> dataTableSpecificationBuilder =
            new DataTableSpecificationBuilder<>();

    @Transactional
    public ViewAssetsDTO create(CreateAssetsDTO request) {

        ThirdParty supplier = resolveSupplier(request.getSupplierId());
        AccountingAccount accountingAccount = resolveAccountingAccount(request.getAccountingAccountId());
        DepretationRule depreciationRule = depretationRuleRepository.findByIdAndAccountingAccountId(request.getDepreciationRuleId(), request.getAccountingAccountId());
        if (depreciationRule == null) {
            throw new IllegalArgumentException("Regla de depreciacion no encontrada");
        }

        validateAssetClassification(request.getClassification(), request.getUsefulLifeMonths());

        String currentUser = resolveCurrentUsername();

        Assets asset = Assets.builder()
                .assetCode(generateAssetCode())
                .assetName(request.getName().trim())
                .description(normalizeOptionalText(request.getDescription()))
                .classification(request.getClassification())
                .assetType(request.getType())
                .supplier(supplier)
                .acquisitionValue(request.getAcquisitionValue())
                .acquisitionDate(request.getAcquisitionDate())
                .usefulLifeMonths(request.getUsefulLifeMonths())
                .depretationRule(depreciationRule)
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

    @Transactional
    public DataTableResponse<ViewAssetsDTO> findAllPaged(DataTableRequest request) {
        if (request == null) {
            request = new DataTableRequest();
        }

        int draw = Math.max(0, request.getDraw());
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<Assets> specification = dataTableSpecificationBuilder.build(request);

        Page<Assets> assetsPage = assetsRepository.findAll(specification, pageable);
        return DataTableResponse.from(assetsPage.map(this::toViewDTO), draw);
    }

    @Transactional
    public ViewAssetsDTO getById(Long id) {
        Assets asset = assetsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El activo seleccionado no existe."));
        return toViewDTO(asset);
    }

    @Transactional
    public ViewAssetsDTO update(Long id, UpdateAssetsDTO request) {

        Assets existingAsset = assetsRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El activo seleccionado no existe."));

        if (existingAsset.getStatus() == AssetStatus.DECOMMISSIONED
                || existingAsset.getStatus() == AssetStatus.TRANSFERRED) {
            throw new IllegalStateException("Los datos ingresados no cumplen las politicas contables.");
        }

        ThirdParty supplier = resolveSupplier(request.getSupplierId());
        AccountingAccount accountingAccount = resolveAccountingAccount(request.getAccountingAccountId());
        DepretationRule depretationRule = depretationRuleRepository.findByIdAndAccountingAccountId(request.getDepreciationRuleId(), request.getAccountingAccountId());
        if (depretationRule == null) {
            throw new IllegalArgumentException("Regla de depreciacion no encontrada");
        }

        validateAssetClassification(request.getClassification(), request.getUsefulLifeMonths());

        String normalizedDescription = normalizeOptionalText(request.getDescription());
        String normalizedObservations = normalizeOptionalText(request.getObservations());
        String currentUser = resolveCurrentUsername();

        existingAsset.setAssetName(request.getName().trim());
        existingAsset.setDescription(normalizedDescription);
        existingAsset.setClassification(request.getClassification());
        existingAsset.setAssetType(request.getType());
        // existingAsset.setChartOfAccount(chartOfAccount);
        existingAsset.setSupplier(supplier);
        existingAsset.setAcquisitionValue(request.getAcquisitionValue());
        existingAsset.setAcquisitionDate(request.getAcquisitionDate());
        existingAsset.setUsefulLifeMonths(request.getUsefulLifeMonths());
        existingAsset.setDepretationRule(depretationRule);
        existingAsset.setAccountsPayableReferenceId(request.getAccountsPayableReferenceId());
        existingAsset.setBankCashReferenceId(request.getBankCashReferenceId());
        existingAsset.setAccountingAccount(accountingAccount);
        existingAsset.setStatus(request.getStatus());
        existingAsset.setObservations(normalizedObservations);
        existingAsset.setUpdatedBy(currentUser);

        Assets savedAsset = assetsRepository.save(existingAsset);
        return toViewDTO(savedAsset);
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

    private AccountingAccount resolveAccountingAccount(Long accountingAccountId) {
        AccountingAccount accountingAccount = accountingAccountRepository
                .findByIdAndDeletedAtIsNull(accountingAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta contable no existe"));

        if (accountingAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Cuenta contable no existe");
        }

        return accountingAccount;
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

    private ViewAssetsDTO toViewDTO(Assets asset) {
        return ViewAssetsDTO.builder()
                .id(asset.getId())
                .assetCode(asset.getAssetCode())
                .name(asset.getAssetName())
                .description(asset.getDescription())
                .classification(asset.getClassification())
                .type(asset.getAssetType())
                .accountingAccount(toAccountingAccountDto(asset.getAccountingAccount()))
                .supplier(toThirdPartyDto(asset.getSupplier()))
                .acquisitionValue(asset.getAcquisitionValue())
                .acquisitionDate(asset.getAcquisitionDate())
                .usefulLifeMonths(asset.getUsefulLifeMonths())
                .depretationRule(toDepretationRuleDto(asset.getDepretationRule()))
                .accountsPayableReferenceId(asset.getAccountsPayableReferenceId())
                .bankCashReferenceId(asset.getBankCashReferenceId())
                .status(asset.getStatus())
                .observations(asset.getObservations())
                .createdBy(asset.getCreatedBy())
                .updatedBy(asset.getUpdatedBy())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }

    private AccountingAccountDTO toAccountingAccountDto(AccountingAccount accountingAccount) {
        if (accountingAccount == null) {
            return null;
        }

        return AccountingAccountDTO.builder()
                .id(accountingAccount.getId())
                .puc_id(accountingAccount.getPucAccount() != null ? accountingAccount.getPucAccount().getId() : null)
                .pucAccount(toChartOfAccountDto(accountingAccount.getPucAccount()))
                .customName(accountingAccount.getCustomName())
                .currencyType(accountingAccount.getCurrencyType() != null
                        ? CurrencyTypeResponseDTO.builder()
                                .id(accountingAccount.getCurrencyType().getId())
                                .isoCode(accountingAccount.getCurrencyType().getIsoCode())
                                .name(accountingAccount.getCurrencyType().getName())
                                .status(accountingAccount.getCurrencyType().getStatus())
                                .createdAt(accountingAccount.getCurrencyType().getCreatedAt())
                                .build()
                        : null)
                .costCenter(accountingAccount.getCostCenter() != null
                        ? CostCenterDTO.builder()
                                .id(accountingAccount.getCostCenter().getId())
                                .code(accountingAccount.getCostCenter().getCode())
                                .name(accountingAccount.getCostCenter().getName())
                                .description(accountingAccount.getCostCenter().getDescription())
                                .status(accountingAccount.getCostCenter().getStatus())
                                .companyId(accountingAccount.getCostCenter().getCompanyId())
                                .createdAt(accountingAccount.getCostCenter().getCreatedAt())
                                .updatedAt(accountingAccount.getCostCenter().getUpdatedAt())
                                .deletionReason(accountingAccount.getCostCenter().getDeletionReason())
                                .build()
                        : null)
                .taxRuleId(accountingAccount.getTaxRuleId())
                .nature(accountingAccount.getNature())
                .status(accountingAccount.getStatus())
                .createdAt(accountingAccount.getCreatedAt())
                .updatedAt(accountingAccount.getUpdatedAt())
                .deletedAt(accountingAccount.getDeletedAt())
                .build();
    }

    private ChartOfAccountResponseDTO toChartOfAccountDto(ChartOfAccount chartOfAccount) {
        if (chartOfAccount == null) {
            return null;
        }
        return ChartOfAccountResponseDTO.builder()
                .id(chartOfAccount.getId())
                .code(chartOfAccount.getCode())
                .name(chartOfAccount.getName())
                .accountClass(chartOfAccount.getAccountClass())
                .level(chartOfAccount.getAccountLevel())
                .nature(chartOfAccount.getAccountNature())
                .status(chartOfAccount.getStatus())
                .createdAt(chartOfAccount.getCreatedAt())
                .updatedAt(chartOfAccount.getUpdatedAt())
                .deletedAt(chartOfAccount.getDeletedAt())
                .build();
    }

    private DepretationRuleDTO toDepretationRuleDto(DepretationRule depretationRule) {
        if (depretationRule == null) {
            return null;
        }
        return DepretationRuleDTO.builder()
                .id(depretationRule.getId())
                .name(depretationRule.getName())
                .build();
    }
    
    private ThirdPartyDTO toThirdPartyDto(ThirdParty entity) {
        if (entity == null) {
            return null;
        }
        List<Long> roleIds = entity.getRoles() == null ? List.of()
                : entity.getRoles().stream().map(ThirdPartyRoleCatalog::getId).toList();

        return ThirdPartyDTO.builder()
                .id(entity.getId())
                .thirdPartyCode(entity.getThirdPartyCode())
                .nit(entity.getNit())
                .dv(entity.getDv())
                .businessName(entity.getBusinessName())
                .roles(toRoleCatalogDtoList(entity.getRoles()))
                .roleIds(roleIds)
                .status(toStatusCatalogDto(entity.getStatus()))
                .statusId(entity.getStatus() != null ? entity.getStatus().getId() : null)
                .blockingReason(entity.getBlockingReason())
                .municipality(toMunicipalityDto(entity.getMunicipality()))
                .municipalityId(entity.getMunicipality() != null ? entity.getMunicipality().getId() : null)
                .typeOrganization(toTypeOrganizationDto(entity.getTypeOrganization()))
                .typeOrganizationId(entity.getTypeOrganization() != null ? entity.getTypeOrganization().getId() : null)
                .typeRegimen(toTypeRegimenDto(entity.getTypeRegimen()))
                .typeRegimenId(entity.getTypeRegimen() != null ? entity.getTypeRegimen().getId() : null)
                .withholdings(toWithholdingDtoList(entity.getWithholdingAssignments()))
                .withholdingIds(toWithholdingIdList(entity.getWithholdingAssignments()))
                .creditLimit(entity.getCreditLimit())
                .paymentTerms(entity.getPaymentTerms())
                .marketSegment(entity.getMarketSegment())
                .contacts(toContactDtoList(entity.getContacts()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private List<ThirdPartyRoleCatalogDTO> toRoleCatalogDtoList(Set<ThirdPartyRoleCatalog> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> ThirdPartyRoleCatalogDTO.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .build())
                .toList();
    }

    private ThirdPartyStatusCatalogDTO toStatusCatalogDto(ThirdPartyStatusCatalog status) {
        if (status == null) {
            return null;
        }
        return ThirdPartyStatusCatalogDTO.builder()
                .id(status.getId())
                .name(status.getName())
                .build();
    }

    private MunicipalityDTO toMunicipalityDto(Municipality municipality) {
        if (municipality == null) {
            return null;
        }
        return MunicipalityDTO.builder()
                .id(municipality.getId())
                .name(municipality.getName())
                .code(municipality.getCode())
                .country(toCountryDto(municipality.getCountry()))
                .createdAt(municipality.getCreatedAt())
                .updatedAt(municipality.getUpdatedAt())
                .deletedAt(municipality.getDeletedAt())
                .build();
    }

    private CountryDTO toCountryDto(Country country) {
        if (country == null) {
            return null;
        }
        return CountryDTO.builder()
                .id(country.getId())
                .name(country.getName())
                .code(country.getCode())
                .createdAt(country.getCreatedAt())
                .updatedAt(country.getUpdatedAt())
                .deletedAt(country.getDeletedAt())
                .build();
    }

    private TypeOrganizationDTO toTypeOrganizationDto(TypeOrganization typeOrganization) {
        if (typeOrganization == null) {
            return null;
        }
        return TypeOrganizationDTO.builder()
                .id(typeOrganization.getId())
                .name(typeOrganization.getName())
                .code(typeOrganization.getCode())
                .createdAt(typeOrganization.getCreatedAt())
                .updatedAt(typeOrganization.getUpdatedAt())
                .deletedAt(typeOrganization.getDeletedAt())
                .build();
    }

    private TypeRegimenDTO toTypeRegimenDto(TypeRegimen typeRegimen) {
        if (typeRegimen == null) {
            return null;
        }
        return TypeRegimenDTO.builder()
                .id(typeRegimen.getId())
                .name(typeRegimen.getName())
                .code(typeRegimen.getCode())
                .createdAt(typeRegimen.getCreatedAt())
                .updatedAt(typeRegimen.getUpdatedAt())
                .deletedAt(typeRegimen.getDeletedAt())
                .build();
    }

    private WithholdingDTO toWithholdingDto(Withholding withholding) {
        if (withholding == null) {
            return null;
        }
        return WithholdingDTO.builder()
                .id(withholding.getId())
                .name(withholding.getName())
                .code(withholding.getCode())
                .createdAt(withholding.getCreatedAt())
                .updatedAt(withholding.getUpdatedAt())
                .deletedAt(withholding.getDeletedAt())
                .build();
    }

    private List<Long> toWithholdingIdList(List<ThirdPartyWithholdingAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        return assignments.stream()
                .map(ThirdPartyWithholdingAssignment::getWithholding)
                .filter(w -> w != null && w.getId() != null)
                .map(Withholding::getId)
                .distinct()
                .toList();
    }

    private List<WithholdingDTO> toWithholdingDtoList(List<ThirdPartyWithholdingAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }
        return assignments.stream()
                .map(ThirdPartyWithholdingAssignment::getWithholding)
                .filter(w -> w != null && w.getId() != null)
                .collect(Collectors.toMap(Withholding::getId, w -> w, (a, b) -> a, java.util.LinkedHashMap::new))
                .values().stream()
                .map(this::toWithholdingDto)
                .toList();
    }

    private List<ThirdContactDTO> toContactDtoList(List<ThirdContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return List.of();
        }
        return contacts.stream()
                .map(contact -> ThirdContactDTO.builder()
                        .id(contact.getId())
                        .position(contact.getPosition())
                        .phone(contact.getPhone())
                        .email(contact.getEmail())
                        .contactPerson(contact.getContactPerson())
                        .createdAt(contact.getCreatedAt())
                        .updatedAt(contact.getUpdatedAt())
                        .deletedAt(contact.getDeletedAt())
                        .build())
                .toList();
    }
}
