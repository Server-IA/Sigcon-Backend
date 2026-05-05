package com.sigcon.backend.general.accounting.closing.domain.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.sigcon.backend.general.accounting.closing.domain.model.enums.ClosingStatus;
import com.sigcon.backend.general.accounting.closing.domain.repository.ClosingEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HU-CFG-RF-15 E5: servicio puente para que otros modulos consulten si hay un
 * cierre contable EN CURSO (no terminado) en un periodo dado.
 *
 * <p><b>Estado actual:</b> ClosingStatus solo tiene COMPLETED y REVERSED — no
 * existe el concepto "IN_PROGRESS" por ahora. Este servicio retorna SIEMPRE
 * {@code false} hasta que el modulo CG implemente cierre con lock pessimistic
 * (ej. estado IN_PROGRESS, cierre por etapas, etc.).
 *
 * <p><b>TODO empalme con CG:</b> cuando se introduzca el flujo de cierre por
 * etapas (preview → ejecutar → confirmar), agregar IN_PROGRESS al enum
 * {@link ClosingStatus} y aqui reemplazar el {@code return false} por una
 * consulta real al repositorio.
 *
 * <p><b>Uso actual:</b>
 * <ul>
 *   <li>{@code DepretationRuleService.updateDepretationRule}: HU-CFG-RF-15 E5
 *       "No se puede modificar una regla vinculada a un cierre contable en curso".
 *   </li>
 * </ul>
 *
 * Pendiente a revisar cuando llegue el sprint de Contabilidad General.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClosingLockService {

    private final ClosingEntryRepository closingEntryRepository;

    /**
     * Indica si hay un cierre contable EN CURSO para la cuenta+fecha indicada.
     *
     * <p>Hoy retorna {@code false}: ClosingStatus solo tiene COMPLETED/REVERSED.
     * Cuando CG agregue IN_PROGRESS, esta lambda hace la consulta real.
     *
     * @param accountingAccountId cuenta contable a evaluar (puede ser null)
     * @param effectiveDate fecha de la regla / vigencia
     * @return true si hay cierre en curso que bloquearia la edicion
     */
    public boolean isClosingInProgressFor(Long accountingAccountId, LocalDate effectiveDate) {
        // TODO empalme CG: cuando exista ClosingStatus.IN_PROGRESS, reemplazar:
        //   return closingEntryRepository
        //       .existsByYearAndMonthAndStatus(year, month, ClosingStatus.IN_PROGRESS);
        log.debug("ClosingLockService.isClosingInProgressFor account={} date={} → false (CG sin IN_PROGRESS aun)",
                accountingAccountId, effectiveDate);
        return false;
    }
}
