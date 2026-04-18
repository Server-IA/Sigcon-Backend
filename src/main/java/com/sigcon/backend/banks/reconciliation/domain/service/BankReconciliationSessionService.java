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
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.vouchers.domain.repository.VoucherRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Servicio de gestion de sesiones de conciliacion bancaria.
 * <p>
 * Una sesion de conciliacion compara los movimientos registrados en el sistema (libros)
 * contra los del extracto bancario para un periodo determinado. El flujo es:
 * DRAFT (borrador) → CLOSED (cerrada). Al cerrar, se actualiza la fecha de ultima
 * conciliacion de la cuenta y se genera un asiento de ajuste si hay diferencias.
 * </p>
 *
 * @see com.sigcon.backend.banks.reconciliation.domain.model.BankReconciliationSession
 * @see com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconciliationSessionService {

    /** Tolerancia maxima para considerar que la conciliacion cuadra (diferencias de centavos). */
    private static final BigDecimal RECON_TOLERANCE = new BigDecimal("0.01");

    private final BankReconciliationSessionRepository sessionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final FinancialMovementRepository financialMovementRepository;
    private final VoucherRepository voucherRepository;
    private final JournalEntryService journalEntryService;
    private final UserUtil userUtil;

    /**
     * Lista todas las sesiones de conciliacion de una cuenta bancaria, ordenadas por fecha de fin descendente.
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @return ResponseEntity con lista de BankReconciliationSessionDTO
     * @throws IllegalArgumentException si la cuenta no existe o fue eliminada
     */
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

    /**
     * Crea una nueva sesion de conciliacion bancaria en estado DRAFT.
     * <p>
     * Solo puede existir una sesion en borrador por cuenta a la vez. El periodo
     * no puede tener fechas invertidas ni fecha final futura. Los saldos del extracto
     * (apertura y cierre) se registran en esta etapa o pueden actualizarse despues.
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param request       datos de la sesion (periodo, saldos del extracto, notas)
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con la sesion creada o error de validacion
     * @throws IllegalArgumentException si ya existe una sesion en borrador o el periodo es invalido
     */
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

        // Solo una sesion en borrador por cuenta a la vez para evitar conflictos
        if (sessionRepository.existsByBankAccount_IdAndStatus(bankAccountId, ReconciliationSessionStatus.DRAFT)) {
            throw new IllegalArgumentException("Ya existe una sesion de conciliacion en borrador para esta cuenta; cierrela o continue con esa.");
        }

        BankReconciliationSession entity = BankReconciliationSession.builder()
                .bankAccount(account)
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

    /**
     * Cierra una sesion de conciliacion bancaria.
     * <p>
     * Al cerrar: (1) se valida que los saldos del extracto esten registrados,
     * (2) se cambia el estado a CLOSED con timestamp y usuario, (3) se actualiza
     * la fecha de ultima conciliacion de la cuenta bancaria, y (4) se genera un
     * asiento de ajuste contable si la diferencia entre el extracto y los libros
     * supera la tolerancia ({@link #RECON_TOLERANCE}).
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param sessionId     identificador de la sesion a cerrar
     * @return ResponseEntity con la sesion cerrada o error de validacion
     * @throws IllegalArgumentException si la sesion no esta en DRAFT o faltan saldos
     */
    @Transactional
    public ResponseEntity<?> close(Long bankAccountId, Long sessionId) {
        User user = userUtil.getUser();
        assertAccountAccess(bankAccountId, user);

        BankReconciliationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesion no encontrada."));
        if (!session.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("La sesion no pertenece a esta cuenta bancaria.");
        }
        // Solo sesiones en borrador pueden cerrarse
        if (session.getStatus() != ReconciliationSessionStatus.DRAFT) {
            throw new IllegalArgumentException("La sesion ya fue cerrada.");
        }
        // Saldos del extracto son obligatorios para poder calcular diferencias
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

        // Calcular diferencia final y generar asiento de ajuste si es necesario
        tryCreateReconciliationAdjustmentEntry(session, account, user);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Sesion cerrada. Fecha de ultima conciliacion de la cuenta actualizada."),
                Optional.of(toDto(session))));
    }

    /**
     * Calcula y retorna el resumen de cuadre de una sesion de conciliacion.
     * <p>
     * El resumen incluye dos comparaciones principales:
     * <ol>
     *   <li><b>Aritmetica del extracto:</b> saldo apertura + movimientos del periodo vs saldo cierre del extracto</li>
     *   <li><b>Extracto vs libros:</b> saldo cierre del extracto vs saldo en libros (saldo inicial + comprobantes)</li>
     * </ol>
     * Ambas comparaciones usan una tolerancia de $0.01 para considerar que cuadra.
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param sessionId     identificador de la sesion de conciliacion
     * @return ResponseEntity con BankReconciliationSummaryDTO detallado
     * @throws IllegalArgumentException si la sesion no pertenece a la cuenta
     */
    public ResponseEntity<?> getSummary(Long bankAccountId, Long sessionId) {
        User user = userUtil.getUser();
        BankAccount account = assertAccountAccess(bankAccountId, user);
        BankReconciliationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesion no encontrada."));
        if (!session.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("La sesion no pertenece a esta cuenta bancaria.");
        }

        LocalDate from = session.getPeriodStart();
        LocalDate to = session.getPeriodEnd();

        // Sumar movimientos financieros del periodo (independiente de la sesion)
        BigDecimal movementsInPeriod = financialMovementRepository.sumAmountByBankAccountAndPeriod(bankAccountId, from, to);
        // Sumar solo movimientos vinculados a esta sesion especifica
        BigDecimal movementsInSession = financialMovementRepository.sumAmountByReconciliationSessionId(sessionId);
        long movCount = financialMovementRepository.countByBankAccountAndPeriod(bankAccountId, from, to);
        long unmatchedCount = financialMovementRepository.countUnmatchedByBankAccountAndPeriod(bankAccountId, from, to);
        BigDecimal unmatchedSum = financialMovementRepository.sumUnmatchedAmountByBankAccountAndPeriod(bankAccountId, from, to);

        // Calcular saldo en libros: saldo inicial + suma de comprobantes hasta fin del periodo
        BigDecimal voucherSum = voucherRepository.sumVoucherAmountsByBankAccountUpToDate(bankAccountId, to);
        if (voucherSum == null) {
            voucherSum = BigDecimal.ZERO;
        }
        BigDecimal initial = account.getInitialBalance() != null ? account.getInitialBalance() : BigDecimal.ZERO;
        BigDecimal bookAtEnd = initial.add(voucherSum).setScale(2, RoundingMode.HALF_UP);

        BigDecimal openingStmt = session.getStatementOpeningBalance();
        BigDecimal closingStmt = session.getStatementClosingBalance();

        // Verificacion aritmetica del extracto: apertura + movimientos debe igualar cierre
        BigDecimal computedClosing = null;
        if (openingStmt != null && movementsInPeriod != null) {
            computedClosing = openingStmt.add(movementsInPeriod).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal extractDiff = null;
        Boolean extractOk = null;
        if (computedClosing != null && closingStmt != null) {
            extractDiff = computedClosing.subtract(closingStmt).setScale(2, RoundingMode.HALF_UP);
            // Si la diferencia es menor o igual a la tolerancia, se considera que cuadra
            extractOk = extractDiff.abs().compareTo(RECON_TOLERANCE) <= 0;
        }

        // Comparacion extracto vs libros: saldo cierre extracto vs saldo contable calculado
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

    /**
     * Actualiza los saldos del extracto bancario (apertura y/o cierre) en una sesion en borrador.
     * <p>
     * Solo se pueden modificar saldos mientras la sesion este en estado DRAFT. Estos saldos
     * provienen del extracto bancario fisico y son necesarios para el calculo de diferencias.
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param sessionId     identificador de la sesion
     * @param request       saldos de apertura y/o cierre a actualizar
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con la sesion actualizada o error si no esta en DRAFT
     */
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

        BankReconciliationSession session = sessionRepository.findById(sessionId)
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

    /**
     * Calcula la diferencia de conciliacion y crea un asiento de ajuste si es necesario.
     * La diferencia se calcula como: saldo cierre extracto - (saldo apertura + suma movimientos conciliados).
     * Si la diferencia es distinta de cero, se genera un asiento contable de ajuste.
     * No interrumpe la operacion principal en caso de error.
     */
    private void tryCreateReconciliationAdjustmentEntry(BankReconciliationSession session, BankAccount account, User user) {
        try {
            if (session.getStatementOpeningBalance() == null || session.getStatementClosingBalance() == null) {
                return;
            }

            // Sumar todos los movimientos vinculados a esta sesion de conciliacion
            BigDecimal matchedMovementsSum = financialMovementRepository.sumAmountByReconciliationSessionId(session.getId());
            if (matchedMovementsSum == null) {
                matchedMovementsSum = BigDecimal.ZERO;
            }

            // Calculo: saldo esperado = apertura extracto + movimientos conciliados
            BigDecimal expectedClosing = session.getStatementOpeningBalance().add(matchedMovementsSum);
            // Diferencia = saldo cierre extracto - saldo esperado calculado
            BigDecimal difference = session.getStatementClosingBalance().subtract(expectedClosing)
                    .setScale(2, RoundingMode.HALF_UP);

            // Si la diferencia esta dentro de la tolerancia ($0.01), no se genera asiento
            if (difference.abs().compareTo(RECON_TOLERANCE) <= 0) {
                log.info("Conciliacion sesion ID={} sin diferencia significativa.", session.getId());
                return;
            }

            if (account.getAccountingAccount() == null) {
                log.warn("Cuenta bancaria ID={} sin cuenta contable, no se genera asiento de ajuste.", account.getId());
                return;
            }

            Long accountingAccountId = account.getAccountingAccount().getId();
            BigDecimal absAmount = difference.abs();

            // Diferencia positiva = faltante en libros (debitar banco para aumentar saldo).
            // Diferencia negativa = sobrante en libros (acreditar banco para disminuir saldo).
            CreateJournalEntryLineRequest debitLine = CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(accountingAccountId)
                    .debitAmount(difference.compareTo(BigDecimal.ZERO) > 0 ? absAmount : BigDecimal.ZERO)
                    .creditAmount(difference.compareTo(BigDecimal.ZERO) < 0 ? absAmount : BigDecimal.ZERO)
                    .description("Ajuste conciliacion bancaria")
                    .build();

            CreateJournalEntryLineRequest contraLine = CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(accountingAccountId)
                    .debitAmount(difference.compareTo(BigDecimal.ZERO) < 0 ? absAmount : BigDecimal.ZERO)
                    .creditAmount(difference.compareTo(BigDecimal.ZERO) > 0 ? absAmount : BigDecimal.ZERO)
                    .description("Contrapartida ajuste conciliacion bancaria")
                    .build();

            String periodDesc = session.getPeriodStart() + " a " + session.getPeriodEnd();
            CreateJournalEntryRequest entryRequest = CreateJournalEntryRequest.builder()
                    .entryDate(session.getPeriodEnd())
                    .description("Ajuste conciliacion bancaria periodo " + periodDesc)
                    .sourceModule(JournalSourceModule.BNK)
                    .sourceId(session.getId())
                    .lines(List.of(debitLine, contraLine))
                    .build();

            String createdBy = user != null ? user.getName() : "SISTEMA";
            journalEntryService.createEntry(entryRequest, createdBy);
            log.info("Asiento de ajuste creado para conciliacion sesion ID={}, diferencia={}", session.getId(), difference);

        } catch (Exception e) {
            log.warn("No se pudo crear asiento de ajuste para conciliacion sesion ID={}: {}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * Escala un valor monetario a 2 decimales con redondeo HALF_UP.
     * Retorna BigDecimal.ZERO si el valor es null.
     */
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
