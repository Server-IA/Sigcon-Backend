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
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
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
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

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
    private final FinancialMovementRepository financialMovementRepository;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<VouchersEntity> dataTableSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final UserUtil userUtil;

    public VouchersEntity createVoucher(CreateVoucherDTO voucherDTO) {

        User user = userUtil.getUser();

        VoucherTypesEntity voucherTypeEntity = voucherTypeRepository.findById(voucherDTO.getVoucherTypeId())
        .orElseThrow(() -> new RuntimeException("El tipo de comprobante no existe"));

        PaymentForms paymentFormEntity = paymentFormRepository.findById(voucherDTO.getPaymentFormId())
        .orElseThrow(() -> new RuntimeException("El formulario de pago no existe"));

        // HU-ACT-01 E8/E9: la validacion de origenes de pago solo aplica para
        // forma de pago CONTADO. En CREDITO no hay salida inmediata de bancos/caja
        // (la CxP se genera automaticamente), asi que los 3 origenes pueden ser null.
        boolean noOrigin =
            (voucherDTO.getBankAccountId() == null || voucherDTO.getBankAccountId() == 0) &&
            (voucherDTO.getCashAccountId() == null || voucherDTO.getCashAccountId() == 0) &&
            (voucherDTO.getCheckId() == null || voucherDTO.getCheckId() == 0);

        if (noOrigin && Boolean.TRUE.equals(paymentFormEntity.getIsContado())) {
            // HU-ACT-01 E9: mensaje EXACTO de la historia de usuario.
            throw new IllegalArgumentException(
                "Debe especificar la cuenta o caja desde donde se realizó el pago");
        }

        VouchersEntity voucherEntity = VouchersEntity.builder().build();
        voucherEntity.setVoucherType(voucherTypeEntity);
        voucherEntity.setNumber(generateVoucherNumber(voucherTypeEntity.getId()));
        voucherEntity.setDate(voucherDTO.getDate());
        voucherEntity.setAmount(voucherDTO.getAmount());
        voucherEntity.setDescription(voucherDTO.getDescription());
        voucherEntity.setPaymentForm(paymentFormEntity);
        voucherEntity.setUser(user);

        if(voucherDTO.getAssetId() != null) {
            Assets assetEntity = assetsRepository.findById(voucherDTO.getAssetId())
            .orElseThrow(() -> new RuntimeException("El activo no existe"));

            voucherEntity.setAsset(assetEntity);
        }

        // HU-ACT-01 E1/E8: el voucher PC (Pago Compra) representa la salida de
        // efectivo asociada a la compra de un activo de contado. Ademas de
        // actualizar el saldo de la cuenta/caja, debe registrar un
        // FinancialMovement para que el contador lo vea en Bancos y Cajas.
        boolean isAssetCashPayment = "PC".equals(voucherTypeEntity.getCode());

        if (voucherDTO.getBankAccountId() != null) {
            BankAccount bankAccount = bankAccountRepository
                .findByIdAndDeletedAtIsNull(voucherDTO.getBankAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));

            voucherEntity.setBankAccount(bankAccount);
            BigDecimal signedAmount;
            switch (voucherTypeEntity.getCode()) {
                case "PC":
                    // QA-2026-05-05: validar fondos disponibles ANTES de restar
                    // (saldo + linea de credito si la cuenta acepta sobregiro).
                    {
                        BigDecimal saldo = bankAccount.getInitialBalance() != null ? bankAccount.getInitialBalance() : BigDecimal.ZERO;
                        BigDecimal linea = Boolean.TRUE.equals(bankAccount.getAllowsOverdraft()) && bankAccount.getCreditLimit() != null
                                ? bankAccount.getCreditLimit() : BigDecimal.ZERO;
                        BigDecimal disponible = saldo.add(linea);
                        if (voucherDTO.getAmount().compareTo(disponible) > 0) {
                            throw new IllegalArgumentException(
                                "Fondos insuficientes en la cuenta bancaria seleccionada. Saldo disponible: $"
                                + saldo + (linea.signum() > 0 ? " + linea de credito $" + linea : "")
                                + ". Valor solicitado: $" + voucherDTO.getAmount());
                        }
                    }
                    bankAccount.setInitialBalance(bankAccount.getInitialBalance().subtract(voucherDTO.getAmount()));
                    signedAmount = voucherDTO.getAmount().negate();
                    break;
                default:
                    bankAccount.setInitialBalance(bankAccount.getInitialBalance().add(voucherDTO.getAmount()));
                    signedAmount = voucherDTO.getAmount();
                    break;
            }
            bankAccountRepository.saveAndFlush(bankAccount);

            if (isAssetCashPayment) {
                FinancialMovement mv = FinancialMovement.builder()
                        .bankAccount(bankAccount)
                        .movementDate(voucherDTO.getDate() != null ? voucherDTO.getDate() : java.time.LocalDate.now())
                        .amount(signedAmount)
                        .description("Pago compra activo (voucher #" + voucherEntity.getNumber() + ")")
                        .externalReference(voucherDTO.getDescription())
                        .sourceType(FinancialMovementSourceType.MANUAL)
                        .flowActivity("INVERSION")
                        .build();
                financialMovementRepository.save(mv);
            }
        }

        if(voucherDTO.getCashAccountId() != null) {
            Cash cashEntity = cashRepository.findById(voucherDTO.getCashAccountId())
            .orElseThrow(() -> new RuntimeException("La cuenta de efectivo no existe"));

            // QA-2026-05-05: validar saldo disponible en la caja ANTES de restar.
            // Sin esta validacion, el JPA bean validator lanzaba un mensaje feo
            // tecnico ("Validation failed for classes [Cash]... saldo no negativo").
            if (isAssetCashPayment) {
                BigDecimal saldoCaja = cashEntity.getInitialBalance() != null ? cashEntity.getInitialBalance() : BigDecimal.ZERO;
                if (voucherDTO.getAmount().compareTo(saldoCaja) > 0) {
                    throw new IllegalArgumentException(
                        "Fondos insuficientes en la caja seleccionada. Saldo disponible: $"
                        + saldoCaja + ". Valor solicitado: $" + voucherDTO.getAmount());
                }
            }
            cashEntity.setInitialBalance(cashEntity.getInitialBalance().subtract(voucherDTO.getAmount()));
            cashRepository.saveAndFlush(cashEntity);

            voucherEntity.setCash(cashEntity);

            if (isAssetCashPayment) {
                FinancialMovement mv = FinancialMovement.builder()
                        .cash(cashEntity)
                        .movementDate(voucherDTO.getDate() != null ? voucherDTO.getDate() : java.time.LocalDate.now())
                        .amount(voucherDTO.getAmount().negate())
                        .description("Pago compra activo (voucher #" + voucherEntity.getNumber() + ")")
                        .externalReference(voucherDTO.getDescription())
                        .sourceType(FinancialMovementSourceType.MANUAL)
                        .flowActivity("INVERSION")
                        .build();
                financialMovementRepository.save(mv);
            }
        }

        if(voucherDTO.getCheckId() != null) {
            Check checkEntity = checkRepository.findById(voucherDTO.getCheckId())
            .orElseThrow(() -> new RuntimeException("El cheque no existe"));

            voucherEntity.setCheck(checkEntity);

            BankAccount bankAccount = bankAccountRepository
                .findByIdAndDeletedAtIsNull(checkEntity.getCheckbook().getBankAccount().getId())
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));

            // QA-2026-05-05: el monto del cheque debe alcanzar el valor solicitado
            // y la cuenta bancaria debe tener fondos.
            if (isAssetCashPayment) {
                BigDecimal valorCheque = checkEntity.getValue() != null ? checkEntity.getValue() : BigDecimal.ZERO;
                if (valorCheque.compareTo(voucherDTO.getAmount()) < 0) {
                    throw new IllegalArgumentException(
                        "El valor del cheque seleccionado ($" + valorCheque + ") es inferior al valor del activo ($"
                        + voucherDTO.getAmount() + "). Use otro cheque de mayor valor.");
                }
                BigDecimal saldoBanco = bankAccount.getInitialBalance() != null ? bankAccount.getInitialBalance() : BigDecimal.ZERO;
                BigDecimal lineaBanco = Boolean.TRUE.equals(bankAccount.getAllowsOverdraft()) && bankAccount.getCreditLimit() != null
                        ? bankAccount.getCreditLimit() : BigDecimal.ZERO;
                BigDecimal disponibleBanco = saldoBanco.add(lineaBanco);
                if (voucherDTO.getAmount().compareTo(disponibleBanco) > 0) {
                    throw new IllegalArgumentException(
                        "Fondos insuficientes en la cuenta bancaria del cheque. Saldo disponible: $"
                        + saldoBanco + (lineaBanco.signum() > 0 ? " + linea de credito $" + lineaBanco : "")
                        + ". Valor solicitado: $" + voucherDTO.getAmount());
                }
            }

            bankAccount.setInitialBalance(bankAccount.getInitialBalance().subtract(voucherDTO.getAmount()));
            bankAccountRepository.saveAndFlush(bankAccount);

            checkEntity.setStatusCheck(CheckStatus.COBRADO);
            checkRepository.saveAndFlush(checkEntity);

            if (isAssetCashPayment) {
                FinancialMovement mv = FinancialMovement.builder()
                        .bankAccount(bankAccount)
                        .movementDate(voucherDTO.getDate() != null ? voucherDTO.getDate() : java.time.LocalDate.now())
                        .amount(voucherDTO.getAmount().negate())
                        .description("Pago compra activo - Cheque #" + checkEntity.getNumberCheck()
                                + " (voucher #" + voucherEntity.getNumber() + ")")
                        .externalReference(voucherDTO.getDescription())
                        .sourceType(FinancialMovementSourceType.MANUAL)
                        .flowActivity("INVERSION")
                        .matchedCheckId(checkEntity.getId())
                        .build();
                financialMovementRepository.save(mv);
            }
        }

        VouchersEntity savedVoucher = voucherRepository.save(voucherEntity);
        auditPublisher.publishCreate(AuditModule.ACT, "Voucher", savedVoucher.getId(),
                "Comprobante " + voucherTypeEntity.getCode() + " #" + savedVoucher.getNumber()
                        + " creado por $" + voucherDTO.getAmount());
        return savedVoucher;
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
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<VouchersEntity> vouchers = voucherRepository.findAll(spec, pageable);

        // Un listado paginado vacio NO es un error: devolver DataTableResponse con
        // totalElements=0 para que el frontend muestre "sin resultados".
        return ResponseEntity.ok(DataTableResponse.from(vouchers.map(this::toDto), request.getDraw()));
    }

    // Funciones
    public BigInteger generateVoucherNumber(Long voucherTypeId) {

        Long companyId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        BigInteger number = voucherRepository.findTopByVoucherTypeIdAndDeletedAtIsNullOrderByNumberDesc(voucherTypeId, companyId);

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
