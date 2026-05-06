package com.sigcon.backend.general.accounting;

import com.sigcon.backend.general.accounting.application.AccountingPeriodDTO;
import com.sigcon.backend.general.accounting.application.CreatePeriodRequest;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio centralizado de periodo contable.
 * Utilizado por Activos, NIIF, Depreciacion y otros modulos para
 * validar si un periodo permite operaciones, y por el controlador
 * para gestionar el ciclo de vida del periodo (crear, cerrar, bloquear, reabrir).
 */
@Service
@RequiredArgsConstructor
public class AccountingPeriodService {

    private final AccountingPeriodRepository repository;
    private final AuditPublisher auditPublisher;
    /**
     * HU-CG-29 E4: bloqueo de cierre de periodo si hay JE en DRAFT.
     * Incluye JE generados por cualquier modulo (AP, AR, BNK, ACT, NOM, CG).
     */
    private final com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository journalEntryRepository;

    // ───────────────────────────────────────────────────────────────
    // Validaciones consumidas por otros modulos
    // ───────────────────────────────────────────────────────────────

    /**
     * Verifica si el periodo contable para una fecha esta abierto.
     * @param date fecha a verificar
     * @return true si el periodo esta abierto
     * @throws IllegalStateException si el periodo esta cerrado o bloqueado
     */
    public boolean validatePeriodOpen(LocalDate date) {
        if (date == null) return true;

        AccountingPeriod period = repository.findByYearAndMonth(date.getYear(), date.getMonthValue())
                .orElse(null);

        // QA-2026-05-05: si NO existe registro pero existe un periodo CLOSED/LOCKED
        // posterior, considerar este periodo como cerrado por antiguedad. Regla
        // contable estandar: no se modifican periodos previos a un cierre fiscal.
        if (period == null) {
            if (hasClosedPeriodAfter(date)) {
                throw new IllegalStateException(
                        "No es posible realizar esta operacion. El periodo contable "
                        + date.getYear() + "-" + String.format("%02d", date.getMonthValue())
                        + " esta cerrado por antiguedad (existe un cierre posterior).");
            }
            return true;
        }

        if (period.isLocked()) {
            throw new IllegalStateException(
                    "No es posible realizar esta operacion. El periodo contable "
                    + date.getYear() + "-" + String.format("%02d", date.getMonthValue())
                    + " esta bloqueado permanentemente.");
        }

        if (period.isClosed()) {
            throw new IllegalStateException(
                    "No es posible realizar esta operacion. El periodo contable "
                    + date.getYear() + "-" + String.format("%02d", date.getMonthValue())
                    + " esta cerrado. Por favor seleccione una fecha dentro de un periodo activo.");
        }

        return true;
    }

    /**
     * Verifica si un periodo especifico (anio-mes) esta abierto.
     */
    public boolean isPeriodOpen(int year, int month) {
        AccountingPeriod period = repository.findByYearAndMonth(year, month).orElse(null);
        return period == null || period.isOpen();
    }

    /**
     * QA-2026-05-05: indica si existe algun periodo CLOSED o LOCKED cuya fecha
     * de inicio sea posterior a la fecha indicada. Util para inferir que un
     * periodo previo (sin registro propio) esta cerrado por antiguedad.
     */
    public boolean hasClosedPeriodAfter(LocalDate date) {
        if (date == null) return false;
        try {
            return repository.findAll().stream()
                    .filter(p -> !p.isOpen())
                    .anyMatch(p -> {
                        int yearCmp = Integer.compare(p.getYear(), date.getYear());
                        if (yearCmp != 0) return yearCmp > 0;
                        return p.getMonth() > date.getMonthValue();
                    });
        } catch (Exception e) {
            return false;
        }
    }

    // ───────────────────────────────────────────────────────────────
    // Consultas
    // ───────────────────────────────────────────────────────────────

    /** Retorna todos los periodos contables como DTOs. */
    public List<AccountingPeriodDTO> getAllPeriods() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    /** Retorna los periodos de un anio especifico. */
    public List<AccountingPeriodDTO> getPeriodsByYear(Integer year) {
        return repository.findByYear(year).stream().map(this::toDTO).toList();
    }

    // ───────────────────────────────────────────────────────────────
    // Ciclo de vida del periodo
    // ───────────────────────────────────────────────────────────────

