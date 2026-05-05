package com.sigcon.backend.banks.checks.domain.service;

import com.sigcon.backend.banks.checkbooks.domain.model.Checkbook;
import com.sigcon.backend.banks.checkbooks.domain.repository.CheckbookRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.financialmovements.domain.service.FinancialMovementService;
import com.sigcon.backend.banks.checks.application.CheckDTO;
import com.sigcon.backend.banks.checks.application.CheckbookDTO;
import com.sigcon.backend.banks.checks.application.EmitCheckRequest;
import com.sigcon.backend.banks.checks.application.ReconcileCheckRequest;
import com.sigcon.backend.banks.checks.application.ReportLostCheckRequest;
import com.sigcon.backend.banks.checks.application.VoidCheckRequest;
import com.sigcon.backend.banks.checks.domain.model.Check;
import com.sigcon.backend.banks.checks.domain.model.enums.CheckStatus;
import com.sigcon.backend.banks.checks.domain.model.enums.CheckType;
import com.sigcon.backend.banks.checks.domain.model.enums.ConciliationMethod;
import com.sigcon.backend.banks.checks.domain.repository.CheckRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckService {

    private static final int MAX_PAGE_SIZE = 200;

    private final CheckRepository checkRepository;
    private final CheckbookRepository checkbookRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FinancialMovementService financialMovementService;
    private final FinancialMovementRepository financialMovementRepository;
    private final AuditPublisher auditPublisher;


    private final DataTableSpecificationBuilder<Check> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

    @Transactional
    public ResponseEntity<?> emit(EmitCheckRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        validateIssueRules(request);
        Checkbook checkbook = resolveCheckbook(request.getCheckbookId());

        if (checkRepository.existsByNumberCheckAndDeletedAtIsNull(request.getNumberCheck())) {
            throw new IllegalArgumentException("BNK-ERR-091: Numero de cheque ya ha sido emitido");
        }

        // HU-018 E4 (Bloque AO): validar fondos disponibles (saldo + sobregiro permitido).
        if (checkbook.getBankAccount() != null) {
            var account = checkbook.getBankAccount();
            BigDecimal initial = account.getInitialBalance() != null ? account.getInitialBalance() : BigDecimal.ZERO;
            BigDecimal moves = financialMovementRepository.sumAmountByBankAccountId(account.getId());
            BigDecimal available = initial.add(moves != null ? moves : BigDecimal.ZERO);
            if (Boolean.TRUE.equals(account.getAllowsOverdraft()) && account.getCreditLimit() != null) {
                available = available.add(account.getCreditLimit());
            }
            if (available.compareTo(request.getValue()) < 0) {
                throw new IllegalArgumentException("Fondos insuficientes. El saldo disponible es de $" + available);
            }
        }

        String supportPath = null;
        String supportMime = null;
        CheckType checkType = request.getTypeCheck() == null ? CheckType.FISICO : request.getTypeCheck();

        if (checkType == CheckType.VIRTUAL) {
            if (request.getSupportDocumentBase64() == null || request.getSupportDocumentBase64().isBlank()) {
                throw new IllegalArgumentException("BNK-ERR-097: Para cheques virtuales debe adjuntar documento soporte");
            }
            byte[] decoded = decodeBase64Payload(request.getSupportDocumentBase64());
            supportMime = resolveSupportedMime(request.getSupportDocumentBase64(), decoded);
            String extension = extensionByMime(supportMime);
            supportPath = buildVirtualDocumentPath(request.getNumberCheck(), extension);
        }

        Check check = Check.builder()
                .checkbook(checkbook)
                .numberCheck(request.getNumberCheck())
                .beneficiary(request.getBeneficiary().trim())
                .value(request.getValue())
                .concept(request.getConcept().trim())
                .issueDate(request.getIssueDate())
                .typeCheck(checkType)
                .statusCheck(CheckStatus.EMITIDO)
                .observations(emptyToNull(request.getObservations()))
                .supportDocumentPath(supportPath)
                .supportDocumentMime(supportMime)
                .blockPayment(false)
                .build();

        checkRepository.save(check);
        auditPublisher.publishCreate(AuditModule.BNK, "Check", check.getId(),
                "Cheque emitido #" + check.getNumberCheck() + " por $" + check.getValue()
                        + " a " + check.getBeneficiary());

        // HU-015 E6 (Bloque AO): recalcular usados/disponibles y marcar AGOTADA si es el ultimo cheque.
        int totalChecks = checkbook.getTotalChecks() != null ? checkbook.getTotalChecks() : 0;
        int usedAfterEmit = (int) checkRepository.countByCheckbook_Id(checkbook.getId());
        checkbook.setUsedChecks(usedAfterEmit);
        checkbook.setAvailableChecks(totalChecks - usedAfterEmit);
        if (totalChecks > 0 && (totalChecks - usedAfterEmit) <= 0) {
            checkbook.setStatus(com.sigcon.backend.banks.checkbooks.domain.model.enums.CheckbookStatus.AGOTADA);
        }
        checkbookRepository.save(checkbook);

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cheque emitido exitosamente - Numero: " + check.getNumberCheck()),
                Optional.of(toDto(check))));
    }

    public ResponseEntity<?> findAllPaged(DataTableRequest request) {
        DataTableRequest safeRequest = normalizeDataTableRequest(request);

        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, MAX_PAGE_SIZE);
        int page = start / safeLength;

        Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);

        Specification<Check> specification = dataTableSpecificationBuilder.build(safeRequest)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<Check> checks = checkRepository.findAll(specification, pageable);
        return ResponseEntity.ok(DataTableResponse.from(checks.map(this::toDto), safeRequest.getDraw()));
    }

    public ResponseEntity<?> getDetail(Long id) {
        Check check = getCheckOrThrow(id);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Detalle de cheque obtenido correctamente."),
                Optional.of(toDto(check))));
    }

    @Transactional
    public ResponseEntity<?> voidCheck(Long id, VoidCheckRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        Check check = getCheckOrThrow(id);
        if (check.getStatusCheck() == CheckStatus.COBRADO) {
            throw new IllegalArgumentException("No se puede anular un cheque ya cobrado");
        }
        if (check.getStatusCheck() != CheckStatus.EMITIDO) {
            throw new IllegalArgumentException("Cheque no disponible para anulacion");
        }

        validateCurrentPassword(request.getCurrentPassword());

        check.setStatusCheck(CheckStatus.ANULADO);
        check.setVoidReason(request.getVoidReason().trim());
        check.setVoidedAt(LocalDateTime.now());

        checkRepository.save(check);
        auditPublisher.publishUpdate(AuditModule.BNK, "Check", check.getId(),
                "Cheque #" + check.getNumberCheck() + " anulado. Motivo: " + check.getVoidReason());

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cheque anulado exitosamente."),
                Optional.of(toDto(check))));
    }

    @Transactional
    public ResponseEntity<?> reportLost(Long id, ReportLostCheckRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        Check check = getCheckOrThrow(id);

        if (check.getTypeCheck() != CheckType.FISICO) {
            throw new IllegalArgumentException("Solo se pueden reportar cheques fisicos como extraviados");
        }
        if (check.getStatusCheck() == CheckStatus.COBRADO) {
            throw new IllegalArgumentException("El cheque ya fue cobrado, no puede reportarse como extraviado");
        }
        if (check.getStatusCheck() != CheckStatus.EMITIDO) {
            throw new IllegalArgumentException("Cheque no disponible para reporte de extravio");
        }
        if (request.getIncidentDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de extravio no puede ser futura");
        }

        check.setStatusCheck(CheckStatus.EXTRAVIADO);
        check.setIncidentType(request.getIncidentType());
        check.setIncidentDate(request.getIncidentDate());
        check.setIncidentDetail(request.getIncidentDetail().trim());
        check.setIncidentActions(request.getIncidentActions().trim());
        check.setBlockPayment(true);

        checkRepository.save(check);
        auditPublisher.publishUpdate(AuditModule.BNK, "Check", check.getId(),
                "Cheque #" + check.getNumberCheck() + " reportado como extraviado ("
                        + check.getIncidentType() + ")");

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cheque reportado como extraviado exitosamente."),
                Optional.of(toDto(check))));
    }

    @Transactional
    public ResponseEntity<?> reconcile(Long id, ReconcileCheckRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        Check check = checkRepository.findWithCheckbookAndBankById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cheque no disponible"));

        if (Boolean.TRUE.equals(check.getBlockPayment()) || check.getStatusCheck() == CheckStatus.EXTRAVIADO) {
            throw new IllegalArgumentException("BNK-ERR-EXV-001: Cheque reportado como no cobrable");
        }
        if (check.getStatusCheck() != CheckStatus.EMITIDO) {
            throw new IllegalArgumentException("El cheque no esta disponible para cobro");
        }
        if (request.getCollectionDate().isBefore(check.getIssueDate()) || request.getCollectionDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de cobro no es valida");
        }
        if (request.getConciliationMethod() == ConciliationMethod.AUTOMATICA && request.getFinancialMovementId() == null) {
            throw new IllegalArgumentException("Para conciliacion automatica debe informar idMovimientoFinanciero");
        }

        FinancialMovement movementToMatch = null;
        if (request.getConciliationMethod() == ConciliationMethod.AUTOMATICA) {
            Long bankAccountId = check.getCheckbook().getBankAccount().getId();
            movementToMatch = financialMovementService
                    .findForAutomaticCheckReconcile(request.getFinancialMovementId(), bankAccountId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Movimiento financiero no encontrado o no corresponde a la cuenta bancaria del cheque."));
            if (movementToMatch.getMatchedCheckId() != null) {
                throw new IllegalArgumentException("El movimiento ya fue conciliado con otro cheque.");
            }
            if (movementToMatch.getAmount().compareTo(check.getValue().negate()) != 0) {
                throw new IllegalArgumentException(
                        "El importe del movimiento debe ser el negativo del valor del cheque (egreso en extracto).");
            }
        } else if (request.getFinancialMovementId() != null) {
            throw new IllegalArgumentException("Solo la conciliacion automatica admite movimiento financiero asociado.");
        }

        check.setCollectionDate(request.getCollectionDate());
        check.setConciliationMethod(request.getConciliationMethod());
        check.setCollectionReference(request.getCollectionReference().trim());
        check.setStatusCheck(CheckStatus.COBRADO);
        check.setFinancialMovementId(request.getFinancialMovementId());

        checkRepository.save(check);
        auditPublisher.publishUpdate(AuditModule.BNK, "Check", check.getId(),
                "Cheque #" + check.getNumberCheck() + " cobrado/conciliado por $" + check.getValue());

        if (movementToMatch != null) {
            financialMovementService.markMatchedToCheck(movementToMatch, check.getId());
        }

        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cheque conciliado exitosamente."),
                Optional.of(toDto(check))));
    }

    @Transactional
    public ResponseEntity<?> delete(Long id) {
        Check check = getCheckOrThrow(id);
        checkRepository.delete(check);
        auditPublisher.publishDelete(AuditModule.BNK, "Check", check.getId(),
                "Cheque #" + check.getNumberCheck() + " eliminado");
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Cheque eliminado exitosamente."),
                Optional.empty()));
    }

    private void validateIssueRules(EmitCheckRequest request) {
        if (request.getValue() == null || request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("BNK-ERR-094: El valor del cheque debe ser mayor a cero");
        }
        if (request.getIssueDate() == null || request.getIssueDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("BNK-ERR-095: La fecha de expedicion no puede ser futura");
        }
        if (request.getBeneficiary() == null || request.getBeneficiary().trim().isEmpty()) {
            throw new IllegalArgumentException("El beneficiario no puede estar vacio");
        }
        if (request.getNumberCheck() == null) {
            throw new IllegalArgumentException("El numero de cheque es obligatorio");
        }
    }

    private void validateCurrentPassword(String rawPassword) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw new IllegalArgumentException("No tiene permisos para anular cheques");
        }

        User currentUser = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("No tiene permisos para anular cheques"));

        if (!passwordEncoder.matches(rawPassword, currentUser.getPassword())) {
            throw new IllegalArgumentException("Contrasena incorrecta");
        }
    }

    private Check getCheckOrThrow(Long id) {
        return checkRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("Cheque no disponible"));
    }

    private Checkbook resolveCheckbook(Long checkbookId) {
        return checkbookRepository.findById(checkbookId)
                .orElseThrow(() -> new IllegalArgumentException("BNK-ERR-073: Chequera no encontrada"));
    }

    private byte[] decodeBase64Payload(String rawBase64) {
        String payload = rawBase64.trim();
        int commaIndex = payload.indexOf(',');
        if (payload.startsWith("data:") && commaIndex >= 0) {
            payload = payload.substring(commaIndex + 1);
        }
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Base64 de documento soporte invalido");
        }
    }

    private String resolveSupportedMime(String rawBase64, byte[] decoded) {
        String mimeFromHeader = null;
        String trimmed = rawBase64.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("data:") && trimmed.contains(";base64,")) {
            int start = "data:".length();
            int end = trimmed.indexOf(";base64,");
            mimeFromHeader = trimmed.substring(start, end).trim();
        }

        String bySignature = detectMimeBySignature(decoded);
        String mime = mimeFromHeader != null ? mimeFromHeader : bySignature;

        if (mime == null) {
            throw new IllegalArgumentException("Tipo de archivo no soportado para documento soporte");
        }

        if (!mime.equals("application/pdf")
                && !mime.equals("image/png")
                && !mime.equals("image/jpeg")
                && !mime.equals("image/jpg")) {
            throw new IllegalArgumentException("Tipo de archivo no soportado para documento soporte");
        }

        return mime.equals("image/jpg") ? "image/jpeg" : mime;
    }

    private String detectMimeBySignature(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        if (bytes[0] == 0x25 && bytes[1] == 0x50 && bytes[2] == 0x44 && bytes[3] == 0x46) {
            return "application/pdf";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private String extensionByMime(String mime) {
        return switch (mime) {
            case "application/pdf" -> "pdf";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> "bin";
        };
    }

    private String buildVirtualDocumentPath(Integer numberCheck, String extension) {
        LocalDateTime now = LocalDateTime.now();
        return String.format(
                "/documentos/cheques/virtuales/%d/%02d/CHEQUE_%s_%d.%s",
                now.getYear(),
                now.getMonthValue(),
                numberCheck,
                System.currentTimeMillis(),
                extension);
    }

    private DataTableRequest normalizeDataTableRequest(DataTableRequest request) {
        DataTableRequest safe = request != null ? request : new DataTableRequest();

        if (safe.getLength() == 0) {
            safe.setLength(20);
        }
        if (safe.getColumns() == null) {
            safe.setColumns(new ArrayList<>());
        }
        if (safe.getSearch() == null) {
            safe.setSearch(new DataTableRequest.DataTableSearch("", false));
        }
        return safe;
    }

    private CheckDTO toDto(Check check) {
        return CheckDTO.builder()
                .id(check.getId())
                .checkbook(toCheckbookDto(check.getCheckbook()))
                .numberCheck(check.getNumberCheck())
                .beneficiary(check.getBeneficiary())
                .value(check.getValue())
                .concept(check.getConcept())
                .issueDate(check.getIssueDate())
                .collectionDate(check.getCollectionDate())
                .typeCheck(check.getTypeCheck())
                .statusCheck(check.getStatusCheck())
                .financialMovementId(check.getFinancialMovementId())
                .observations(check.getObservations())
                .supportDocumentPath(check.getSupportDocumentPath())
                .supportDocumentMime(check.getSupportDocumentMime())
                .voidReason(check.getVoidReason())
                .voidedAt(check.getVoidedAt())
                .incidentType(check.getIncidentType())
                .incidentDate(check.getIncidentDate())
                .incidentDetail(check.getIncidentDetail())
                .incidentActions(check.getIncidentActions())
                .blockPayment(check.getBlockPayment())
                .conciliationMethod(check.getConciliationMethod())
                .collectionReference(check.getCollectionReference())
                .createdAt(check.getCreatedAt())
                .updatedAt(check.getUpdatedAt())
                .build();
    }

    private CheckbookDTO toCheckbookDto(Checkbook checkbook) {
        if (checkbook == null) {
            return null;
        }
        return CheckbookDTO.builder()
                .id(checkbook.getId())
                .bankAccountId(
                    checkbook.getBankAccount() != null 
                        ? checkbook.getBankAccount().getId() 
                        : null
                )
                .checkbookNumber(checkbook.getCheckbookNumber())
                .issuingBank(checkbook.getIssuingBank())
                .checkStartNumber(checkbook.getCheckStartNumber())
                .checkEndNumber(checkbook.getCheckEndNumber())
                .totalChecks(checkbook.getTotalChecks())
                .usedChecks(checkbook.getUsedChecks())
                .availableChecks(checkbook.getAvailableChecks())
                .receivedDate(checkbook.getReceivedDate())
                .activationDate(checkbook.getActivationDate())
                .status(checkbook.getStatus())
                .observations(checkbook.getObservations())
                .createdAt(checkbook.getCreatedAt())
                .updatedAt(checkbook.getUpdatedAt())
                .deletedAt(checkbook.getDeletedAt())
                .build();
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
