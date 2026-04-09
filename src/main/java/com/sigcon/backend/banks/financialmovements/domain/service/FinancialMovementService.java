package com.sigcon.backend.banks.financialmovements.domain.service;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.application.CreateBankFinancialMovementRequest;
import com.sigcon.backend.banks.financialmovements.application.FinancialMovementDTO;
import com.sigcon.backend.banks.financialmovements.application.MatchVoucherRequest;
import com.sigcon.backend.banks.financialmovements.application.VoucherMatchSuggestionDTO;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.reconciliation.domain.model.BankReconciliationSession;
import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import com.sigcon.backend.banks.reconciliation.domain.repository.BankReconciliationSessionRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.vouchers.application.CreateVoucherDTO;
import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;
import com.sigcon.backend.vouchers.domain.models.VouchersEntity;
import com.sigcon.backend.vouchers.domain.repository.VoucherRepository;
import com.sigcon.backend.vouchers.domain.repository.VoucherTypeRepository;
import com.sigcon.backend.vouchers.domain.service.VoucherService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinancialMovementService {

    private final FinancialMovementRepository financialMovementRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankReconciliationSessionRepository reconciliationSessionRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherTypeRepository voucherTypeRepository;
    private final VoucherService voucherService;

    private final UserUtil userUtil;

    public ResponseEntity<?> listForBankAccount(Long bankAccountId, boolean unmatchedOnly) {
        User user = userUtil.getUser();
        assertBankAccount(bankAccountId, user);

        List<FinancialMovement> rows = unmatchedOnly
                ? financialMovementRepository.findUnmatchedByBankAccountId(bankAccountId)
                : financialMovementRepository.findAllByBankAccountIdOrdered(bankAccountId);

        List<FinancialMovementDTO> dtos = rows.stream().map(this::toDto).toList();
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Movimientos obtenidos correctamente."),
                        Optional.of(dtos)));
    }

    @Transactional
    public ResponseEntity<?> createForBankAccount(Long bankAccountId, CreateBankFinancialMovementRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        User user = userUtil.getUser();
        BankAccount account = assertBankAccount(bankAccountId, user);
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("El importe del movimiento debe ser distinto de cero.");
        }

        BankReconciliationSession session = resolveSessionForCreate(bankAccountId, user, request.getReconciliationSessionId());

        FinancialMovement entity = FinancialMovement.builder()
                .bankAccount(account)
                .company(user.getCompany())
                .movementDate(request.getMovementDate())
                .amount(request.getAmount())
                .description(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null)
                .externalReference(StringUtils.hasText(request.getExternalReference()) ? request.getExternalReference().trim() : null)
                .sourceType(FinancialMovementSourceType.MANUAL)
                .reconciliationSession(session)
                .build();

        financialMovementRepository.save(entity);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Movimiento registrado correctamente."),
                        Optional.of(toDto(entity))));
    }

    @Transactional
    public ResponseEntity<?> importCsv(Long bankAccountId, Long reconciliationSessionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo CSV.");
        }
        User user = userUtil.getUser();
        BankAccount account = assertBankAccount(bankAccountId, user);
        BankReconciliationSession session = resolveSessionForCreate(bankAccountId, user, reconciliationSessionId);

        int imported = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (lineNo == 1 && isCsvHeader(trimmed)) {
                    continue;
                }
                try {
                    String[] p = trimmed.split(";", -1);
                    if (p.length < 2) {
                        p = trimmed.split(",", -1);
                    }
                    if (p.length < 2) {
                        errors.add("Linea " + lineNo + ": formato invalido (use fecha;importe;descripcion;referencia).");
                        continue;
                    }
                    LocalDate d = LocalDate.parse(p[0].trim());
                    String amtRaw = p[1].trim()
                        .replace(" ", "")
                        .replace(",", "")
                        .replace("\"", "");

                    if (amtRaw.startsWith(".")) {
                        amtRaw = "0" + amtRaw;
                    }

                    System.out.println("amtRaw: " + amtRaw);

                    BigDecimal amt = new BigDecimal(amtRaw);
                    if (amt.compareTo(BigDecimal.ZERO) == 0) {
                        errors.add("Linea " + lineNo + ": importe cero omitido.");
                        continue;
                    }
                    String desc = p.length > 2 ? nullIfBlank(p[2]) : null;
                    String ref = p.length > 3 ? nullIfBlank(p[3]) : null;

                    FinancialMovement mov = FinancialMovement.builder()
                            .bankAccount(account)
                            .company(user.getCompany())
                            .movementDate(d)
                            .amount(amt)
                            .description(desc)
                            .externalReference(ref)
                            .sourceType(FinancialMovementSourceType.BANK_IMPORT)
                            .reconciliationSession(session)
                            .build();
                    financialMovementRepository.save(mov);
                    imported++;
                } catch (Exception ex) {
                    errors.add("Linea " + lineNo + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + ex.getMessage());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("imported", imported);
        payload.put("errors", errors);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Importacion finalizada."),
                Optional.of(payload)));
    }

    public ResponseEntity<?> suggestVouchers(Long bankAccountId, Long movementId) {
        User user = userUtil.getUser();
        assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_IdAndCompany_Id(movementId, bankAccountId, user.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));

        LocalDate from = mov.getMovementDate().minusDays(7);
        LocalDate to = mov.getMovementDate().plusDays(7);
        BigDecimal targetAbs = mov.getAmount().abs();

        List<VouchersEntity> candidates = voucherRepository.findReconciliationCandidates(
                bankAccountId, user.getCompany().getId(), from, to);

        List<VoucherMatchSuggestionDTO> suggestions = candidates.stream()
                .filter(v -> v.getAmount() != null && v.getAmount().abs().compareTo(targetAbs) == 0)
                .filter(v -> financialMovementRepository.findByMatchedVoucherId(v.getId()).isEmpty())
                .limit(25)
                .map(v -> VoucherMatchSuggestionDTO.builder()
                        .id(v.getId())
                        .number(v.getNumber() != null ? v.getNumber().toString() : null)
                        .date(v.getDate())
                        .amount(v.getAmount())
                        .description(v.getDescription())
                        .build())
                .toList();

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Sugerencias obtenidas."),
                Optional.of(suggestions)));
    }

    @Transactional
    public ResponseEntity<?> matchVoucher(Long bankAccountId, Long movementId, MatchVoucherRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        User user = userUtil.getUser();
        assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_IdAndCompany_Id(movementId, bankAccountId, user.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));
        if (mov.getMatchedCheckId() != null) {
            throw new IllegalArgumentException("El movimiento ya esta conciliado con un cheque; no puede emparejarse con comprobante.");
        }
        if (mov.getMatchedVoucherId() != null) {
            throw new IllegalArgumentException("El movimiento ya tiene un comprobante asociado.");
        }

        VouchersEntity voucher = null;

        if (request.getVoucherId() == null) {

            VoucherTypesEntity voucherType = voucherTypeRepository.findById(2l).orElseThrow(() -> new IllegalArgumentException("Tipo de comprobante no encontrado."));

            CreateVoucherDTO createVoucherDTO = CreateVoucherDTO.builder()
                .voucherTypeId(voucherType.getId())
                .date(mov.getMovementDate())
                .amount(mov.getAmount())
                .description(mov.getDescription())
                .paymentFormId(1l)
                .bankAccountId(request.getBankAccountId())
                .build();

            voucher = voucherService.createVoucher(createVoucherDTO);
        }else{
            voucher = voucherRepository.findById(request.getVoucherId())
            .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado."));
        }

        if (voucher.getDeletedAt() != null) {
            throw new IllegalArgumentException("Comprobante no disponible.");
        }
        if (voucher.getCompany() == null || !voucher.getCompany().getId().equals(user.getCompany().getId())) {
            throw new IllegalArgumentException("El comprobante no pertenece a su empresa.");
        }
        if (voucher.getBankAccount() == null || !voucher.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("El comprobante no corresponde a esta cuenta bancaria.");
        }
        if (voucher.getAmount() == null || mov.getAmount().abs().compareTo(voucher.getAmount().abs()) != 0) {
            throw new IllegalArgumentException("El importe del comprobante debe coincidir en valor absoluto con el movimiento.");
        }

        Optional<FinancialMovement> otherMov = financialMovementRepository.findByMatchedVoucherId(voucher.getId());
        if (otherMov.isPresent() && !otherMov.get().getId().equals(mov.getId())) {
            throw new IllegalArgumentException("Este comprobante ya esta emparejado con otro movimiento.");
        }

        mov.setMatchedVoucherId(voucher.getId());
        financialMovementRepository.save(mov);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Movimiento emparejado con comprobante."),
                Optional.of(toDto(mov))));
    }

    @Transactional
    public ResponseEntity<?> unmatch(Long bankAccountId, Long movementId) {
        User user = userUtil.getUser();
        assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_IdAndCompany_Id(movementId, bankAccountId, user.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));
        if (mov.getMatchedCheckId() != null) {
            throw new IllegalArgumentException("Movimiento conciliado con cheque: no se puede desemparejar desde aqui.");
        }
        if (mov.getMatchedVoucherId() == null) {
            throw new IllegalArgumentException("El movimiento no tiene comprobante asociado.");
        }
        mov.setMatchedVoucherId(null);
        financialMovementRepository.save(mov);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Emparejamiento con comprobante eliminado."),
                Optional.of(toDto(mov))));
    }

    public Optional<FinancialMovement> findForAutomaticCheckReconcile(Long movementId, Long bankAccountId, Long companyId) {
        return financialMovementRepository.findForCheckReconcile(movementId, bankAccountId, companyId);
    }

    @Transactional
    public void markMatchedToCheck(FinancialMovement movement, Long checkId) {
        movement.setMatchedCheckId(checkId);
        financialMovementRepository.save(movement);
    }

    private BankAccount assertBankAccount(Long bankAccountId, User user) {
        BankAccount account = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-029: Cuenta no encontrada."));
        if (account.getDeletedAt() != null) {
            throw new IllegalArgumentException("BNK-ERR-029: Cuenta no encontrada.");
        }
        if (!account.getCompany().getId().equals(user.getCompany().getId())) {
            throw new IllegalArgumentException("No tiene acceso a esta cuenta bancaria.");
        }
        return account;
    }

    private BankReconciliationSession resolveSessionForCreate(Long bankAccountId, User user, Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        BankReconciliationSession session = reconciliationSessionRepository.findByIdAndCompany_Id(sessionId, user.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Sesion de conciliacion no encontrada."));
        if (!session.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("La sesion no corresponde a esta cuenta.");
        }
        if (session.getStatus() != ReconciliationSessionStatus.DRAFT) {
            throw new IllegalArgumentException("Solo se pueden agregar movimientos a una sesion en borrador.");
        }
        return session;
    }

    private static boolean isCsvHeader(String line) {
        String low = line.toLowerCase();
        return low.contains("fecha") || low.contains("date")
                || low.contains("importe") || low.contains("amount") || low.contains("monto");
    }

    private static String nullIfBlank(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private FinancialMovementDTO toDto(FinancialMovement m) {
        return FinancialMovementDTO.builder()
                .id(m.getId())
                .bankAccountId(m.getBankAccount() != null ? m.getBankAccount().getId() : null)
                .movementDate(m.getMovementDate())
                .amount(m.getAmount())
                .description(m.getDescription())
                .externalReference(m.getExternalReference())
                .sourceType(m.getSourceType())
                .matchedCheckId(m.getMatchedCheckId())
                .matchedVoucherId(m.getMatchedVoucherId())
                .reconciliationSessionId(m.getReconciliationSession() != null ? m.getReconciliationSession().getId() : null)
                .build();
    }
}