    /**
     * Crea un nuevo periodo contable en estado OPEN.
     * @throws IllegalArgumentException si ya existe un periodo para ese anio/mes
     */
    @Transactional
    public AccountingPeriodDTO createPeriod(CreatePeriodRequest request) {
        repository.findByYearAndMonth(request.getYear(), request.getMonth())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Ya existe un periodo contable para "
                            + request.getYear() + "-" + String.format("%02d", request.getMonth()));
                });

        AccountingPeriod period = AccountingPeriod.builder()
                .year(request.getYear())
                .month(request.getMonth())
                .status(AccountingPeriodStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        AccountingPeriod saved = repository.save(period);
        auditPublisher.publishCreate(AuditModule.CG, "AccountingPeriod", saved.getId(),
                "Periodo contable creado: " + saved.getYear() + "-" + String.format("%02d", saved.getMonth()));
        return toDTO(saved);
    }

    /**
     * Cierra un periodo contable: OPEN -> CLOSED.
     * @throws IllegalStateException si el periodo no esta abierto
     */
    @Transactional
    public AccountingPeriodDTO closePeriod(Long id, String closedBy, String notes) {
        AccountingPeriod period = findByIdOrThrow(id);

        if (!period.isOpen()) {
            throw new IllegalStateException(
                    "Solo se puede cerrar un periodo que este en estado OPEN. Estado actual: " + period.getStatus());
        }

        // HU-CG-29 E4: bloquear cierre si hay asientos en BORRADOR en el periodo.
        // Esto aplica a JE de cualquier modulo (AP, AR, BNK, ACT, NOM, CG).
        long draftCount = journalEntryRepository
                .countByPeriodYearAndPeriodMonthAndStatusAndDeletedAtIsNull(
                        period.getYear(), period.getMonth(),
                        com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus.DRAFT);
        if (draftCount > 0) {
            throw new IllegalStateException(
                    "No se puede cerrar el periodo " + period.getYear() + "-"
                    + String.format("%02d", period.getMonth()) + ". Existen "
                    + draftCount + " comprobante(s) en estado BORRADOR. "
                    + "Contabilicelos o eliminelos antes de cerrar el periodo.");
        }

        period.setStatus(AccountingPeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);
        period.setNotes(notes);
        period.setUpdatedAt(LocalDateTime.now());

        AccountingPeriod saved = repository.save(period);
        auditPublisher.publishUpdate(AuditModule.CG, "AccountingPeriod", saved.getId(),
                "Periodo contable cerrado: " + saved.getYear() + "-" + String.format("%02d", saved.getMonth())
                        + " por " + closedBy);
        return toDTO(saved);
    }

    /**
     * Bloquea permanentemente un periodo: CLOSED -> LOCKED.
     * @throws IllegalStateException si el periodo no esta cerrado
     */
    @Transactional
    public AccountingPeriodDTO lockPeriod(Long id, String lockedBy) {
        AccountingPeriod period = findByIdOrThrow(id);

        if (!period.isClosed()) {
            throw new IllegalStateException(
                    "Solo se puede bloquear un periodo que este en estado CLOSED. Estado actual: " + period.getStatus());
        }

        period.setStatus(AccountingPeriodStatus.LOCKED);
        period.setLockedAt(LocalDateTime.now());
        period.setLockedBy(lockedBy);
        period.setUpdatedAt(LocalDateTime.now());

        AccountingPeriod saved = repository.save(period);
        auditPublisher.publishUpdate(AuditModule.CG, "AccountingPeriod", saved.getId(),
                "Periodo contable bloqueado permanentemente: "
                        + saved.getYear() + "-" + String.format("%02d", saved.getMonth()) + " por " + lockedBy);
        return toDTO(saved);
    }

    /**
     * Reabre un periodo cerrado: CLOSED -> OPEN. No aplica para periodos bloqueados.
     * @throws IllegalStateException si el periodo no esta cerrado o esta bloqueado
     */
    @Transactional
    public AccountingPeriodDTO reopenPeriod(Long id) {
        AccountingPeriod period = findByIdOrThrow(id);

        if (period.isLocked()) {
            throw new IllegalStateException(
                    "No es posible reabrir un periodo bloqueado permanentemente.");
        }
        if (!period.isClosed()) {
            throw new IllegalStateException(
                    "Solo se puede reabrir un periodo que este en estado CLOSED. Estado actual: " + period.getStatus());
        }

        period.setStatus(AccountingPeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);
        period.setNotes(null);
        period.setUpdatedAt(LocalDateTime.now());

        AccountingPeriod saved = repository.save(period);
        auditPublisher.publishUpdate(AuditModule.CG, "AccountingPeriod", saved.getId(),
                "Periodo contable reabierto: "
                        + saved.getYear() + "-" + String.format("%02d", saved.getMonth()));
        return toDTO(saved);
    }

    // ───────────────────────────────────────────────────────────────
    // Helpers privados
    // ───────────────────────────────────────────────────────────────

    private AccountingPeriod findByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Periodo contable no encontrado con id: " + id));
    }

    /** Convierte la entidad a DTO de lectura. */
    private AccountingPeriodDTO toDTO(AccountingPeriod entity) {
        return AccountingPeriodDTO.builder()
                .id(entity.getId())
                .year(entity.getYear())
                .month(entity.getMonth())
                .status(entity.getStatus().name())
                .closedAt(entity.getClosedAt())
                .closedBy(entity.getClosedBy())
                .lockedAt(entity.getLockedAt())
                .lockedBy(entity.getLockedBy())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
