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
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.sigcon.backend.vouchers.application.CreateVoucherDTO;
import com.sigcon.backend.vouchers.domain.models.VoucherTypesEntity;
import com.sigcon.backend.vouchers.domain.models.VouchersEntity;
import com.sigcon.backend.vouchers.domain.repository.VoucherRepository;
import com.sigcon.backend.vouchers.domain.repository.VoucherTypeRepository;
import com.sigcon.backend.vouchers.domain.service.VoucherService;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.extern.slf4j.Slf4j;

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

/**
 * Servicio de gestion de movimientos financieros bancarios.
 * <p>
 * Gestiona el ciclo de vida de los movimientos financieros: creacion manual,
 * importacion masiva desde CSV (extracto bancario), emparejamiento con comprobantes
 * contables y conciliacion con cheques. Cada movimiento manual genera automaticamente
 * un asiento contable via {@link JournalEntryService}.
 * </p>
 *
 * @see com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement
 * @see com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialMovementService {

    private final FinancialMovementRepository financialMovementRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BankReconciliationSessionRepository reconciliationSessionRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherTypeRepository voucherTypeRepository;
    private final VoucherService voucherService;
    private final JournalEntryService journalEntryService;
    private final JournalEntryRepository journalEntryRepository;
    private final AuditPublisher auditPublisher;

    private final UserUtil userUtil;

    /**
     * Lista los movimientos financieros de una cuenta bancaria.
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param unmatchedOnly si es true, retorna solo movimientos sin emparejar (sin comprobante ni cheque)
     * @return ResponseEntity con lista de FinancialMovementDTO
     * @throws IllegalArgumentException si la cuenta no existe o fue eliminada
     */
    /**
     * Busqueda paginada (DataTable) de TODOS los movimientos del tenant.
     * No requiere bankAccountId — multi-tenant filter ya restringe por empresa.
     */
    public ResponseEntity<?> search(DataTableRequest request) {
        try {
            int length = request.getLength() > 0 && request.getLength() <= 100 ? request.getLength() : 20;
            int start  = Math.max(request.getStart(), 0);
            int page   = start / length;
            Pageable pageable = PageRequest.of(page, length, Sort.by(Sort.Direction.DESC, "movementDate", "id"));
            Specification<FinancialMovement> spec = new DataTableSpecificationBuilder<FinancialMovement>().build(request);
            Page<FinancialMovement> p = financialMovementRepository.findAll(spec, pageable);
            DataTableResponse<FinancialMovementDTO> resp = new DataTableResponse<>();
            resp.setDraw(request.getDraw());
            resp.setRecordsTotal(p.getTotalElements());
            resp.setRecordsFiltered(p.getTotalElements());
            resp.setData(p.getContent().stream().map(this::toDto).toList());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("FinancialMovementService.search: error", e);
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(java.util.Optional.of("Error al buscar movimientos: " + e.getMessage())));
        }
    }

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

    /**
     * Crea un movimiento financiero manual para una cuenta bancaria.
     * <p>
     * El movimiento se registra con tipo de origen MANUAL y opcionalmente se vincula
     * a una sesion de conciliacion en estado DRAFT. Tras guardar, se intenta crear
     * un asiento contable automatico (partida doble) usando la cuenta contable
     * asociada a la cuenta bancaria. Si la creacion del asiento falla, el movimiento
     * se guarda igualmente (el asiento es complementario, no bloqueante).
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param request       datos del movimiento (fecha, importe, descripcion, referencia)
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con el movimiento creado o error de validacion
     * @throws IllegalArgumentException si el importe es cero o la cuenta no existe
     */
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

        // Vincular opcionalmente a una sesion de conciliacion (solo si esta en borrador)
        BankReconciliationSession session = resolveSessionForCreate(bankAccountId, user, request.getReconciliationSessionId());

        FinancialMovement entity = FinancialMovement.builder()
                .bankAccount(account)
                .movementDate(request.getMovementDate())
                .amount(request.getAmount())
                .description(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null)
                .externalReference(StringUtils.hasText(request.getExternalReference()) ? request.getExternalReference().trim() : null)
                .flowActivity(StringUtils.hasText(request.getFlowActivity()) ? request.getFlowActivity().trim() : null)
                .sourceType(FinancialMovementSourceType.MANUAL)
                .reconciliationSession(session)
                .build();

        financialMovementRepository.save(entity);
        auditPublisher.publishCreate(AuditModule.BNK, "FinancialMovement", entity.getId(), "FinancialMovement creado id=" + entity.getId());

        // Intentar crear asiento contable automatico via JournalEntryService.
        // Si falla (ej: cuenta sin cuenta contable, periodo cerrado), solo se logea warning.
        tryCreateJournalEntry(entity, account, user);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Movimiento registrado correctamente."),
                        Optional.of(toDto(entity))));
    }

    /**
     * Importa movimientos financieros desde un archivo CSV (extracto bancario).
     * <p>
     * Formato esperado del CSV: {@code fecha;importe;descripcion;referencia} (separador ; o ,).
     * La primera linea se omite si parece ser encabezado. Cada linea se procesa de forma
     * independiente: si una falla, se registra el error y se continua con las siguientes.
     * Los movimientos importados se marcan con tipo de origen BANK_IMPORT.
     * </p>
     *
     * @param bankAccountId          identificador de la cuenta bancaria destino
     * @param reconciliationSessionId identificador de sesion de conciliacion (opcional)
     * @param file                   archivo CSV con los movimientos a importar
     * @return ResponseEntity con cantidad importada y lista de errores por linea
     * @throws IllegalArgumentException si el archivo es nulo, vacio o no se puede leer
     */
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
                // Omitir encabezado si la primera linea contiene palabras clave como "fecha", "importe", etc.
                if (lineNo == 1 && isCsvHeader(trimmed)) {
                    continue;
                }
                try {
                    // Intentar separar por punto y coma primero (formato comun en Colombia), luego por coma
                    String[] p = trimmed.split(";", -1);
                    if (p.length < 2) {
                        p = trimmed.split(",", -1);
                    }
                    if (p.length < 2) {
                        errors.add("Linea " + lineNo + ": formato invalido (use fecha;importe;descripcion;referencia).");
                        continue;
                    }
                    LocalDate d = LocalDate.parse(p[0].trim());
                    // Limpiar el importe: quitar espacios, comas de miles y comillas
                    String amtRaw = p[1].trim()
                        .replace(" ", "")
                        .replace(",", "")
                        .replace("\"", "");

                    // Corregir importes que inician con punto decimal (ej: ".50" → "0.50")
                    if (amtRaw.startsWith(".")) {
                        amtRaw = "0" + amtRaw;
                    }

                    System.out.println("amtRaw: " + amtRaw);

                    BigDecimal amt = new BigDecimal(amtRaw);
                    // Movimientos con importe cero no tienen sentido contable, se omiten
                    if (amt.compareTo(BigDecimal.ZERO) == 0) {
                        errors.add("Linea " + lineNo + ": importe cero omitido.");
                        continue;
                    }
                    String desc = p.length > 2 ? nullIfBlank(p[2]) : null;
                    String ref = p.length > 3 ? nullIfBlank(p[3]) : null;

                    FinancialMovement mov = FinancialMovement.builder()
                            .bankAccount(account)
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

    /**
     * Sugiere comprobantes contables candidatos para emparejar con un movimiento bancario.
     * <p>
     * Busca comprobantes de la misma cuenta bancaria cuya fecha este dentro de una ventana
     * de +/- 7 dias respecto a la fecha del movimiento, con el mismo importe en valor absoluto
     * y que no esten ya emparejados con otro movimiento.
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param movementId    identificador del movimiento a emparejar
     * @return ResponseEntity con lista de hasta 25 sugerencias de comprobantes
     * @throws IllegalArgumentException si el movimiento o la cuenta no existen
     */
    public ResponseEntity<?> suggestVouchers(Long bankAccountId, Long movementId) {
        User user = userUtil.getUser();
        assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_Id(movementId, bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));

        // Ventana de busqueda: 7 dias antes y despues de la fecha del movimiento
        LocalDate from = mov.getMovementDate().minusDays(7);
        LocalDate to = mov.getMovementDate().plusDays(7);
        BigDecimal targetAbs = mov.getAmount().abs();

        List<VouchersEntity> candidates = voucherRepository.findReconciliationCandidates(
                bankAccountId, from, to);

        // Filtrar: mismo importe absoluto y que no esten ya emparejados con otro movimiento
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

    /**
     * Empareja un movimiento financiero con un comprobante contable existente o crea uno nuevo.
     * <p>
     * Si {@code request.getVoucherId()} es null, se crea automaticamente un comprobante
     * de egreso (tipo ID=2) con los datos del movimiento. Si se proporciona un ID, se
     * valida que el comprobante pertenezca a la misma cuenta bancaria y que sus importes
     * coincidan en valor absoluto. Un movimiento solo puede emparejarse con un comprobante
     * a la vez (relacion 1:1), y no puede emparejarse si ya esta conciliado con un cheque.
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param movementId    identificador del movimiento a emparejar
     * @param request       contiene el ID del comprobante o null para crear uno nuevo
     * @param bindingResult resultado de validacion de campos
     * @return ResponseEntity con el movimiento actualizado o error de validacion
     * @throws IllegalArgumentException si el movimiento ya esta emparejado o los importes no coinciden
     */
    @Transactional
    public ResponseEntity<?> matchVoucher(Long bankAccountId, Long movementId, MatchVoucherRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        User user = userUtil.getUser();
        assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_Id(movementId, bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));
        // Un movimiento conciliado con cheque no puede tener comprobante adicional (exclusion mutua)
        if (mov.getMatchedCheckId() != null) {
            throw new IllegalArgumentException("El movimiento ya esta conciliado con un cheque; no puede emparejarse con comprobante.");
        }
        if (mov.getMatchedVoucherId() != null) {
            throw new IllegalArgumentException("El movimiento ya tiene un comprobante asociado.");
        }

        VouchersEntity voucher = null;

        if (request.getVoucherId() == null) {
            // Si no se proporciona comprobante, crear uno automaticamente (tipo egreso, forma pago contado)
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
        // El comprobante debe pertenecer a la misma cuenta bancaria que el movimiento
        if (voucher.getBankAccount() == null || !voucher.getBankAccount().getId().equals(bankAccountId)) {
            throw new IllegalArgumentException("El comprobante no corresponde a esta cuenta bancaria.");
        }
        // Los importes deben coincidir en valor absoluto (el signo puede diferir)
        if (voucher.getAmount() == null || mov.getAmount().abs().compareTo(voucher.getAmount().abs()) != 0) {
            throw new IllegalArgumentException("El importe del comprobante debe coincidir en valor absoluto con el movimiento.");
        }

        // Verificar que el comprobante no este ya emparejado con otro movimiento (relacion 1:1)
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

    /**
     * Elimina el emparejamiento entre un movimiento financiero y su comprobante contable.
     * <p>
     * Solo aplica para emparejamientos con comprobantes. Si el movimiento esta conciliado
     * con un cheque, se debe desemparejar desde el modulo de cheques.
     * </p>
     *
     * @param bankAccountId identificador de la cuenta bancaria
     * @param movementId    identificador del movimiento a desemparejar
     * @return ResponseEntity con el movimiento actualizado o error si no tiene comprobante
     * @throws IllegalArgumentException si el movimiento esta conciliado con cheque o no tiene comprobante
     */

    /**
     * QA-BLOQUE-AP (2026-04-29): sugiere JournalEntries POSTED que tienen al
     * menos una linea sobre la cuenta PUC del banco, en ventana de fecha
     * +/-7 dias y mismo importe absoluto que el movimiento. Para empresas
     * que operan con asientos contables modernos en lugar de Vouchers legacy.
     */
    public ResponseEntity<?> suggestJournalEntries(Long bankAccountId, Long movementId) {
        User user = userUtil.getUser();
        BankAccount bankAccount = assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_Id(movementId, bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));

        Long pucAccountId = bankAccount.getAccountingAccount() != null
                ? bankAccount.getAccountingAccount().getId() : null;
        if (pucAccountId == null) {
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("La cuenta bancaria no tiene cuenta PUC asociada."),
                    Optional.of(java.util.Collections.emptyList())));
        }

        LocalDate from = mov.getMovementDate().minusDays(7);
        LocalDate to = mov.getMovementDate().plusDays(7);

        List<JournalEntry> candidates = journalEntryRepository.findReconciliationCandidatesByAccount(
                pucAccountId, bankAccount.getCompanyId(), from, to);

        // Filtrar JEs ya emparejados con otro movimiento + retornar DTO ligero
        List<VoucherMatchSuggestionDTO> suggestions = candidates.stream()
                .filter(je -> financialMovementRepository.findByMatchedJournalEntryId(je.getId()).isEmpty())
                .limit(50)
                .map(je -> VoucherMatchSuggestionDTO.builder()
                        .id(je.getId())
                        .number(je.getEntryNumber() != null
                                ? "JE-" + je.getFiscalYear() + "-" + je.getEntryNumber()
                                : ("#" + je.getId()))
                        .date(je.getEntryDate())
                        .amount(je.getTotalDebit() != null ? je.getTotalDebit() : BigDecimal.ZERO)
                        .description(je.getDescription())
                        .build())
                .toList();

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Sugerencias obtenidas."),
                Optional.of(suggestions)));
    }

    /**
     * QA-BLOQUE-AP (2026-04-29): empareja un movimiento financiero con un
     * JournalEntry. Reusa el mismo DTO MatchVoucherRequest porque el campo
     * voucherId se interpreta como journalEntryId en este contexto.
     */
    @Transactional
    public ResponseEntity<?> matchJournalEntry(Long bankAccountId, Long movementId, MatchVoucherRequest request) {
        User user = userUtil.getUser();
        BankAccount bankAccount = assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_Id(movementId, bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));

        if (mov.getMatchedCheckId() != null) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El movimiento ya esta conciliado con un cheque y no puede emparejarse con un asiento.")));
        }
        if (mov.getMatchedVoucherId() != null) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El movimiento ya esta emparejado con un comprobante (voucher).")));
        }
        if (mov.getMatchedJournalEntryId() != null) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El movimiento ya esta emparejado con un asiento contable.")));
        }

        Long jeId = request != null ? request.getVoucherId() : null;
        if (jeId == null) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("Debe indicar el ID del asiento contable a emparejar.")));
        }

        JournalEntry je = journalEntryRepository.findById(jeId)
                .orElseThrow(() -> new IllegalArgumentException("Asiento contable no encontrado."));

        // Validar tenant
        if (!java.util.Objects.equals(je.getCompanyId(), bankAccount.getCompanyId())) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El asiento contable pertenece a otra empresa.")));
        }

        // Validar que no este ya emparejado con otro movimiento
        if (financialMovementRepository.findByMatchedJournalEntryId(jeId).isPresent()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("El asiento ya esta emparejado con otro movimiento financiero.")));
        }

        mov.setMatchedJournalEntryId(jeId);
        FinancialMovement saved = financialMovementRepository.save(mov);

        auditPublisher.publishUpdate(
                com.sigcon.backend.audit.domain.model.enums.AuditModule.BNK,
                "FinancialMovement", saved.getId(),
                "Emparejado con JournalEntry id=" + jeId);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Movimiento emparejado con asiento contable."),
                Optional.of(toDto(saved))));
    }

    @Transactional
    public ResponseEntity<?> unmatch(Long bankAccountId, Long movementId) {
        User user = userUtil.getUser();
        assertBankAccount(bankAccountId, user);

        FinancialMovement mov = financialMovementRepository.findByIdAndBankAccount_Id(movementId, bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Movimiento no encontrado."));
        if (mov.getMatchedCheckId() != null) {
            throw new IllegalArgumentException("Movimiento conciliado con cheque: no se puede desemparejar desde aqui.");
        }
        // QA-BLOQUE-AP (2026-04-29): unmatch limpia ambos campos. Si el movimiento
        // estaba emparejado con JE en lugar de Voucher, tambien lo libera.
        if (mov.getMatchedVoucherId() == null && mov.getMatchedJournalEntryId() == null) {
            throw new IllegalArgumentException("El movimiento no tiene comprobante asociado.");
        }
        mov.setMatchedVoucherId(null);
        mov.setMatchedJournalEntryId(null);
        financialMovementRepository.save(mov);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Emparejamiento con comprobante eliminado."),
                Optional.of(toDto(mov))));
    }

    /**
     * Busca un movimiento financiero candidato para conciliacion automatica con cheque.
     * Retorna el movimiento solo si no tiene cheque ni comprobante ya asignado.
     *
     * @param movementId    identificador del movimiento
     * @param bankAccountId identificador de la cuenta bancaria
     * @return Optional con el movimiento si cumple condiciones, vacio si no
     */
    public Optional<FinancialMovement> findForAutomaticCheckReconcile(Long movementId, Long bankAccountId) {
        return financialMovementRepository.findForCheckReconcile(movementId, bankAccountId);
    }

    /**
     * QA HU-026: matching automatico de movimientos sin cruzar contra
     * comprobantes (vouchers) por monto exacto y fecha cercana (+/- N dias).
     * <p>
     * No cruza si el voucher ya esta cruzado con otro movimiento o si el monto
     * no coincide. Reporta cuantos quedaron pendientes para revision manual.
     */
    @Transactional
    public ResponseEntity<?> autoMatchMovements(Long bankAccountId, Integer dateToleranceDays) {
        int tolerance = dateToleranceDays != null && dateToleranceDays >= 0 ? dateToleranceDays : 3;

        // Movimientos no cruzados (sin cheque ni comprobante)
        java.util.List<FinancialMovement> candidates;
        if (bankAccountId != null) {
            candidates = financialMovementRepository.findAllByBankAccountIdOrdered(bankAccountId).stream()
                    .filter(m -> m.getMatchedCheckId() == null && m.getMatchedVoucherId() == null)
                    .toList();
        } else {
            candidates = financialMovementRepository.findAll().stream()
                    .filter(m -> m.getMatchedCheckId() == null && m.getMatchedVoucherId() == null)
                    .toList();
        }

        int matched = 0;
        int skipped = 0;
        java.util.List<java.util.Map<String, Object>> details = new java.util.ArrayList<>();
        for (FinancialMovement mov : candidates) {
            if (mov.getMovementDate() == null || mov.getAmount() == null) {
                skipped++;
                continue;
            }
            java.time.LocalDate from = mov.getMovementDate().minusDays(tolerance);
            java.time.LocalDate to = mov.getMovementDate().plusDays(tolerance);
            // Buscar vouchers de la misma cuenta con monto coincidente y fecha en rango
            java.util.List<VouchersEntity> vouchers = mov.getBankAccount() != null
                    ? voucherRepository.findAll().stream()
                        .filter(v -> v.getDeletedAt() == null
                                && v.getBankAccount() != null
                                && v.getBankAccount().getId().equals(mov.getBankAccount().getId())
                                && v.getAmount() != null
                                && v.getAmount().abs().compareTo(mov.getAmount().abs()) == 0
                                && v.getDate() != null
                                && !v.getDate().isBefore(from)
                                && !v.getDate().isAfter(to))
                        .toList()
                    : java.util.List.of();
            // Filtrar los ya emparejados con otro movimiento
            java.util.List<VouchersEntity> available = vouchers.stream()
                    .filter(v -> financialMovementRepository.findByMatchedVoucherId(v.getId())
                                .map(other -> other.getId().equals(mov.getId()))
                                .orElse(true))
                    .toList();

            if (available.size() == 1) {
                mov.setMatchedVoucherId(available.get(0).getId());
                financialMovementRepository.save(mov);
                matched++;
                details.add(java.util.Map.of(
                        "movementId", mov.getId(),
                        "voucherId", available.get(0).getId(),
                        "amount", mov.getAmount(),
                        "date", String.valueOf(mov.getMovementDate())));
            } else {
                // 0 candidates -> skip; >1 candidates -> ambiguo, requiere revision manual
                skipped++;
            }
        }

        java.util.Map<String, Object> result = java.util.Map.of(
                "evaluated", candidates.size(),
                "matched", matched,
                "skipped", skipped,
                "toleranceDays", tolerance,
                "matches", details);

        auditPublisher.publish(
                com.sigcon.backend.audit.domain.model.enums.AuditAction.UPDATE,
                AuditModule.BNK,
                com.sigcon.backend.audit.domain.model.enums.AuditSeverity.MEDIUM,
                "FinancialMovement", null,
                "Auto-match HU-026: " + matched + " emparejados de " + candidates.size() + " candidatos",
                null, null, null);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Matching automatico ejecutado: " + matched + " emparejados, "
                        + skipped + " requieren revision manual."),
                Optional.of(result)));
    }

    /**
     * Marca un movimiento financiero como conciliado con un cheque especifico.
     * Usado internamente por el servicio de cheques durante la conciliacion automatica.
     *
     * @param movement movimiento a marcar
     * @param checkId  identificador del cheque conciliado
     */
    @Transactional
    public void markMatchedToCheck(FinancialMovement movement, Long checkId) {
        movement.setMatchedCheckId(checkId);
        financialMovementRepository.save(movement);
    }

    /**
     * Verifica que la cuenta bancaria existe y no esta eliminada.
     * Lanza excepcion si no cumple.
     */
    private BankAccount assertBankAccount(Long bankAccountId, User user) {
        BankAccount account = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-029: Cuenta no encontrada."));
        if (account.getDeletedAt() != null) {
            throw new IllegalArgumentException("BNK-ERR-029: Cuenta no encontrada.");
        }
        return account;
    }

    /**
     * Resuelve la sesion de conciliacion para vincular el movimiento.
     * Si sessionId es null, retorna null (movimiento sin sesion).
     * Solo permite vincular a sesiones en estado DRAFT de la misma cuenta.
     */
    private BankReconciliationSession resolveSessionForCreate(Long bankAccountId, User user, Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        BankReconciliationSession session = reconciliationSessionRepository.findById(sessionId)
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

    /**
     * Intenta crear un asiento contable asociado al movimiento financiero manual.
     * En caso de error, solo se registra en log y no se interrumpe la operacion principal.
     */
    private void tryCreateJournalEntry(FinancialMovement movement, BankAccount account, User user) {
        try {
            if (account.getAccountingAccount() == null) {
                log.warn("Movimiento {} sin cuenta contable asociada, no se genera asiento.", movement.getId());
                return;
            }

            Long accountingAccountId = account.getAccountingAccount().getId();
            BigDecimal amount = movement.getAmount().abs();

            // Partida doble: positivo = ingreso (debito banco), negativo = egreso (credito banco).
            // La contrapartida usa la misma cuenta contable (simplificacion; idealmente deberia
            // usar una cuenta puente configurable por la empresa).
            CreateJournalEntryLineRequest debitLine = CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(accountingAccountId)
                    .debitAmount(movement.getAmount().compareTo(BigDecimal.ZERO) > 0 ? amount : BigDecimal.ZERO)
                    .creditAmount(movement.getAmount().compareTo(BigDecimal.ZERO) < 0 ? amount : BigDecimal.ZERO)
                    .description(movement.getDescription())
                    .build();

            CreateJournalEntryLineRequest contraLine = CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(accountingAccountId)
                    .debitAmount(movement.getAmount().compareTo(BigDecimal.ZERO) < 0 ? amount : BigDecimal.ZERO)
                    .creditAmount(movement.getAmount().compareTo(BigDecimal.ZERO) > 0 ? amount : BigDecimal.ZERO)
                    .description("Contrapartida: " + (movement.getDescription() != null ? movement.getDescription() : "Movimiento financiero manual"))
                    .build();

            CreateJournalEntryRequest entryRequest = CreateJournalEntryRequest.builder()
                    .entryDate(movement.getMovementDate())
                    .description("Movimiento financiero manual - " + (movement.getDescription() != null ? movement.getDescription() : ""))
                    .sourceModule(JournalSourceModule.BNK)
                    .sourceId(movement.getId())
                    .lines(java.util.List.of(debitLine, contraLine))
                    .build();

            String createdBy = user != null ? user.getName() : "SISTEMA";
            journalEntryService.createEntry(entryRequest, createdBy);
            log.info("Asiento contable creado para movimiento financiero ID={}", movement.getId());

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para movimiento ID={}: {}", movement.getId(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar el movimiento: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para movimiento ID={}", movement.getId(), e);
            throw e;
        }
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
                .flowActivity(m.getFlowActivity())
                .matchedCheckId(m.getMatchedCheckId())
                .matchedVoucherId(m.getMatchedVoucherId())
                .reconciliationSessionId(m.getReconciliationSession() != null ? m.getReconciliationSession().getId() : null)
                .build();
    }
}
