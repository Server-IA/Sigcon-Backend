package com.sigcon.backend.banks.reconciliation.domain.service;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.reconciliation.application.BankReconciliationSessionDTO;
import com.sigcon.backend.banks.reconciliation.application.BankReconciliationSummaryDTO;
import com.sigcon.backend.banks.reconciliation.application.CreateBankReconciliationSessionRequest;
import com.sigcon.backend.banks.reconciliation.application.UpdateBankReconciliationStatementBalancesRequest;
import com.sigcon.backend.banks.reconciliation.domain.model.BankReconciliationSession;
import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import com.sigcon.backend.banks.reconciliation.domain.repository.BankReconciliationSessionRepository;
import com.sigcon.backend.vouchers.domain.repository.VoucherRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankReconciliationSessionService {

    private static final BigDecimal RECON_TOLERANCE = new BigDecimal("0.01");

    private final BankReconciliationSessionRepository sessionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final FinancialMovementRepository financialMovementRepository;
    private final VoucherRepository voucherRepository;
    private final UserUtil userUtil;

    public ResponseEntity<?> listByBankAccount(Long bankAccountId) {
        User user = userUtil.getUser();
        assertAccountAccess(bankAccountId, user);

        List<BankReconciliationSessionDTO> dtos = sessionRepository.findByBankAccount_IdOrderByPeriodEndDesc(bankAccountId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Sesiones obtenidas correctamente."),
                Optional.of(dtos)));
    }

    @Transactional
    public ResponseEntity<?> create(Long bankAccountId, CreateBankReconciliationSessionRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        User user = userUtil.getUser();
        BankAccount account = assertAccountAccess(bankAccountId, user);

        if (request.getPeriodStart().isAfter(request.getPeriodEnd())) {
            throw new IllegalArgumentException("La fecha inicial del periodo no puede ser posterior a la final.");
        }
        if (request.getPeriodEnd().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha final del periodo no puede ser futura.");
        }

        if (sessionRepository.existsByBankAccount_IdAndStatus(bankAccountId, ReconciliationSessionStatus.DRAFT)) {
            throw new IllegalArgumentException("Ya existe una sesion de conciliacion en borrador para esta cuenta; cierrela o continue con esa.");
        }

        BankReconciliationSession entity = BankReconciliationSession.builder()
                .bankAccount(account)
                .company(user.getCompany())
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .statementOpeningBalance(request.getStatementOpeningBalance())
                .statementClosingBalance(request.getStatementClosingBalance())
                .status(ReconciliationSessionStatus.DRAFT)
                .notes(StringUtils.hasText(request.getNotes()) ? request.getNotes().trim() : null)
                .build();

        sessionRepository.save(entity);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Sesion de conciliacion creada."),
                Optional.of(toDto(entity))));
    }

    @Transactional
    public ResponseEntity<?> close(Long bankAccountId, Long sessionId) {
        User user = userUtil.getUser();
        assertAccountAccess(bankAccountId, user);

        BankReconciliationSession session = sessionRepository.findByIdAndCompany_Id(sessionId, user.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Sesion no encontrada."));
        if (!session.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("La sesion no pertenece a esta cuenta bancaria.");
        }
        if (session.getStatus() != ReconciliationSessionStatus.DRAFT) {
            throw new IllegalArgumentException("La sesion ya fue cerrada.");
        }
        if (session.getStatementOpeningBalance() == null || session.getStatementClosingBalance() == null) {
            throw new IllegalArgumentException(
                    "Debe registrar el saldo inicial y el saldo final del extracto antes de cerrar la sesion.");
        }

        session.setStatus(ReconciliationSessionStatus.CLOSED);
        session.setClosedAt(LocalDateTime.now());
        session.setClosedBy(user.getId());
        sessionRepository.save(session);

        BankAccount account = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-029: Cuenta no encontrada."));
        account.setLastReconciliationDate(session.getPeriodEnd());
        bankAccountRepository.save(account);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Sesion cerrada. Fecha de ultima conciliacion de la cuenta actualizada."),
                Optional.of(toDto(session))));
    }

    public ResponseEntity<?> getSummary(Long bankAccountId, Long sessionId) {
        User user = userUtil.getUser();
        BankAccount account = assertAccountAccess(bankAccountId, user);
        BankReconciliationSession session = sessionRepository.findByIdAndCompany_Id(sessionId, user.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Sesion no encontrada."));
        if (!session.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("La sesion no pertenece a esta cuenta bancaria.");
        }

        Long companyId = user.getCompany().getId();
        LocalDate from = session.getPeriodStart();
        LocalDate to = session.getPeriodEnd();

        BigDecimal movementsInPeriod = financialMovementRepository.sumAmountByBankAccountAndPeriod(bankAccountId, companyId, from, to);
        BigDecimal movementsInSession = financialMovementRepository.sumAmountByReconciliationSessionId(sessionId);
        long movCount = financialMovementRepository.countByBankAccountAndPeriod(bankAccountId, companyId, from, to);
        long unmatchedCount = financialMovementRepository.countUnmatchedByBankAccountAndPeriod(bankAccountId, companyId, from, to);
        BigDecimal unmatchedSum = financialMovementRepository.sumUnmatchedAmountByBankAccountAndPeriod(bankAccountId, companyId, from, to);

        BigDecimal voucherSum = voucherRepository.sumVoucherAmountsByBankAccountUpToDate(bankAccountId, companyId, to);
        if (voucherSum == null) {
            voucherSum = BigDecimal.ZERO;
        }
        BigDecimal initial = account.getInitialBalance() != null ? account.getInitialBalance() : BigDecimal.ZERO;
        BigDecimal bookAtEnd = initial.add(voucherSum).setScale(2, RoundingMode.HALF_UP);

        BigDecimal openingStmt = session.getStatementOpeningBalance();
        BigDecimal closingStmt = session.getStatementClosingBalance();

        BigDecimal computedClosing = null;
        if (openingStmt != null && movementsInPeriod != null) {
            computedClosing = openingStmt.add(movementsInPeriod).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal extractDiff = null;
        Boolean extractOk = null;
        if (computedClosing != null && closingStmt != null) {
            extractDiff = computedClosing.subtract(closingStmt).setScale(2, RoundingMode.HALF_UP);
            extractOk = extractDiff.abs().compareTo(RECON_TOLERANCE) <= 0;
        }

        BigDecimal stmtVsBook = null;
        Boolean stmtMatchesBook = null;
        if (closingStmt != null) {
            stmtVsBook = closingStmt.subtract(bookAtEnd).setScale(2, RoundingMode.HALF_UP);
            stmtMatchesBook = stmtVsBook.abs().compareTo(RECON_TOLERANCE) <= 0;
        }

        BankReconciliationSummaryDTO dto = BankReconciliationSummaryDTO.builder()
                .sessionId(session.getId())
                .periodStart(from)
                .periodEnd(to)
                .status(session.getStatus())
                .statementOpeningBalance(openingStmt)
                .statementClosingBalance(closingStmt)
                .movementsInPeriodNetSum(scaleMoney(movementsInPeriod))
                .movementsInPeriodCount(movCount)
                .movementsLinkedToSessionNetSum(scaleMoney(movementsInSession))
                .unmatchedMovementsInPeriodCount(unmatchedCount)
                .unmatchedMovementsInPeriodNetSum(scaleMoney(unmatchedSum))
                .computedClosingFromExtractOpening(computedClosing)
                .extractArithmeticDifference(extractDiff)
                .extractArithmeticOk(extractOk)
                .bankAccountInitialBalance(scaleMoney(initial))
                .voucherMovementsUpToPeriodEndSum(scaleMoney(voucherSum))
                .bookBalanceAtPeriodEnd(bookAtEnd)
                .statementClosingVsBookDifference(stmtVsBook)
                .statementClosingMatchesBook(stmtMatchesBook)
                .build();

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Resumen de cuadre obtenido."),
                Optional.of(dto)));
    }

    @Transactional
    public ResponseEntity<?> updateStatementBalances(
            Long bankAccountId,
            Long sessionId,
            UpdateBankReconciliationStatementBalancesRequest request,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        User user = userUtil.getUser();
        assertAccountAccess(bankAccountId, user);

        BankReconciliationSession session = sessionRepository.findByIdAndCompany_Id(sessionId, user.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("Sesion no encontrada."));
        if (!session.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("La sesion no pertenece a esta cuenta bancaria.");
        }
        if (session.getStatus() != ReconciliationSessionStatus.DRAFT) {
            throw new IllegalArgumentException("Solo se pueden editar saldos de extracto en sesiones en borrador.");
        }
        if (request.getStatementOpeningBalance() != null) {
            session.setStatementOpeningBalance(request.getStatementOpeningBalance());
        }
        if (request.getStatementClosingBalance() != null) {
            session.setStatementClosingBalance(request.getStatementClosingBalance());
        }
        sessionRepository.save(session);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Saldos del extracto actualizados."),
                Optional.of(toDto(session))));
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private BankAccount assertAccountAccess(Long bankAccountId, User user) {
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

    private BankReconciliationSessionDTO toDto(BankReconciliationSession s) {
        return BankReconciliationSessionDTO.builder()
                .id(s.getId())
                .bankAccountId(s.getBankAccount() != null ? s.getBankAccount().getId() : null)
                .periodStart(s.getPeriodStart())
                .periodEnd(s.getPeriodEnd())
                .statementOpeningBalance(s.getStatementOpeningBalance())
                .statementClosingBalance(s.getStatementClosingBalance())
                .status(s.getStatus())
                .notes(s.getNotes())
                .closedAt(s.getClosedAt())
                .closedBy(s.getClosedBy())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
