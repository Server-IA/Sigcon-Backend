package com.sigcon.backend.lists_accounting.accounting_lists.domain.service;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.CreateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.DeleteChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.UpdateChartOfAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.repository.ChartOfAccountRepository;
import com.sigcon.backend.lists_accounting.accounting_lists.application.PucValidationReportDTO;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryLineRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servicio de gestion del Plan Unico de Cuentas (PUC) colombiano.
 * <p>
 * Implementa las operaciones CRUD sobre el catalogo PUC segun la normativa
 * colombiana (Decreto 2649/1993). Cada cuenta tiene un codigo con longitud fija
 * que determina su nivel jerarquico: 1 digito = Clase, 2 = Grupo, 4 = Cuenta,
 * 6 = Subcuenta. La naturaleza (DEBITO/CREDITO) se valida contra la clase contable.
 * </p>
 * <p>
 * Reglas clave:
 * <ul>
 *   <li>No se puede modificar el codigo ni inactivar cuentas con dependencias activas (cuentas contables vinculadas)</li>
 *   <li>Solo se pueden eliminar (soft delete) cuentas en estado INACTIVE sin dependencias</li>
 *   <li>Codigo y nombre deben ser unicos en todo el catalogo</li>
 * </ul>
 * </p>
 *
 * @see com.sigcon.backend.lists_accounting.accounting_lists.domain.model.ChartOfAccount
 */
