package com.sigcon.backend.assets.assets.domain.service;

import com.sigcon.backend.assets.assets.application.BulkAssetsUploadRequest;
import com.sigcon.backend.assets.assets.application.BulkAssetsUploadResponse;
import com.sigcon.backend.assets.assets.application.CreateAssetTaxesRetention;
import com.sigcon.backend.assets.assets.application.CreateAssetsDTO;
import com.sigcon.backend.assets.assets.application.UpdateAssetsDTO;
import com.sigcon.backend.assets.assets.application.ViewAssetTaxesRetentionDTO;
import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.model.AssetsTaxesRetention;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetClassification;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetType;
import com.sigcon.backend.assets.assets.domain.repository.AssetTaxesRetentionRepository;
//import com.sigcon.backend.assets.assets.domain.repository.AssetChartOfAccountBridgeRepository;
import com.sigcon.backend.assets.assets.domain.repository.AssetThirdPartyBridgeRepository;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.banks.bankaccounts.application.BankAccountDTO;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.cash_management.application.CashDTO;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.repository.CashRepository;
import com.sigcon.backend.banks.checkbooks.domain.model.Checkbook;
import com.sigcon.backend.banks.checkbooks.domain.repository.CheckbookRepository;
import com.sigcon.backend.banks.checks.application.CheckDTO;
import com.sigcon.backend.banks.checks.domain.model.Check;
import com.sigcon.backend.banks.checks.domain.repository.CheckRepository;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
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
import com.sigcon.backend.lists_accounting.ruler_tax.application.RuleTaxDTO;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.repository.RuleTaxRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.parametrization.resources.application.MunicipalityDTO;
import com.sigcon.backend.parametrization.resources.application.PaymentFormsDTO;
import com.sigcon.backend.parametrization.resources.application.TypeOrganizationDTO;
import com.sigcon.backend.parametrization.resources.application.TypeRegimenDTO;
import com.sigcon.backend.parametrization.resources.application.WithholdingDTO;
import com.sigcon.backend.parametrization.resources.domain.model.Country;
import com.sigcon.backend.parametrization.resources.domain.model.Municipality;
import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;
import com.sigcon.backend.parametrization.resources.domain.model.Withholding;
import com.sigcon.backend.parametrization.users.domain.model.User;
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
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.vouchers.application.CreateVoucherDTO;
import com.sigcon.backend.vouchers.application.VoucherDTO;
import com.sigcon.backend.vouchers.application.VoucherTypeDTO;
import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;
import com.sigcon.backend.vouchers.domain.models.VouchersEntity;
import com.sigcon.backend.vouchers.domain.repository.VoucherRepository;
import com.sigcon.backend.vouchers.domain.service.VoucherService;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetsService {

    private static final int MAX_BULK_ROWS = 10_000;
    private static final String ASSET_NAME_REGEX = "^[\\p{L}0-9\\-_/.,\\s]{3,150}$";
    private static final BigDecimal MIN_ACQUISITION_VALUE = new BigDecimal("0.01");
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private final VoucherService voucherService;
    private final AuditPublisher auditPublisher;

    private final UserUtil userUtil;

    private final AssetsRepository assetsRepository;
    private final AssetTaxesRetentionRepository assetTaxesRetentionRepository;
    private final AssetThirdPartyBridgeRepository thirdPartyRepository;
    private final DepretationRuleRepository depretationRuleRepository;
    private final VoucherRepository voucherRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CheckbookRepository checkbookRepository;
    private final CashRepository cashRepository;
    private final CheckRepository checkRepository;

    private final RuleTaxRepository taxRuleRepository;

    // private final AssetChartOfAccountBridgeRepository chartOfAccountRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final com.sigcon.backend.general.accounting.AccountingPeriodService accountingPeriodService;
    private final DataTableSpecificationBuilder<Assets> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

    @Transactional
    public ViewAssetsDTO create(CreateAssetsDTO request) {

        User user = userUtil.getUser();

        // ERR-MNT-ACT-01: Validar período contable abierto con mensaje descriptivo
        if (request.getAcquisitionDate() != null) {
            accountingPeriodService.validatePeriodOpen(request.getAcquisitionDate());
        }

        // ACT-01 E8: Validar que se haya seleccionado una forma de pago valida
        if (request.getPaymentFormId() == null) {
            throw new IllegalArgumentException("Debe seleccionar una forma de pago válida (contado o crédito).");
        }

        ThirdParty supplier = resolveSupplier(request.getSupplierId());
        AccountingAccount accountingAccount = resolveAccountingAccount(request.getAccountingAccountId());
        DepretationRule depreciationRule = depretationRuleRepository
                .findByIdAndAccountingAccountId(request.getDepreciationRuleId(), request.getAccountingAccountId());
        if (depreciationRule == null) {
            throw new IllegalArgumentException("Regla de depreciacion no encontrada");
        }

        validateAssetClassification(request.getClassification(), request.getUsefulLifeMonths());

        String currentUser = resolveCurrentUsername();

        BigDecimal taxValue = BigDecimal.ZERO;
        // TODO: Leer de SystemInfoService cuando se integre
        // if(user.getCompany().getTypeRegimen().getId() == 2){
        //     TaxRulerEntity ruleTax = taxRuleRepository.findById(request.getRulerTax() != null ? request.getRulerTax() : 0L)
        //             .orElseThrow(() -> new IllegalArgumentException("Regla tributaria para el calculo de impuestos no encontrada"));
        //     BigDecimal percentage = BigDecimal.valueOf(ruleTax.getPercentage());
        //     taxValue = percentage.multiply(request.getAcquisitionValue()).divide(BigDecimal.valueOf(100));
        //
        //     BigDecimal totalAssetValue = request.getAcquisitionValue().add(taxValue);
        //
        //     if(request.getBankAccountId() != null || request.getCashAccountId() != null){
        //         if(request.getBankAccountId() != null){
        //             BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
        //                     .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));
        //
        //             BigDecimal bankAccountBalance = bankAccount.getInitialBalance().add(bankAccount.getCreditLimit());
        //
        //             if(bankAccountBalance.compareTo(totalAssetValue) < 0){
        //                 throw new IllegalArgumentException("El saldo de la cuenta bancaria no es suficiente para cubrir el valor del activo");
        //             }
        //         }else if(request.getCheckId() != null){
        //             Check check = checkRepository.findById(request.getCheckId())
        //                     .orElseThrow(() -> new IllegalArgumentException("Chequera no encontrada"));
        //             BigDecimal checkbookBalance = check.getCheckbook().getBankAccount().getInitialBalance().add(check.getCheckbook().getBankAccount().getCreditLimit());
        //             if(checkbookBalance.compareTo(totalAssetValue) < 0){
        //                 throw new IllegalArgumentException("El saldo de la chequera no es suficiente para cubrir el valor del activo");
        //             }else if(check.getValue().compareTo(totalAssetValue) != 0){
        //                 throw new IllegalArgumentException("El valor del cheque no coincide con el valor del activo");
        //             }
        //         }
        //     }
        //
        //     if(request.getCashAccountId() != null){
        //         Cash cash = cashRepository.findById(request.getCashAccountId())
        //                 .orElseThrow(() -> new IllegalArgumentException("Caja no encontrada"));
        //         if(cash.getMaxLimit().compareTo(totalAssetValue) < 0){
        //             throw new IllegalArgumentException("El limite maximo de la caja no es suficiente para cubrir el valor del activo");
        //         }else if(cash.getMinLimit().compareTo(totalAssetValue) > 0){
        //             throw new IllegalArgumentException("El limite minimo de la caja no es suficiente para cubrir el valor del activo");
        //         }
        //         BigDecimal cashBalance = cash.getInitialBalance();
        //         if(cashBalance.compareTo(totalAssetValue) < 0){
        //             throw new IllegalArgumentException("El saldo de la caja no es suficiente para cubrir el valor del activo");
        //         }
        //     }
        //
        // }

        Assets asset = Assets.builder()
                .assetCode(generateAssetCode())
                .assetName(request.getName().trim())
                .description(normalizeOptionalText(request.getDescription()))
                .classification(request.getClassification())
                .assetType(request.getType())
                .supplier(supplier)
                .acquisitionValue(request.getAcquisitionValue())
                .taxValue(taxValue)
                .acquisitionDate(request.getAcquisitionDate())
                .usefulLifeMonths(request.getUsefulLifeMonths())
                .depretationRule(depreciationRule)
                .accountsPayableReferenceId(request.getAccountsPayableReferenceId())
                .bankCashReferenceId(request.getBankCashReferenceId())
                .paymentFormId(request.getPaymentFormId())
                .paymentMethodId(request.getPaymentMethodId())
                .accountingAccount(accountingAccount)
                // .status(request.getStatus())
                .observations(normalizeOptionalText(request.getObservations()))
                .createdBy(currentUser)
                .updatedBy(currentUser)
                .build(); 

        Assets savedAsset = assetsRepository.save(asset);
        auditPublisher.publishCreate(AuditModule.ACT, "Asset", savedAsset.getId(),
                "Activo registrado: " + savedAsset.getAssetCode() + " - " + savedAsset.getAssetName());

        // TODO: Leer de SystemInfoService cuando se integre
        // if(user.getCompany().getTypeRegimen().getId() == 2){
        //     TaxRulerEntity ruleTax = taxRuleRepository.findById(request.getRulerTax())
        //             .orElseThrow(() -> new IllegalArgumentException("Regla tributaria para el calculo de impuestos no encontrada"));
        //     BigDecimal percentage = BigDecimal.valueOf(ruleTax.getPercentage());
        //     taxValue = percentage.multiply(request.getAcquisitionValue()).divide(BigDecimal.valueOf(100));
        //
        //     AssetsTaxesRetention assetTaxesRetention = AssetsTaxesRetention.builder()
        //             .asset(savedAsset)
        //             .taxRule(ruleTax)
        //             .percentage(percentage)
        //             .amount(taxValue)
        //             .build();
        //     assetTaxesRetentionRepository.save(assetTaxesRetention);
        // }

        // ACT-01 E2 (2026-04-28): tolerar taxesRetention null o vacio. El frontend
        // puede no enviar el array si el activo no tiene impuestos asociados.
        List<CreateAssetTaxesRetention> taxesRetentionList = request.getTaxesRetention() != null
                ? request.getTaxesRetention() : java.util.Collections.emptyList();
        for (CreateAssetTaxesRetention taxesRetention : taxesRetentionList) {

            TaxRulerEntity ruleTax = taxRuleRepository.findById(taxesRetention.getTaxRuleId())
                    .orElseThrow(() -> new IllegalArgumentException("Regla de impuesto no encontrada"));
            BigDecimal percentage = BigDecimal.valueOf(ruleTax.getPercentage());
            BigDecimal amount = BigDecimal.ZERO;

            // TODO: Leer de SystemInfoService cuando se integre
            // if(user.getCompany().getTypeRegimen().getId() == 2){
            //     if(ruleTax.getAccountingAccount().getPucAccount().getCode().startsWith("2367")){
            //         percentage = percentage.multiply(taxValue).divide(BigDecimal.valueOf(100));
            //         amount = percentage.multiply(request.getAcquisitionValue()).divide(BigDecimal.valueOf(100));
            //     }else{
            //         amount = percentage.multiply(request.getAcquisitionValue()).divide(BigDecimal.valueOf(100));
            //     }
            // }else{
            //     amount = percentage.multiply(request.getAcquisitionValue()).divide(BigDecimal.valueOf(100));
            // }
            amount = percentage.multiply(request.getAcquisitionValue()).divide(BigDecimal.valueOf(100));

            AssetsTaxesRetention assetTaxesRetention = AssetsTaxesRetention.builder()
                    .asset(savedAsset)
                    .taxRule(taxRuleRepository.findById(taxesRetention.getTaxRuleId())
                            .orElseThrow(() -> new IllegalArgumentException("Regla de impuesto no encontrada")))
                    .percentage(taxesRetention.getPercentage() != null ? taxesRetention.getPercentage() : null)
                    .amount(amount)
                    .build();
            assetTaxesRetentionRepository.save(assetTaxesRetention);
        }

        BigDecimal totalAmount = request.getAcquisitionValue().add(taxValue);

        CreateVoucherDTO voucherDTO = CreateVoucherDTO.builder()
        .voucherTypeId(1L)
        .date(request.getAcquisitionDate())
        .amount(totalAmount)
        .description("Compra de activo: " + savedAsset.getAssetName())
        .paymentFormId(request.getPaymentFormId())
        .bankAccountId(request.getBankAccountId() != null ? request.getBankAccountId() : null)
        .cashAccountId(request.getCashAccountId() != null ? request.getCashAccountId() : null)
        .checkId(request.getCheckId() != null ? request.getCheckId() : null)
        .assetId(savedAsset.getId())
        .build();

        voucherService.createVoucher(voucherDTO);

        return toViewDTO(savedAsset);
    }

    @Transactional
    public ResponseEntity<?> bulkStore(BulkAssetsUploadRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        if (request == null || request.getFileBase64() == null || request.getFileBase64().isBlank()) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }

        byte[] fileBytes = decodeBase64Payload(request.getFileBase64());
        String extension = resolveExtension(request.getFileName(), request.getFileBase64());
        char delimiter = resolveDelimiter(request.getDelimiter());

        List<BulkAssetRow> rows = switch (extension) {
            case "csv" -> parseCsvRows(fileBytes, delimiter);
            case "xlsx" -> parseXlsxRows(fileBytes);
            default -> throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        };

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }
        if (rows.size() > MAX_BULK_ROWS) {
            throw new IllegalArgumentException("BULK_003: Archivo excede limite maximo (10,000 registros).");
        }

        String currentUser = resolveCurrentUsername();
        int year = Year.now().getValue();
        java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong(
                assetsRepository.count() + 1);
        Set<String> seenAssetCodes = new HashSet<>();
        List<Assets> toCreate = new ArrayList<>();

        for (BulkAssetRow row : rows) {
            validateBulkRow(row);

            AssetClassification classification = resolveClassification(row.classification(), row.line());
            AssetType type = resolveAssetType(row.type(), row.line());
            AssetStatus status = resolveAssetStatus(row.status(), row.line());

            Long accountingAccountId = parseRequiredLong(row.accountingAccountId(), "cuenta contable", row.line());
            Long supplierId = parseRequiredLong(row.supplierId(), "proveedor", row.line());
            Long depreciationRuleId = parseRequiredLong(row.depreciationRuleId(), "regla_depreciacion", row.line());
            BigDecimal acquisitionValue = parseRequiredBigDecimal(row.acquisitionValue(), row.line());
            LocalDate acquisitionDate = parseRequiredDate(row.acquisitionDate(), row.line());
            Integer usefulLifeMonths = parseRequiredInt(row.usefulLifeMonths(), row.line());

            AccountingAccount accountingAccount = resolveAccountingAccount(accountingAccountId);
            ThirdParty supplier = resolveSupplier(supplierId);
            DepretationRule depreciationRule = depretationRuleRepository
                    .findByIdAndAccountingAccountId(depreciationRuleId, accountingAccountId);
            if (depreciationRule == null) {
                throw new IllegalArgumentException(
                        "BULK_004: Error en linea " + row.line() + ": regla de depreciacion no valida.");
            }

            validateAssetClassification(classification, usefulLifeMonths);

            Long accountsPayableRefId = parseOptionalLong(row.accountsPayableReferenceId(),
                    "referencia cuentas por pagar", row.line());
            Long bankCashRefId = parseOptionalLong(row.bankCashReferenceId(),
                    "referencia bancos/cajas", row.line());

            String assetCode = resolveBulkAssetCode(row.assetCode(), seenAssetCodes, year, sequence, row.line());
            User user = userUtil.getUser();

            Assets asset = Assets.builder()
                    .assetCode(assetCode)
                    .assetName(row.name().trim())
                    .description(normalizeOptionalText(row.description()))
                    .classification(classification)
                    .assetType(type)
                    .supplier(supplier)
                    .acquisitionValue(acquisitionValue)
                    .acquisitionDate(acquisitionDate)
                    .usefulLifeMonths(usefulLifeMonths)
                    .depretationRule(depreciationRule)
                    .accountsPayableReferenceId(accountsPayableRefId)
                    .bankCashReferenceId(bankCashRefId)
                    .accountingAccount(accountingAccount)
                    .status(status)
                    .observations(normalizeOptionalText(row.observations()))
                    .createdBy(currentUser)
                    .updatedBy(currentUser)
                    .build();

            toCreate.add(asset);
        }

        if (!toCreate.isEmpty()) {
            assetsRepository.saveAll(toCreate);
        }

        BulkAssetsUploadResponse response = BulkAssetsUploadResponse.builder()
                .totalProcessed(rows.size())
                .created(toCreate.size())
                .build();

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Carga masiva procesada exitosamente."),
                        Optional.of(response)));
    }

    @Transactional
    public DataTableResponse<ViewAssetsDTO> findAllPaged(DataTableRequest request) {
        if (request == null) {
            request = new DataTableRequest();
        }

        int draw = Math.max(0, request.getDraw());
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        // F-ACT-10-05: Limitar máximo 100 registros por página
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        // F-ACT-10-02: Soporte de ordenamiento por columnas
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort.by("id").descending();
        String orderCol = request.getOrderColumnName();
        if (orderCol != null && !orderCol.isEmpty() && !orderCol.contains(".")) {
            try {
                sort = "desc".equalsIgnoreCase(request.getOrderDir())
                    ? org.springframework.data.domain.Sort.by(orderCol).descending()
                    : org.springframework.data.domain.Sort.by(orderCol).ascending();
            } catch (Exception ignored) {}
        }

        Pageable pageable = length == -1
                ? Pageable.unpaged(sort)
                : PageRequest.of(page, safeLength, sort);

        Specification<Assets> specification = dataTableSpecificationBuilder.build(request);

        Page<Assets> assetsPage = assetsRepository.findAll(specification, pageable);
        return DataTableResponse.from(assetsPage.map(this::toViewDTO), draw);
    }

    /** F-ACT-03-01: Eliminación lógica de activo */
    @Transactional
    public void deleteAsset(Long id) {
        Assets asset = assetsRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Activo no encontrado"));
        asset.setDeletedAt(java.time.LocalDateTime.now());
        assetsRepository.save(asset);
        auditPublisher.publishDelete(AuditModule.ACT, "Asset", asset.getId(), "Asset eliminado id=" + asset.getId());
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
            throw new IllegalStateException("No es posible editar el activo. El activo se encuentra en estado '"
                + existingAsset.getStatus() + "' y no permite modificaciones.");
        }

        // Validar período contable abierto SOLO si la fecha viene en el request
        // (en update es opcional; si null se preserva la fecha existente).
        if (request.getAcquisitionDate() != null) {
            accountingPeriodService.validatePeriodOpen(request.getAcquisitionDate());
        }

        // Proveedor: si null se preserva el actual (HU-ACT-09 update parcial).
        ThirdParty supplier = request.getSupplierId() != null
                ? resolveSupplier(request.getSupplierId())
                : existingAsset.getSupplier();
        AccountingAccount accountingAccount = resolveAccountingAccount(request.getAccountingAccountId());

        // Regla de depreciacion: si null se preserva la actual. Si llega y la
        // cuenta cambio, se busca con la nueva cuenta.
        DepretationRule depretationRule;
        if (request.getDepreciationRuleId() != null) {
            depretationRule = depretationRuleRepository
                    .findByIdAndAccountingAccountId(request.getDepreciationRuleId(), request.getAccountingAccountId());
            if (depretationRule == null) {
                throw new IllegalArgumentException(
                    "El metodo de depreciacion no aplica para la cuenta contable seleccionada.");
            }
        } else {
            depretationRule = existingAsset.getDepretationRule();
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
        if (request.getAcquisitionDate() != null) {
            existingAsset.setAcquisitionDate(request.getAcquisitionDate());
        }
        existingAsset.setUsefulLifeMonths(request.getUsefulLifeMonths());
        existingAsset.setDepretationRule(depretationRule);
        existingAsset.setAccountsPayableReferenceId(request.getAccountsPayableReferenceId());
        existingAsset.setBankCashReferenceId(request.getBankCashReferenceId());
        // Preservar valores existentes si el cliente no los envia (update parcial).
        if (request.getPaymentFormId() != null) {
            existingAsset.setPaymentFormId(request.getPaymentFormId());
        }
        if (request.getPaymentMethodId() != null) {
            existingAsset.setPaymentMethodId(request.getPaymentMethodId());
        }
        existingAsset.setAccountingAccount(accountingAccount);
        // Status: si el cliente no lo manda, preservar el existente.
        if (request.getStatus() != null) {
            existingAsset.setStatus(request.getStatus());
        }
        existingAsset.setObservations(normalizedObservations);
        existingAsset.setUpdatedBy(currentUser);

        Assets savedAsset = assetsRepository.save(existingAsset);
        auditPublisher.publishUpdate(AuditModule.ACT, "Asset", existingAsset.getId(), "Asset actualizado id=" + existingAsset.getId());
        return toViewDTO(savedAsset);
    }

    private void validateAssetClassification(AssetClassification classification, Integer usefulLifeMonths) {
        if (classification == AssetClassification.CURRENT && usefulLifeMonths != null && usefulLifeMonths > 12) {
            throw new IllegalArgumentException("La clasificiacion del activo no puede ser corriente si la vida util es mayor a 12 meses.");
        }

        if (classification == AssetClassification.NON_CURRENT && usefulLifeMonths != null && usefulLifeMonths <= 12) {
            throw new IllegalArgumentException("La clasificiacion del activo no puede ser no corriente si la vida util es menor o igual a 12 meses.");
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

        List<VouchersEntity> vouchers = voucherRepository.findAllByAssetIdAndDeletedAtIsNull(
                asset.getId(), com.sigcon.backend.platform.tenant.TenantContext.getCompanyId());
        List<AssetsTaxesRetention> taxesRetention = assetTaxesRetentionRepository.findAllByAssetIdAndDeletedAtIsNull(asset.getId());

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
                .taxValue(asset.getTaxValue())
                .acquisitionDate(asset.getAcquisitionDate())
                .usefulLifeMonths(asset.getUsefulLifeMonths())

                .vouchers(toVoucherDtoList(vouchers))

                .taxesRetention(toTaxesRetentionDtoList(taxesRetention))

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

    private List<VoucherDTO> toVoucherDtoList(List<VouchersEntity> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return List.of();
        }
        return vouchers.stream()
                .map(this::toVoucherDto)
                .toList();
    }

    private VoucherDTO toVoucherDto(VouchersEntity voucher) {
        if (voucher == null) {
            return null;
        }
        return VoucherDTO.builder()
            .id(voucher.getId())
            .number(voucher.getNumber())
            .date(voucher.getDate())
            .amount(voucher.getAmount())
            .description(voucher.getDescription())
            .paymentForm(toPaymentFormDto(voucher.getPaymentForm()))
            .voucherType(toVoucherTypeDto(voucher.getVoucherType()))
            .cashAccount(toCashAccountsDto(voucher.getCash()))
            .check(toChecksDTO(voucher.getCheck()))
            .bankAccount(toBankAccountDto(voucher.getBankAccount()))
        .build();
    }

    private CashDTO toCashAccountsDto(Cash cashAccount) {
        if (cashAccount == null) {
            return null;
        }
        return CashDTO.builder()
                .id(cashAccount.getId())
                .cashName(cashAccount.getCashName())
                .build();
    }

    private CheckDTO toChecksDTO(Check check) {
        if (check == null) {
            return null;
        }
        return CheckDTO.builder()
                .id(check.getId())
                .numberCheck(check.getNumberCheck())
                .build();
    }

    private BankAccountDTO toBankAccountDto(BankAccount bankAccount) {
        if (bankAccount == null) {
            return null;
        }
        return BankAccountDTO.builder()
                .id(bankAccount.getId())
                .accountName(bankAccount.getAccountName())
                .accountNumberMasked(bankAccount.getAccountNumber())
                .build();
    }

    private PaymentFormsDTO toPaymentFormDto(PaymentForms paymentForm) {
        if (paymentForm == null) {
            return null;
        }
        return PaymentFormsDTO.builder()
                .id(paymentForm.getId())
                .name(paymentForm.getName())
                .build();
    }

    private VoucherTypeDTO toVoucherTypeDto(VoucherTypesEntity voucherType) {
        if (voucherType == null) {
            return null;
        }
        return VoucherTypeDTO.builder()
                .id(voucherType.getId())
                .name(voucherType.getName())
                .build();
    }

    private List<ViewAssetTaxesRetentionDTO> toTaxesRetentionDtoList(List<AssetsTaxesRetention> taxesRetention) {
        if (taxesRetention == null || taxesRetention.isEmpty()) {
            return List.of();
        }
        return taxesRetention.stream()
                .map(this::toTaxesRetentionDto)
                .toList();
    }

    private ViewAssetTaxesRetentionDTO toTaxesRetentionDto(AssetsTaxesRetention taxesRetention) {
        if (taxesRetention == null) {
            return null;
        }
        return ViewAssetTaxesRetentionDTO.builder()
                .id(taxesRetention.getId())
                .taxRule(toTaxRuleDto(taxesRetention.getTaxRule()))
                .percentage(taxesRetention.getPercentage())
                .amount(taxesRetention.getAmount())
                .build();
    }

    private RuleTaxDTO toTaxRuleDto(TaxRulerEntity taxRule) {
        if (taxRule == null) {
            return null;
        }
        return RuleTaxDTO.builder()
                .id(taxRule.getId())
                .name(taxRule.getName())
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
                                .createdAt(accountingAccount.getCostCenter().getCreatedAt())
                                .updatedAt(accountingAccount.getCostCenter().getUpdatedAt())
                                .deletionReason(accountingAccount.getCostCenter().getDeletionReason())
                                .build()
                        : null)
                // .taxRules(ruleTaxRepository.findByAccountingAccountId(accountingAccount.getId())
                //                 .stream().map(this::convertRuleTaxToDTO)
                //                 .toList())
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

    private byte[] decodeBase64Payload(String fileBase64) {
        String payload = fileBase64.trim();
        int comma = payload.indexOf(',');
        if (comma >= 0) {
            payload = payload.substring(comma + 1);
        }
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }
    }

    private String resolveExtension(String fileName, String fileBase64) {
        if (fileName != null && fileName.contains(".")) {
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if ("csv".equals(extension) || "xlsx".equals(extension)) {
                return extension;
            }
        }

        String payload = fileBase64 == null ? "" : fileBase64.toLowerCase(Locale.ROOT);
        if (payload.startsWith("data:text/csv")) {
            return "csv";
        }
        if (payload.startsWith("data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return "xlsx";
        }
        return "";
    }

    private char resolveDelimiter(String delimiter) {
        if (delimiter == null || delimiter.isBlank()) {
            return ',';
        }
        return delimiter.charAt(0);
    }

    private List<BulkAssetRow> parseCsvRows(byte[] fileBytes, char delimiter) {
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");
        if (lines.length == 0) {
            return List.of();
        }

        String headerLine = removeBom(lines[0]);
        char effectiveDelimiter = detectDelimiter(headerLine, delimiter);
        List<String> headers = parseCsvLine(headerLine, effectiveDelimiter);
        Map<String, Integer> canonicalHeaderIndexes = mapCanonicalHeaderIndexes(headers);
        validateRequiredHeaders(canonicalHeaderIndexes.keySet());

        List<BulkAssetRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i] == null || lines[i].trim().isEmpty()) {
                continue;
            }
            List<String> values = parseCsvLine(lines[i], effectiveDelimiter);
            rows.add(new BulkAssetRow(
                    i + 1,
                    getRowValue(values, canonicalHeaderIndexes, "asset_code"),
                    getRowValue(values, canonicalHeaderIndexes, "name"),
                    getRowValue(values, canonicalHeaderIndexes, "description"),
                    getRowValue(values, canonicalHeaderIndexes, "classification"),
                    getRowValue(values, canonicalHeaderIndexes, "type"),
                    getRowValue(values, canonicalHeaderIndexes, "accounting_account_id"),
                    getRowValue(values, canonicalHeaderIndexes, "supplier_id"),
                    getRowValue(values, canonicalHeaderIndexes, "acquisition_value"),
                    getRowValue(values, canonicalHeaderIndexes, "acquisition_date"),
                    getRowValue(values, canonicalHeaderIndexes, "useful_life_months"),
                    getRowValue(values, canonicalHeaderIndexes, "depreciation_rule_id"),
                    getRowValue(values, canonicalHeaderIndexes, "accounts_payable_reference_id"),
                    getRowValue(values, canonicalHeaderIndexes, "bank_cash_reference_id"),
                    getRowValue(values, canonicalHeaderIndexes, "status"),
                    getRowValue(values, canonicalHeaderIndexes, "observations")));
        }
        return rows;
    }

    private List<BulkAssetRow> parseXlsxRows(byte[] fileBytes) {
        try {
            Map<String, byte[]> entries = extractZipEntries(fileBytes);
            List<String> sharedStrings = readSharedStrings(entries.get("xl/sharedStrings.xml"));
            List<Map<Integer, String>> sheetRows = readXlsxRows(entries.get("xl/worksheets/sheet1.xml"), sharedStrings);
            if (sheetRows.isEmpty()) {
                return List.of();
            }

            Map<Integer, String> headersByIndex = sheetRows.get(0);
            Map<String, Integer> canonicalHeaderIndexes = mapCanonicalHeaderIndexesByColumn(headersByIndex);
            validateRequiredHeaders(canonicalHeaderIndexes.keySet());

            List<BulkAssetRow> rows = new ArrayList<>();
            for (int i = 1; i < sheetRows.size(); i++) {
                Map<Integer, String> rowMap = sheetRows.get(i);
                if (rowMap.values().stream().allMatch(v -> v == null || v.trim().isEmpty())) {
                    continue;
                }
                rows.add(new BulkAssetRow(
                        i + 1,
                        getRowValue(rowMap, canonicalHeaderIndexes, "asset_code"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "name"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "description"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "classification"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "type"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "accounting_account_id"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "supplier_id"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "acquisition_value"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "acquisition_date"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "useful_life_months"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "depreciation_rule_id"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "accounts_payable_reference_id"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "bank_cash_reference_id"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "status"),
                        getRowValue(rowMap, canonicalHeaderIndexes, "observations")));
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
        }
    }

    private String removeBom(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private char detectDelimiter(String headerLine, char configuredDelimiter) {
        if (headerLine == null || headerLine.isBlank()) {
            return configuredDelimiter;
        }

        int commas = countChar(headerLine, ',');
        int semicolons = countChar(headerLine, ';');
        int pipes = countChar(headerLine, '|');
        int tabs = countChar(headerLine, '\t');

        if (configuredDelimiter == ',' && semicolons > commas) {
            return ';';
        }
        if (configuredDelimiter == ',' && pipes > commas) {
            return '|';
        }
        if (configuredDelimiter == ',' && tabs > commas) {
            return '\t';
        }
        if (configuredDelimiter == ';' && commas > semicolons) {
            return ',';
        }

        return configuredDelimiter;
    }

    private int countChar(String text, char needle) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private List<String> parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private Map<String, Integer> mapCanonicalHeaderIndexes(List<String> headers) {
        Map<String, Integer> canonicalToIndex = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            String canonical = resolveCanonicalHeader(header);
            if (canonical != null) {
                canonicalToIndex.put(canonical, i);
            }
        }
        return canonicalToIndex;
    }

    private Map<String, Integer> mapCanonicalHeaderIndexesByColumn(Map<Integer, String> headersByColumn) {
        Map<String, Integer> canonicalToIndex = new HashMap<>();
        for (Map.Entry<Integer, String> entry : headersByColumn.entrySet()) {
            String canonical = resolveCanonicalHeader(entry.getValue());
            if (canonical != null) {
                canonicalToIndex.put(canonical, entry.getKey());
            }
        }
        return canonicalToIndex;
    }

    private String resolveCanonicalHeader(String header) {
        if (header == null) {
            return null;
        }
        String normalized = normalizeHeader(header);
        if (Set.of("codigo_activo", "asset_code", "codigo", "code", "codigo_activo_fijo").contains(normalized)) {
            return "asset_code";
        }
        if (Set.of("nombre", "nombre_activo", "asset_name", "name", "activo").contains(normalized)) {
            return "name";
        }
        if (Set.of("descripcion", "description", "detalle", "detalle_activo").contains(normalized)) {
            return "description";
        }
        if (Set.of("clasificacion", "classification", "clasificacion_activo").contains(normalized)) {
            return "classification";
        }
        if (Set.of("tipo", "tipo_activo", "type", "asset_type").contains(normalized)) {
            return "type";
        }
        if (Set.of("cuenta_contable", "cuenta_contable_id", "accounting_account",
                "accounting_account_id", "cuenta").contains(normalized)) {
            return "accounting_account_id";
        }
        if (Set.of("proveedor", "proveedor_id", "supplier", "supplier_id", "tercero", "tercero_id")
                .contains(normalized)) {
            return "supplier_id";
        }
        if (Set.of("valor_adquisicion", "acquisition_value", "valor_compra", "valor").contains(normalized)) {
            return "acquisition_value";
        }
        if (Set.of("fecha_adquisicion", "acquisition_date", "fecha_compra", "fecha").contains(normalized)) {
            return "acquisition_date";
        }
        if (Set.of("vida_util_meses", "vida_util", "useful_life_months", "useful_life").contains(normalized)) {
            return "useful_life_months";
        }
        if (Set.of("regla_depreciacion", "regla_depreciacion_id", "depreciation_rule",
                "depreciation_rule_id").contains(normalized)) {
            return "depreciation_rule_id";
        }
        if (Set.of("referencia_cxp", "accounts_payable_reference_id", "cuentas_por_pagar", "ref_cxp")
                .contains(normalized)) {
            return "accounts_payable_reference_id";
        }
        if (Set.of("referencia_bancos", "bank_cash_reference_id", "bancos_cajas", "ref_bancos")
                .contains(normalized)) {
            return "bank_cash_reference_id";
        }
        if (Set.of("estado", "status", "estado_activo", "asset_status").contains(normalized)) {
            return "status";
        }
        if (Set.of("observaciones", "observations", "notas", "nota").contains(normalized)) {
            return "observations";
        }
        return null;
    }

    private void validateRequiredHeaders(Set<String> foundHeaders) {
        List<String> required = List.of(
                "name",
                "classification",
                "type",
                "accounting_account_id",
                "supplier_id",
                "acquisition_value",
                "acquisition_date",
                "useful_life_months",
                "depreciation_rule_id");
        for (String key : required) {
            if (!foundHeaders.contains(key)) {
                throw new IllegalArgumentException(
                        "BULK_001: Formato de archivo invalido: columnas obligatorias faltantes.");
            }
        }
    }

    private String getRowValue(List<String> values, Map<String, Integer> headerIndexes, String key) {
        Integer index = headerIndexes.get(key);
        if (index == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private String getRowValue(Map<Integer, String> values, Map<String, Integer> headerIndexes, String key) {
        Integer index = headerIndexes.get(key);
        return index == null ? null : values.get(index);
    }

    private Map<String, byte[]> extractZipEntries(byte[] fileBytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), zipInputStream.readAllBytes());
            }
        }
        return entries;
    }

    private List<String> readSharedStrings(byte[] sharedStringsBytes) throws Exception {
        if (sharedStringsBytes == null || sharedStringsBytes.length == 0) {
            return List.of();
        }

        Document document = parseXml(sharedStringsBytes);
        NodeList textNodes = document.getElementsByTagNameNS("*", "t");
        List<String> sharedStrings = new ArrayList<>();
        for (int i = 0; i < textNodes.getLength(); i++) {
            sharedStrings.add(textNodes.item(i).getTextContent());
        }
        return sharedStrings;
    }

    private List<Map<Integer, String>> readXlsxRows(byte[] sheetBytes, List<String> sharedStrings) throws Exception {
        if (sheetBytes == null || sheetBytes.length == 0) {
            return List.of();
        }

        Document document = parseXml(sheetBytes);
        NodeList rowNodes = document.getElementsByTagNameNS("*", "row");
        List<Map<Integer, String>> rows = new ArrayList<>();

        for (int i = 0; i < rowNodes.getLength(); i++) {
            Node rowNode = rowNodes.item(i);
            NodeList cellNodes = rowNode.getChildNodes();
            Map<Integer, String> rowValues = new HashMap<>();
            for (int j = 0; j < cellNodes.getLength(); j++) {
                Node cellNode = cellNodes.item(j);
                if (!"c".equals(cellNode.getLocalName())) {
                    continue;
                }
                Node refNode = cellNode.getAttributes() != null ? cellNode.getAttributes().getNamedItem("r") : null;
                int columnIndex = refNode == null ? -1
                        : columnNameToIndex(refNode.getTextContent().replaceAll("\\d", ""));
                if (columnIndex < 0) {
                    continue;
                }

                String cellType = "";
                Node typeNode = cellNode.getAttributes() != null ? cellNode.getAttributes().getNamedItem("t") : null;
                if (typeNode != null) {
                    cellType = typeNode.getTextContent();
                }

                String value = "";
                NodeList cellChildren = cellNode.getChildNodes();
                for (int k = 0; k < cellChildren.getLength(); k++) {
                    Node child = cellChildren.item(k);
                    if ("v".equals(child.getLocalName())) {
                        value = child.getTextContent();
                        break;
                    }
                }

                if ("s".equals(cellType) && value != null && !value.isBlank()) {
                    int sharedIndex = Integer.parseInt(value);
                    if (sharedIndex >= 0 && sharedIndex < sharedStrings.size()) {
                        value = sharedStrings.get(sharedIndex);
                    }
                }
                rowValues.put(columnIndex, value == null ? null : value.trim());
            }
            rows.add(rowValues);
        }

        return rows;
    }

    private Document parseXml(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
    }

    private int columnNameToIndex(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return -1;
        }
        int result = 0;
        for (int i = 0; i < columnName.length(); i++) {
            char ch = Character.toUpperCase(columnName.charAt(i));
            if (ch < 'A' || ch > 'Z') {
                return -1;
            }
            result = result * 26 + (ch - 'A' + 1);
        }
        return result - 1;
    }

    private void validateBulkRow(BulkAssetRow row) {
        if (row.name() == null || row.name().trim().isEmpty()) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + row.line() + ": nombre es obligatorio.");
        }
        if (!row.name().trim().matches(ASSET_NAME_REGEX)) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": nombre de activo invalido.");
        }
        if (row.classification() == null || row.classification().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": clasificacion es obligatoria.");
        }
        if (row.type() == null || row.type().trim().isEmpty()) {
            throw new IllegalArgumentException("BULK_004: Error en linea " + row.line() + ": tipo es obligatorio.");
        }
        if (row.accountingAccountId() == null || row.accountingAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": cuenta contable es obligatoria.");
        }
        if (row.supplierId() == null || row.supplierId().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": proveedor es obligatorio.");
        }
        if (row.acquisitionValue() == null || row.acquisitionValue().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": valor adquisicion es obligatorio.");
        }
        if (row.acquisitionDate() == null || row.acquisitionDate().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": fecha adquisicion es obligatoria.");
        }
        if (row.usefulLifeMonths() == null || row.usefulLifeMonths().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": vida util es obligatoria.");
        }
        if (row.depreciationRuleId() == null || row.depreciationRuleId().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": regla de depreciacion es obligatoria.");
        }
        if (row.assetCode() != null && row.assetCode().trim().length() > 30) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": codigo de activo excede longitud permitida.");
        }
        if (row.description() != null && row.description().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": descripcion excede 500 caracteres.");
        }
        if (row.observations() != null && row.observations().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + row.line() + ": observaciones excede 500 caracteres.");
        }
    }

    private Long parseRequiredLong(String raw, String fieldName, int line) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": " + fieldName + " es obligatorio.");
        }
        try {
            Long value = parseLongValue(raw);
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": " + fieldName + " invalido.");
        }
    }

    private Long parseOptionalLong(String raw, String fieldName, int line) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            Long value = parseLongValue(raw);
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": " + fieldName + " invalida.");
        }
    }

    private Long parseLongValue(String raw) {
        String value = raw.trim();
        if (value.matches("^\\d+$")) {
            return Long.parseLong(value);
        }
        if (value.matches("^\\d+(\\.0+)?$")) {
            int dot = value.indexOf('.');
            return Long.parseLong(dot >= 0 ? value.substring(0, dot) : value);
        }
        if (value.matches("^\\d+(,0+)?$")) {
            int comma = value.indexOf(',');
            return Long.parseLong(comma >= 0 ? value.substring(0, comma) : value);
        }
        throw new NumberFormatException("invalid");
    }

    private Integer parseRequiredInt(String raw, int line) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": vida util es obligatoria.");
        }
        try {
            long value = parseLongValue(raw);
            if (value < 1 || value > Integer.MAX_VALUE) {
                throw new NumberFormatException("non-positive");
            }
            return (int) value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": vida util invalida.");
        }
    }

    private BigDecimal parseRequiredBigDecimal(String raw, int line) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": valor adquisicion es obligatorio.");
        }
        try {
            BigDecimal value = parseBigDecimal(raw);
            if (value.compareTo(MIN_ACQUISITION_VALUE) < 0) {
                throw new IllegalArgumentException("non-positive");
            }
            return value;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": valor adquisicion invalido.");
        }
    }

    private BigDecimal parseBigDecimal(String raw) {
        String value = raw.trim().replace(" ", "");
        int lastComma = value.lastIndexOf(',');
        int lastDot = value.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                value = value.replace(".", "");
                value = value.replace(",", ".");
            } else {
                value = value.replace(",", "");
            }
        } else if (lastComma >= 0) {
            value = value.replace(",", ".");
        }
        return new BigDecimal(value);
    }

    private LocalDate parseRequiredDate(String raw, int line) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": fecha adquisicion es obligatoria.");
        }

        LocalDate parsed = parseDate(raw);
        if (parsed == null) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": fecha adquisicion invalida.");
        }
        return parsed;
    }

    private LocalDate parseDate(String raw) {
        String value = raw.trim();
        if (value.matches("^\\d+(\\.\\d+)?$")) {
            double serial = Double.parseDouble(value);
            long days = (long) Math.floor(serial);
            if (days >= 0) {
                return EXCEL_EPOCH.plusDays(days);
            }
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("d/M/yyyy"));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private AssetClassification resolveClassification(String value, int line) {
        String normalized = compactToken(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "BULK_004: Error en linea " + line + ": clasificacion invalida.");
        }
        if (normalized.contains("NONCURRENT") || normalized.contains("NOCORRI") || normalized.contains("NONCORR")) {
            return AssetClassification.NON_CURRENT;
        }
        if (normalized.contains("CURRENT") || normalized.contains("CORRI")) {
            return AssetClassification.CURRENT;
        }
        throw new IllegalArgumentException(
                "BULK_004: Error en linea " + line + ": clasificacion invalida.");
    }

    private AssetType resolveAssetType(String value, int line) {
        String normalized = compactToken(value);
        if (normalized.contains("INTANG")) {
            return AssetType.INTANGIBLE;
        }
        if (normalized.contains("TANG")) {
            return AssetType.TANGIBLE;
        }
        throw new IllegalArgumentException(
                "BULK_004: Error en linea " + line + ": tipo de activo invalido.");
    }

    private AssetStatus resolveAssetStatus(String value, int line) {
        if (value == null || value.trim().isEmpty()) {
            return AssetStatus.ACTIVE;
        }
        String normalized = compactToken(value);
        if (normalized.contains("ACTIV")) {
            return AssetStatus.ACTIVE;
        }
        if (normalized.contains("REPAR") || normalized.contains("REPAIR")) {
            return AssetStatus.IN_REPAIR;
        }
        if (normalized.contains("DECOM") || normalized.contains("BAJA")) {
            return AssetStatus.DECOMMISSIONED;
        }
        if (normalized.contains("TRANS") || normalized.contains("TRASL")) {
            return AssetStatus.TRANSFERRED;
        }
        throw new IllegalArgumentException(
                "BULK_004: Error en linea " + line + ": estado invalido.");
    }

    private String resolveBulkAssetCode(String rawAssetCode, Set<String> seenAssetCodes, int year,
            java.util.concurrent.atomic.AtomicLong sequence, int line) {
        if (rawAssetCode != null && !rawAssetCode.trim().isEmpty()) {
            String cleaned = rawAssetCode.trim();
            if (!seenAssetCodes.add(cleaned)) {
                throw new IllegalArgumentException(
                        "BULK_002: Linea " + line + ": codigo de activo duplicado en archivo/sistema.");
            }
            if (assetsRepository.existsByAssetCode(cleaned)) {
                throw new IllegalArgumentException(
                        "BULK_002: Linea " + line + ": codigo de activo duplicado en archivo/sistema.");
            }
            return cleaned;
        }

        while (true) {
            String candidate = String.format("ACT%d%06d", year, sequence.getAndIncrement());
            if (!seenAssetCodes.contains(candidate) && !assetsRepository.existsByAssetCode(candidate)) {
                seenAssetCodes.add(candidate);
                return candidate;
            }
        }
    }

    private String normalizeHeader(String value) {
        return normalizeToken(value)
                .replace(" ", "_")
                .replace("/", "_")
                .replace("-", "_")
                .replace("(", "_")
                .replace(")", "_")
                .replace(".", "_")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String compactToken(String value) {
        return normalizeToken(value).replaceAll("[^A-Z0-9]", "");
    }

    private record BulkAssetRow(
            int line,
            String assetCode,
            String name,
            String description,
            String classification,
            String type,
            String accountingAccountId,
            String supplierId,
            String acquisitionValue,
            String acquisitionDate,
            String usefulLifeMonths,
            String depreciationRuleId,
            String accountsPayableReferenceId,
            String bankCashReferenceId,
            String status,
            String observations) {
    }

    private BigDecimal calculateTotalBalance(BigDecimal acquisitionValue, BigDecimal taxValue) {
        return acquisitionValue.add(taxValue);
    }
}
