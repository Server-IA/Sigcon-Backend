package com.sigcon.backend.vouchers.domain.service;

import java.math.BigInteger;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.repository.CashRepository;
import com.sigcon.backend.banks.checks.domain.model.Check;
import com.sigcon.backend.banks.checks.domain.repository.CheckRepository;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.vouchers.application.CreateVoucherDTO;
import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;
import com.sigcon.backend.vouchers.domain.models.VouchersEntity;
import com.sigcon.backend.vouchers.domain.repository.VoucerTypeRepository;
import com.sigcon.backend.vouchers.domain.repository.VoucherRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucerTypeRepository voucherTypeRepository;
    private final PaymentFormRepository paymentFormRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CashRepository cashRepository;
    private final CheckRepository checkRepository;
    private final AssetsRepository assetsRepository;

    private final UserUtil userUtil;

    public VouchersEntity createVoucher(CreateVoucherDTO voucherDTO) {

        System.out.println("\n\nVoucherDTO: " + voucherDTO);

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

        if (voucherDTO.getBankAccountId() != null) {
            BankAccount bankAccount = bankAccountRepository
                .findByIdAndDeletedAtIsNull(voucherDTO.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));
        
            voucherEntity.setBankAccount(bankAccount);
        }

        if(voucherDTO.getCashAccountId() != null) {
            Cash cashEntity = cashRepository.findById(voucherDTO.getCashAccountId())
            .orElseThrow(() -> new RuntimeException("La cuenta de efectivo no existe"));

            voucherEntity.setCash(cashEntity);
        }

        if(voucherDTO.getCheckId() != null) {
            Check checkEntity = checkRepository.findById(voucherDTO.getCheckId())
            .orElseThrow(() -> new RuntimeException("El cheque no existe"));

            voucherEntity.setCheck(checkEntity);
        }

        if(voucherDTO.getAssetId() != null) {
            Assets assetEntity = assetsRepository.findById(voucherDTO.getAssetId())
            .orElseThrow(() -> new RuntimeException("El activo no existe"));

            voucherEntity.setAsset(assetEntity);
        }

        return voucherRepository.save(voucherEntity);
    }

    // Funciones
    private BigInteger generateVoucherNumber(Long voucherTypeId, Long companyId) {

        BigInteger number = voucherRepository.findTopByVoucherTypeIdAndCompanyIdOrderByNumberDesc(voucherTypeId, companyId);

        if (number == null || number.equals(BigInteger.ZERO)) {
            return BigInteger.ONE;
        } else {
            return number.add(BigInteger.ONE);
        }
    }
}
