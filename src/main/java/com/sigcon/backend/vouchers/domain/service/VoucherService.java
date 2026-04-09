package com.sigcon.backend.vouchers.domain.service;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.banks.bankaccounts.application.BankAccountDTO;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.repository.CashRepository;
import com.sigcon.backend.banks.checkbooks.domain.model.Checkbook;
import com.sigcon.backend.banks.checkbooks.domain.repository.CheckbookRepository;
import com.sigcon.backend.banks.checks.domain.model.Check;
import com.sigcon.backend.banks.checks.domain.model.enums.CheckStatus;
import com.sigcon.backend.banks.checks.domain.repository.CheckRepository;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.vouchers.application.CreateVoucherDTO;
import com.sigcon.backend.vouchers.application.VoucherDTO;
import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;
import com.sigcon.backend.vouchers.domain.models.VouchersEntity;
import com.sigcon.backend.vouchers.domain.repository.VoucherTypeRepository;
import com.sigcon.backend.vouchers.domain.repository.VoucherRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherTypeRepository voucherTypeRepository;
    private final PaymentFormRepository paymentFormRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CashRepository cashRepository;
    private final CheckRepository checkRepository;
    private final AssetsRepository assetsRepository;
    private final CheckbookRepository checkbookRepository;

    private final DataTableSpecificationBuilder<VouchersEntity> dataTableSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final UserUtil userUtil;

    public VouchersEntity createVoucher(CreateVoucherDTO voucherDTO) {

        User user = userUtil.getUser();
        Company company = user.getCompany();

        VoucherTypesEntity voucherTypeEntity = voucherTypeRepository.findById(voucherDTO.getVoucherTypeId())
        .orElseThrow(() -> new RuntimeException("El tipo de comprobante no existe"));

        PaymentForms paymentFormEntity = paymentFormRepository.findById(voucherDTO.getPaymentFormId())
        .orElseThrow(() -> new RuntimeException("El formulario de pago no existe"));

        if (
            (voucherDTO.getBankAccountId() == null || voucherDTO.getBankAccountId() == 0) &&
            (voucherDTO.getCashAccountId() == null || voucherDTO.getCashAccountId() == 0) &&
            (voucherDTO.getCheckId() == null || voucherDTO.getCheckId() == 0)
        ) {
            throw new IllegalArgumentException("Debe existir al menos un origen de pago");
        }

        VouchersEntity voucherEntity = VouchersEntity.builder().build();
        voucherEntity.setVoucherType(voucherTypeEntity);
        voucherEntity.setNumber(generateVoucherNumber(voucherTypeEntity.getId(), company.getId()));
        voucherEntity.setDate(voucherDTO.getDate());
        voucherEntity.setAmount(voucherDTO.getAmount());
        voucherEntity.setDescription(voucherDTO.getDescription());
        voucherEntity.setPaymentForm(paymentFormEntity);
        voucherEntity.setCompany(company);
        voucherEntity.setUser(user);

        if(voucherDTO.getAssetId() != null) {
            Assets assetEntity = assetsRepository.findById(voucherDTO.getAssetId())
            .orElseThrow(() -> new RuntimeException("El activo no existe"));

            voucherEntity.setAsset(assetEntity);
        }

        if (voucherDTO.getBankAccountId() != null) {
            BankAccount bankAccount = bankAccountRepository
                .findByIdAndDeletedAtIsNull(voucherDTO.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));
        
            voucherEntity.setBankAccount(bankAccount);
            switch (voucherTypeEntity.getCode()) {
                case "PC":
                    bankAccount.setInitialBalance(bankAccount.getInitialBalance().subtract(voucherDTO.getAmount()));
                    break;
                default:
                    bankAccount.setInitialBalance(bankAccount.getInitialBalance().add(voucherDTO.getAmount()));
                    break;
            }
            bankAccountRepository.saveAndFlush(bankAccount);
        }

        if(voucherDTO.getCashAccountId() != null) {
            Cash cashEntity = cashRepository.findById(voucherDTO.getCashAccountId())
            .orElseThrow(() -> new RuntimeException("La cuenta de efectivo no existe"));

            cashEntity.setInitialBalance(cashEntity.getInitialBalance().subtract(voucherDTO.getAmount()));
            cashRepository.saveAndFlush(cashEntity);

            voucherEntity.setCash(cashEntity);
        }

        if(voucherDTO.getCheckId() != null) {
            Check checkEntity = checkRepository.findById(voucherDTO.getCheckId())
            .orElseThrow(() -> new RuntimeException("El cheque no existe"));

            voucherEntity.setCheck(checkEntity);

            BankAccount bankAccount = bankAccountRepository
                .findByIdAndDeletedAtIsNull(checkEntity.getCheckbook().getBankAccount().getId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));

            bankAccount.setInitialBalance(bankAccount.getInitialBalance().subtract(voucherDTO.getAmount()));
            bankAccountRepository.saveAndFlush(bankAccount);

            checkEntity.setStatusCheck(CheckStatus.COBRADO);
            checkRepository.saveAndFlush(checkEntity);
        }

        return voucherRepository.save(voucherEntity);
    }

    public ResponseEntity<?> getVouchers(DataTableRequest request) {

        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : length > 100 ? 100 : length;
        int page = start / safeLength;

        User user = userUtil.getUser();

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<VouchersEntity> spec = dataTableSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")))
                .and((root, query, cb) -> cb.equal(root.get("company"), user.getCompany()));

        Page<VouchersEntity> vouchers = voucherRepository.findAll(spec, pageable);

        if (vouchers.isEmpty()) {
            throw new IllegalArgumentException(
                    "VOU_001: No se encontraron vouchers con los criterios de busqueda especificados.");
        }

        return ResponseEntity.ok(DataTableResponse.from(vouchers.map(this::toDto), request.getDraw()));
    }

    // Funciones
    public BigInteger generateVoucherNumber(Long voucherTypeId, Long companyId) {

        BigInteger number = voucherRepository.findTopByVoucherTypeIdAndCompanyIdOrderByNumberDesc(voucherTypeId, companyId);

        if (number == null || number.equals(BigInteger.ZERO)) {
            return BigInteger.ONE;
        } else {
            return number.add(BigInteger.ONE);
        }
    }

    private VoucherDTO toDto(VouchersEntity voucher) {
        return VoucherDTO.builder()
        .id(voucher.getId())
        .number(voucher.getNumber())
        .date(voucher.getDate())
        .amount(voucher.getAmount())
        .description(voucher.getDescription())
        .bankAccount(
            voucher.getBankAccount() != null ? toBankAccountDto(voucher.getBankAccount()) : (
                voucher.getCheck() != null ? toBankAccountDto(voucher.getCheck().getCheckbook().getBankAccount()) : null
            )
        )
        .build();
    }

    private BankAccountDTO toBankAccountDto(BankAccount bankAccount) {
        return BankAccountDTO.builder()
        .id(bankAccount.getId())
        .code(bankAccount.getCode())
        .accountName(bankAccount.getAccountName())
        .build();
    }
}