@Service
@RequiredArgsConstructor
public class ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final AuditPublisher auditPublisher;


    private final DataTableSpecificationBuilder<ChartOfAccount> chartOfAccountSpecificationBuilder =
            new DataTableSpecificationBuilder<>();

    /**
     * Crea una nueva cuenta en el catalogo PUC.
     * <p>
     * Valida: campos obligatorios, clase contable, nivel jerarquico, coherencia
     * entre longitud del codigo y nivel declarado, naturaleza compatible con la clase,
     * y unicidad de codigo y nombre en todo el catalogo (incluyendo eliminados logicamente).
     * </p>
     *
     * @param request datos de la cuenta PUC a crear (codigo, nombre, clase, nivel, naturaleza)
     * @throws IllegalArgumentException si hay campos vacios, duplicados o incoherencia codigo/nivel
     * @throws IllegalStateException    si la naturaleza no es compatible con la clase contable
     */
    @Transactional
    public void createChartOfAccount(CreateChartOfAccountDTO request) {
        String code = normalizeCode(request.getCode());
        String name = normalizeName(request.getName());

        validateMandatoryCreateFields(request);
        validateAccountClass(request.getAccountClass());
        validateAccountLevel(request.getLevel());
        // Validar que la longitud del codigo coincida con el nivel jerarquico declarado
        validateCodeByLevel(code, request.getLevel());

        // Validar que la naturaleza (DEBITO/CREDITO) sea compatible con la clase contable
        AccountNature resolvedNature = validateAccountNature(request.getAccountClass(), request.getNature());

        // Unicidad global de codigo (incluye registros eliminados para evitar reutilizacion)
        if (chartOfAccountRepository.existsAnyByCode(code)) {
            throw new IllegalArgumentException("Codigo oficial ya registrado");
        }

        if (chartOfAccountRepository.existsAnyByName(name)) {
            throw new IllegalArgumentException("Nombre ya registrado");
        }

        ChartOfAccount account = ChartOfAccount.builder()
                .code(code)
                .name(name)
                .accountClass(request.getAccountClass())
                .accountLevel(request.getLevel())
                .accountNature(resolvedNature)
                .build();

        chartOfAccountRepository.save(account);
        auditPublisher.publishCreate(AuditModule.CFG, "ChartOfAccount", account.getId(), "ChartOfAccount creado id=" + account.getId());
    }

    /**
     * Busca cuentas PUC con filtros dinamicos y paginacion (DataTable).
     *
     * @param request parametros de paginacion, busqueda y ordenamiento
     * @return DataTableResponse con cuentas PUC que coincidan con los criterios
     *         (posiblemente vacio)
     */
    public DataTableResponse<ChartOfAccountResponseDTO> searchChartOfAccounts(DataTableRequest request) {
        DataTableRequest safeRequest = normalizeDataTableRequest(request == null ? new DataTableRequest() : request);

        int draw = Math.max(0, safeRequest.getDraw());
        int start = Math.max(0, safeRequest.getStart());
        int length = safeRequest.getLength();
        int safeLength = length <= 0 ? 10 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<ChartOfAccount> spec = chartOfAccountSpecificationBuilder.build(safeRequest);

        Page<ChartOfAccount> result = chartOfAccountRepository.findAll(spec, pageable);

        // Un listado paginado vacio NO es un error: el frontend renderiza
        // tabla vacia con DataTableResponse (totalElements=0).
        return DataTableResponse.from(result.map(this::toResponseDTO), draw);
    }

    /**
     * Actualiza una cuenta del catalogo PUC.
     * <p>
     * Restricciones clave cuando la cuenta tiene dependencias activas (cuentas contables vinculadas):
     * <ul>
     *   <li>No se puede cambiar el codigo (afectaria la estructura jerarquica)</li>
     *   <li>No se puede cambiar a estado INACTIVE (romperia las transacciones existentes)</li>
     * </ul>
     * Valida unicidad de codigo y nombre excluyendo el registro actual.
     * </p>
     *
     * @param request datos actualizados de la cuenta PUC
     * @param id      identificador de la cuenta a actualizar
     * @throws IllegalArgumentException si la cuenta no existe, hay duplicados o campos invalidos
     * @throws IllegalStateException    si se intenta modificar codigo/estado con dependencias activas
     */
    @Transactional
    public void updateChartOfAccount(UpdateChartOfAccountDTO request, Long id) {
        validateMandatoryUpdateFields(request);

        ChartOfAccount account = chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta PUC seleccionada no esta disponible para edicion"));

        String targetCode = normalizeCode(request.getCode());
        String targetName = normalizeName(request.getName());
        validateAccountClass(request.getAccountClass());
        validateAccountLevel(request.getLevel());
        validateCodeByLevel(targetCode, request.getLevel());

        AccountNature resolvedNature = validateAccountNature(request.getAccountClass(), request.getNature());

        // Verificar si la cuenta tiene cuentas contables vinculadas (dependencias)
        String dependency = hasActiveDependencies(account);
        boolean hasDeps = dependency != null;

        // No se puede cambiar el codigo si hay dependencias (romperia la jerarquia PUC)
        if (!targetCode.equals(account.getCode()) && hasDeps) {
            throw new IllegalStateException("No se puede modificar el campo, ya que la cuenta PUC esta asociada a transacciones registradas en el sistema.");
        }

        // No se puede inactivar una cuenta con dependencias activas
        if (request.getStatus() == AccountStatus.INACTIVE && hasDeps) {
            throw new IllegalStateException("No se puede cambiar el estado, la cuenta tiene transacciones registradas.");
        }

        // Validar unicidad excluyendo el registro actual
        if (chartOfAccountRepository.existsAnyByCodeAndIdNot(targetCode, id)) {
            throw new IllegalArgumentException("Codigo oficial ya registrado");
        }

        if (chartOfAccountRepository.existsAnyByNameAndIdNot(targetName, id)) {
            throw new IllegalArgumentException("Duplicidad del nombre de la cuenta");
        }

        account.setCode(targetCode);
        account.setName(targetName);
        account.setAccountClass(request.getAccountClass());
        account.setAccountLevel(request.getLevel());
        account.setAccountNature(resolvedNature);
        account.setStatus(request.getStatus());

        chartOfAccountRepository.save(account);
        auditPublisher.publishUpdate(AuditModule.CFG, "ChartOfAccount", account.getId(), "ChartOfAccount actualizado id=" + account.getId());
    }

    /**
     * Elimina (soft delete) una cuenta del catalogo PUC.
     * <p>
     * Requisitos para eliminar:
     * <ul>
     *   <li>La cuenta debe existir y no estar ya eliminada</li>
     *   <li>Debe estar en estado INACTIVE (no se pueden eliminar cuentas activas)</li>
     *   <li>No debe tener cuentas contables vinculadas (dependencias activas)</li>
     * </ul>
     * Se registra el motivo de eliminacion para trazabilidad.
     * </p>
     *
     * @param id      identificador de la cuenta a eliminar
     * @param request contiene el motivo de eliminacion
     * @throws IllegalArgumentException si la cuenta no existe
     * @throws IllegalStateException    si la cuenta esta activa o tiene dependencias
     */
    @Transactional
    public void deleteChartOfAccount(Long id, DeleteChartOfAccountDTO request) {
        ChartOfAccount account = chartOfAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta seleccionada del catalogo PUC no existe"));

        if (account.getDeletedAt() != null) {
            throw new IllegalStateException("La cuenta seleccionada del catalogo PUC no existe");
        }

        // Prerrequisito: la cuenta debe estar inactiva antes de poder eliminarse
        if (account.getStatus() != AccountStatus.INACTIVE) {
            throw new IllegalStateException("La cuenta esta activa, debe estar en estado inactiva para poder ser eliminada");
        }

        // Verificar que no tenga cuentas contables hijas vinculadas
        String dependency = hasActiveDependencies(account);

        if (dependency != null) {
            throw new IllegalStateException("No se puede inactivar la cuenta del catalogo PUC, porque esta vinculada a registros activos. Retire las dependencias e intente de nuevo");
        }

        account.setDeletedAt(LocalDateTime.now());

        account.setDeletedReason(request.getReason().trim());
        chartOfAccountRepository.save(account);
        auditPublisher.publishDelete(AuditModule.CFG, "ChartOfAccount", account.getId(), "ChartOfAccount eliminado id=" + account.getId());
    }

    /**
     * Valida que la clase contable no sea nula.
     * Las clases validas segun PUC colombiano son: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, COST, CONTINGENT.
     *
     * @param accountClass clase contable a validar
     * @return la misma clase si es valida
     * @throws IllegalArgumentException si es null
     */
    public AccountClass validateAccountClass(AccountClass accountClass) {
        if (accountClass == null) {
            throw new IllegalArgumentException("Clase de la cuenta no valida");
        }
        return accountClass;
    }

    /**
     * Valida que la naturaleza contable sea compatible con la clase.
     * Ejemplo: cuentas de Activo (clase 1) y Gasto (clase 5) tienen naturaleza DEBITO;
     * Pasivo (clase 2) y Ingreso (clase 4) tienen naturaleza CREDITO.
     *
     * @param accountClass   clase contable
     * @param accountNature  naturaleza declarada (DEBITO/CREDITO)
     * @return la naturaleza si es compatible
     * @throws IllegalArgumentException si alguno es null
     * @throws IllegalStateException    si la naturaleza no corresponde a la clase
     */
    public AccountNature validateAccountNature(AccountClass accountClass, AccountNature accountNature) {
        if (accountClass == null || accountNature == null) {
            throw new IllegalArgumentException("Naturaleza de la cuenta no valida");
        }

        if (!accountClass.matchesNature(accountNature)) {
            throw new IllegalStateException("La naturaleza de la cuenta es invalida");
        }

        return accountNature;
    }

    /**
     * Valida que el nivel jerarquico no sea nulo.
     *
     * @param accountLevel nivel jerarquico (CLASS, GROUP, ACCOUNT, SUBACCOUNT)
     * @throws IllegalArgumentException si es null
     */
    public void validateAccountLevel(AccountLevel accountLevel) {
        if (accountLevel == null) {
            throw new IllegalArgumentException("Jerarquia de la cuenta no valida, por favor seleccione una jerarquia valida");
        }
    }

    /**
     * Valida coherencia entre la longitud del codigo y el nivel jerarquico declarado.
     * Segun PUC colombiano: 1 digito = Clase, 2 = Grupo, 4 = Cuenta, 6 = Subcuenta.
     * Otras longitudes no son validas.
     */
    private void validateCodeByLevel(String code, AccountLevel level) {
        int codeLength = code.length();
        AccountLevel expectedLevelByCode = resolveLevelByLength(codeLength);

        if (expectedLevelByCode == null) {
            throw new IllegalArgumentException("Codigo invalido: la jerarquia PUC Colombiana solo permite 1, 2, 4 o 6 digitos");
        }

        if (expectedLevelByCode != level) {
            throw new IllegalArgumentException("Jerarquia de cuenta invalida: el codigo " + code
                    + " (" + codeLength + " digitos) corresponde al nivel " + levelToDisplay(expectedLevelByCode)
                    + " pero se recibio nivel " + levelToDisplay(level) + ".");
        }
    }

    /**
     * Resuelve el nivel jerarquico PUC a partir de la longitud del codigo.
     * Retorna null si la longitud no corresponde a ningun nivel valido.
     */
    private AccountLevel resolveLevelByLength(int codeLength) {
        return switch (codeLength) {
            case 1 -> AccountLevel.CLASS;
            case 2 -> AccountLevel.GROUP;
            case 4 -> AccountLevel.ACCOUNT;
            case 6 -> AccountLevel.SUBACCOUNT;
            default -> null;
        };
    }

    private String levelToDisplay(AccountLevel level) {
        if (level == null) {
            return "Invalido";
        }

        return switch (level) {
            case CLASS -> "Clase";
            case GROUP -> "Grupo";
            case ACCOUNT -> "Cuenta";
            case SUBACCOUNT -> "Subcuenta";
        };
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Por favor diligencie todos los campos obligatorios");
        }
        return code.trim();
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Por favor diligencie todos los campos obligatorios");
        }
        return name.trim();
    }

    private void validateMandatoryCreateFields(CreateChartOfAccountDTO request) {
        if (request.getAccountClass() == null || request.getLevel() == null || request.getNature() == null) {
            throw new IllegalArgumentException("Por favor diligencie todos los campos obligatorios");
        }
    }

    private void validateMandatoryUpdateFields(UpdateChartOfAccountDTO request) {
        if (!StringUtils.hasText(request.getCode())
                || !StringUtils.hasText(request.getName())
                || request.getAccountClass() == null
                || request.getLevel() == null
                || request.getNature() == null
                || request.getStatus() == null) {
            throw new IllegalArgumentException("No se puede actualizar la informacion. Por favor complete todos los campos obligatorios antes de continuar.");
        }
    }

    private ChartOfAccountResponseDTO toResponseDTO(ChartOfAccount account) {
        return ChartOfAccountResponseDTO.builder()
                .id(account.getId())
                .code(account.getCode())
                .name(account.getName())
                .accountClass(account.getAccountClass())
                .level(account.getAccountLevel())
                .nature(account.getAccountNature())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .deletedAt(account.getDeletedAt())
                .build();
    }

    private DataTableRequest normalizeDataTableRequest(DataTableRequest request) {
        if (request.getColumns() == null) {
            request.setColumns(new ArrayList<>());
            return request;
        }

        List<DataTableRequest.DataTableColumn> normalizedColumns = request.getColumns().stream()
                .map(column -> {
                    if (column == null || !StringUtils.hasText(column.getData())) {
                        return column;
                    }
                    column.setData(mapDataTableColumn(column.getData().trim()));
                    return column;
                })
                .toList();

        request.setColumns(normalizedColumns);
        return request;
    }

    private String mapDataTableColumn(String columnName) {
        return switch (columnName) {
            case "level" -> "accountLevel";
            case "nature" -> "accountNature";
            default -> columnName;
        };
    }

    /**
     * Verifica si la cuenta PUC tiene cuentas contables (AccountingAccount) vinculadas.
     * Se usa para proteger la integridad referencial antes de modificar o eliminar.
     *
     * @param account cuenta PUC a verificar
     * @return mensaje de error si hay dependencias, null si no las hay
     */
    private String hasActiveDependencies(ChartOfAccount account) {
        Long id = account.getId();
        List<AccountingAccount> dependencies = accountingAccountRepository.findByPucAccount_Id(id);
        if (!dependencies.isEmpty()) {
            return "No se puede inactivar la cuenta del catalogo PUC, porque esta vinculada a registros activos. Retire las dependencias e intente de nuevo";
        }
        return null;
    }

    // ───────────────────────────────────────────────────────────────
    // Reporte de validacion masiva del PUC (HU-CG-09D)
    // ───────────────────────────────────────────────────────────────

    /**
     * Ejecuta la validacion integral de consistencia del PUC (HU-CG-09D).
     *
     * Realiza cuatro controles sobre el catalogo unico de cuentas:
     * <ol>
     *   <li>ORPHAN: toda cuenta GROUP/ACCOUNT/SUBACCOUNT debe tener ancestros
     *       de los niveles previos (clase, grupo, cuenta) segun el prefijo del codigo.</li>
     *   <li>WRONG_NATURE: la naturaleza declarada en la cuenta (debito/credito)
     *       debe coincidir con la naturaleza esperada para la clase PUC
     *       (clases 1, 5, 6, 7 = DEBIT; clases 2, 3, 4 = CREDIT).</li>
     *   <li>DUPLICATE_CODE: no deben existir codigos PUC repetidos entre registros
     *       activos (deleted_at IS NULL).</li>
     *   <li>INACTIVE_WITH_MOVEMENTS: cuentas con estado INACTIVE que tengan
     *       movimientos en journal_entry_lines indican inconsistencia.</li>
     * </ol>
     *
     * @return reporte consolidado con totales, cuentas activas/inactivas y lista detallada
     *         de inconsistencias
     */
    public PucValidationReportDTO validatePuc() {
        List<ChartOfAccount> allAccounts = chartOfAccountRepository.findAll();

        int total = allAccounts.size();
        int active = 0;
        int inactive = 0;

        // Indexar cuentas por codigo para busqueda rapida de ancestros
        Map<String, ChartOfAccount> byCode = new HashMap<>();
        Map<String, List<ChartOfAccount>> duplicates = new HashMap<>();
        for (ChartOfAccount acc : allAccounts) {
            if (acc.getStatus() == AccountStatus.ACTIVE) active++;
            else if (acc.getStatus() == AccountStatus.INACTIVE) inactive++;

            String code = acc.getCode();
            duplicates.computeIfAbsent(code, k -> new ArrayList<>()).add(acc);
            byCode.putIfAbsent(code, acc);
        }

        List<PucValidationReportDTO.PucIssueDTO> issues = new ArrayList<>();

        // 1. ORPHAN: grupo/cuenta/subcuenta sin ancestros
        for (ChartOfAccount acc : allAccounts) {
            AccountLevel level = acc.getAccountLevel();
            String code = acc.getCode();
            if (level == null || code == null) continue;

            // Longitudes esperadas por convencion PUC colombiano: 1/2/4/6
            List<Integer> ancestorLengths = switch (level) {
                case GROUP -> List.of(1);             // padre: clase (1 digito)
                case ACCOUNT -> List.of(1, 2);        // padres: clase y grupo
                case SUBACCOUNT -> List.of(1, 2, 4);  // padres: clase, grupo, cuenta
                default -> List.of();
            };

            for (Integer len : ancestorLengths) {
                if (code.length() > len) {
                    String prefix = code.substring(0, len);
                    if (!byCode.containsKey(prefix)) {
                        issues.add(PucValidationReportDTO.PucIssueDTO.builder()
                                .accountId(acc.getId())
                                .pucCode(code)
                                .accountName(acc.getName())
                                .issueType("ORPHAN")
                                .description("Cuenta sin ancestro de codigo " + prefix
                                        + " en la jerarquia PUC")
                                .build());
                        break;
                    }
                }
            }
        }

        // 2. WRONG_NATURE: naturaleza incoherente con la clase PUC
        for (ChartOfAccount acc : allAccounts) {
            AccountClass clazz = acc.getAccountClass();
            AccountNature nature = acc.getAccountNature();
            if (clazz == null || nature == null) continue;
            if (!clazz.matchesNature(nature)) {
                issues.add(PucValidationReportDTO.PucIssueDTO.builder()
                        .accountId(acc.getId())
                        .pucCode(acc.getCode())
                        .accountName(acc.getName())
                        .issueType("WRONG_NATURE")
                        .description("Naturaleza " + nature
                                + " incoherente con clase " + clazz
                                + " (esperada: " + clazz.getExpectedNature() + ")")
                        .build());
            }
        }

        // 3. DUPLICATE_CODE: codigos repetidos entre activos
        Set<Long> reportedDuplicates = new HashSet<>();
        for (Map.Entry<String, List<ChartOfAccount>> e : duplicates.entrySet()) {
            List<ChartOfAccount> items = e.getValue();
            if (items.size() > 1) {
                for (ChartOfAccount acc : items) {
                    if (reportedDuplicates.add(acc.getId())) {
                        issues.add(PucValidationReportDTO.PucIssueDTO.builder()
                                .accountId(acc.getId())
                                .pucCode(acc.getCode())
                                .accountName(acc.getName())
                                .issueType("DUPLICATE_CODE")
                                .description("Codigo PUC " + acc.getCode()
                                        + " duplicado (" + items.size() + " ocurrencias activas)")
                                .build());
                    }
                }
            }
        }

        // 4. INACTIVE_WITH_MOVEMENTS: inactivas con movimientos en journal_entry_lines
        for (ChartOfAccount acc : allAccounts) {
            if (acc.getStatus() == AccountStatus.INACTIVE
                    && journalEntryLineRepository.existsMovementsByPucAccountId(acc.getId())) {
                issues.add(PucValidationReportDTO.PucIssueDTO.builder()
                        .accountId(acc.getId())
                        .pucCode(acc.getCode())
                        .accountName(acc.getName())
                        .issueType("INACTIVE_WITH_MOVEMENTS")
                        .description("Cuenta INACTIVE con movimientos contables registrados")
                        .build());
            }
        }

        return PucValidationReportDTO.builder()
                .totalAccounts(total)
                .activeAccounts(active)
                .inactiveAccounts(inactive)
                .errorCount(issues.size())
                .issues(issues)
                .build();
    }
}

