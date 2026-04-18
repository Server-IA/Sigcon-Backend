package com.sigcon.backend.parametrization.account_mappings.domain.service;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.parametrization.account_mappings.application.AccountMappingDTO;
import com.sigcon.backend.parametrization.account_mappings.domain.model.AccountMapping;
import com.sigcon.backend.parametrization.account_mappings.domain.repository.AccountMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio central que resuelve el ID de la cuenta contable (accounting_accounts.id)
 * a partir de un codigo de concepto logico (ej. {@code AR_CLIENTES} -> cuenta PUC 1305).
 *
 * <p>Este servicio es el reemplazo de los {@code fallbackAccountId} / IDs hardcodeados
 * que existian en los servicios que generan asientos contables (AR, AP, BNK, ACT).
 *
 * <p><b>Caching:</b> los mapeos cambian muy rara vez (solo por actualizacion manual en BD
 * o por cambio normativo de DIAN), por lo que se mantienen en memoria en un cache local.
 * Si fuera necesario refrescar, reiniciar la aplicacion.
 *
 * <p><b>Fail-fast:</b> al arrancar la aplicacion, {@link #validateMandatoryConcepts()}
 * verifica que todos los conceptos criticos esten configurados. Si falta alguno, la
 * aplicacion NO arranca y se lanza {@link IllegalStateException}.
 *
 * <p><b>Patron de uso:</b>
 * <pre>
 *   Long idCxc = accountMappingService.resolveOrThrow(AccountingConcept.AR_CLIENTES);
 *   // usar en CreateJournalEntryLineRequest.accountingAccountId(idCxc)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountMappingService {

    private final AccountMappingRepository mappingRepository;
    private final AccountingAccountRepository accountingAccountRepository;

    /**
     * Conceptos obligatorios en el sistema. Si alguno falta al iniciar, la app no arranca.
     * Mantener en sync con el seed de {@code V31__account_mappings.sql}.
     */
    private static final List<String> REQUIRED_CONCEPTS = List.of(
            AccountingConcept.AR_CLIENTES,
            AccountingConcept.AR_ANTICIPOS,
            AccountingConcept.AR_RET_PRACTICADAS_CLIENTE,
            AccountingConcept.AR_INGRESOS,
            AccountingConcept.AR_IVA_GENERADO,
            AccountingConcept.AP_PROVEEDORES,
            AccountingConcept.AP_ANTICIPOS,
            AccountingConcept.AP_RET_PRACTICADAS,
            AccountingConcept.AP_IVA_DESCONTABLE,
            AccountingConcept.BANCOS_DEFAULT,
            AccountingConcept.CAJA_DEFAULT,
            AccountingConcept.DIF_CAMBIO_INGRESO,
            AccountingConcept.DIF_CAMBIO_GASTO,
            AccountingConcept.AP_COMPRAS_DEFAULT,
            // HU-NOM-02/03: conceptos requeridos para generar el JE de liquidacion
            // de nomina. Seed en migracion V9-G.
            AccountingConcept.NOMINA_SALARIOS,
            AccountingConcept.NOMINA_CXP_EMPLEADOS,
            AccountingConcept.NOMINA_RETENCIONES,
            AccountingConcept.NOMINA_CESANTIAS
    );

    /** Cache thread-safe concept_code -> accounting_account_id. */
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    /**
     * Hook que se ejecuta cuando la aplicacion ya completo el arranque de Spring,
     * incluyendo {@code CommandLineRunner}s como {@code DataInitializer} que ejecuta
     * las migraciones SQL. Valida que TODOS los conceptos obligatorios tengan mapeo
     * activo; si alguno falta, lanza {@link IllegalStateException} y el contexto se
     * cierra (fail-fast tras arranque).
     *
     * <p>Se usa {@link ApplicationReadyEvent} en lugar de {@code @PostConstruct} porque
     * los scripts SQL se ejecutan desde un {@code CommandLineRunner} ({@code DataInitializer}),
     * que corre DESPUES de las fases de inicializacion de beans. Un {@code @PostConstruct}
     * dispararia antes y fallaria siempre en el primer arranque.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void validateMandatoryConcepts() {
        List<String> missing = new ArrayList<>();
        for (String concept : REQUIRED_CONCEPTS) {
            if (!mappingRepository.existsByConceptCode(concept)) {
                missing.add(concept);
            }
        }
        if (!missing.isEmpty()) {
            String msg = "Conceptos contables obligatorios NO configurados en account_mappings: "
                    + String.join(", ", missing)
                    + ". Verifique que la migracion V31 se haya ejecutado correctamente.";
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.info("AccountMappingService: {} conceptos contables obligatorios verificados OK",
                REQUIRED_CONCEPTS.size());
    }

    /**
     * Resuelve el ID de cuenta contable para un concepto. Si no existe, lanza excepcion.
     *
     * @param conceptCode codigo logico del concepto (usar constantes de {@link AccountingConcept})
     * @return ID de la cuenta contable (accounting_accounts.id)
     * @throws IllegalStateException si el concepto no esta mapeado
     */
    public Long resolveOrThrow(String conceptCode) {
        Long cached = cache.get(conceptCode);
        if (cached != null) {
            return cached;
        }
        AccountMapping mapping = mappingRepository.findByConceptCode(conceptCode)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe mapeo contable para concepto: " + conceptCode
                        + ". Configure en tabla account_mappings."));
        cache.put(conceptCode, mapping.getAccountingAccountId());
        return mapping.getAccountingAccountId();
    }

    /**
     * Resuelve el ID de cuenta contable, devolviendo Optional vacio si no existe.
     * Util para casos donde la ausencia del mapeo es aceptable (no obligatorio).
     *
     * @param conceptCode codigo logico del concepto
     * @return Optional con el ID, o vacio si no hay mapeo
     */
    public Optional<Long> resolve(String conceptCode) {
        Long cached = cache.get(conceptCode);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<AccountMapping> mapping = mappingRepository.findByConceptCode(conceptCode);
        mapping.ifPresent(m -> cache.put(conceptCode, m.getAccountingAccountId()));
        return mapping.map(AccountMapping::getAccountingAccountId);
    }

    /**
     * Lista todos los mapeos del sistema con su informacion extendida (para vista admin).
     *
     * @return lista de DTOs con concept_code, puc_code, accounting_account_id y nombre
     */
    public List<AccountMappingDTO> listAll() {
        List<AccountMapping> mappings = mappingRepository.findAllByOrderByConceptCodeAsc();
        List<AccountMappingDTO> result = new ArrayList<>(mappings.size());
        for (AccountMapping m : mappings) {
            String accountName = accountingAccountRepository.findById(m.getAccountingAccountId())
                    .map(AccountingAccount::getCustomName)
                    .orElse(null);
            result.add(AccountMappingDTO.builder()
                    .id(m.getId())
                    .conceptCode(m.getConceptCode())
                    .conceptDescription(m.getConceptDescription())
                    .pucCode(m.getPucCode())
                    .accountingAccountId(m.getAccountingAccountId())
                    .accountingAccountName(accountName)
                    .build());
        }
        return result;
    }

    /**
     * Invalida el cache. Util para tests o tras una actualizacion manual en BD.
     */
    public void clearCache() {
        cache.clear();
    }
}
