package com.sigcon.backend.accounts_receivable.dian.resolutions.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.accounts_receivable.dian.resolutions.application.DianResolutionDTO;
import com.sigcon.backend.accounts_receivable.dian.resolutions.application.DianResolutionRequest;
import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model.DianResolution;
import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model.DianResolutionStatus;
import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.repository.DianResolutionRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de resoluciones DIAN (AR-17).
 * Administra el catalogo de resoluciones de numeracion autorizadas por la DIAN
 * y garantiza asignacion atomica del siguiente consecutivo de factura electronica.
 * Referencia normativa: Resolucion 0042 de 2020 y Anexo Tecnico.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DianResolutionService {

    private final DianResolutionRepository repository;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<DianResolution> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Umbral (%) por debajo del cual se dispara alerta de rango disponible.
     * HU-AR-17 E2: alerta cuando se ha consumido el 80% del rango (i.e., cuando
     * queda menos del 20% disponible).
     */
    private static final double RANGE_ALERT_THRESHOLD_PERCENT = 20.0;
    /** Dias minimos de vigencia por debajo de los cuales se dispara alerta (HU-AR-17 E3). */
    private static final long DAYS_ALERT_THRESHOLD = 30L;

    /**
     * Lista paginada de resoluciones DIAN para DataTable.
     */
    public ResponseEntity<?> search(DataTableRequest request) {
        if (request == null) request = new DataTableRequest();
        int length = request.getLength() > 0 ? request.getLength() : 10;
        int start = request.getStart();
        Pageable pageable = PageRequest.of(start / length, length);
        Specification<DianResolution> spec = specBuilder.build(request);
        Page<DianResolutionDTO> data = repository.findAll(spec, pageable).map(this::toDTO);
        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Obtiene una resolucion por ID.
     */
    public ResponseEntity<?> getById(Long id) {
        DianResolution r = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("La resolucion DIAN no fue encontrada"));
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Resolucion encontrada"), Optional.of(toDTO(r))));
    }

    /**
     * Crea una nueva resolucion DIAN validando unicidad y rango.
     */
    @Transactional
    public ResponseEntity<?> store(DianResolutionRequest request) {
        validateRequest(request);
        if (repository.existsByResolutionNumberAndDeletedAtIsNull(request.getResolutionNumber())) {
            throw new IllegalArgumentException("El numero de resolucion ya esta registrado");
        }
        DianResolution entity = DianResolution.builder()
                .resolutionNumber(request.getResolutionNumber())
                .prefix(request.getPrefix())
                .startNumber(request.getStartNumber())
                .endNumber(request.getEndNumber())
                .currentNumber(request.getStartNumber() - 1)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .technicalKey(request.getTechnicalKey())
                .status(request.getStatus() != null ? request.getStatus() : DianResolutionStatus.ACTIVE)
                .notes(request.getNotes())
                .build();
        entity = repository.save(entity);
        auditPublisher.publishCreate(AuditModule.AR, "DianResolution", entity.getId(),
                "Resolucion DIAN creada: " + entity.getResolutionNumber()
                        + " (rango " + entity.getStartNumber() + "-" + entity.getEndNumber() + ")");
        log.info("Resolucion DIAN {} creada (rango {}-{})",
                entity.getResolutionNumber(), entity.getStartNumber(), entity.getEndNumber());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Resolucion creada correctamente"), Optional.of(toDTO(entity))));
    }

    /**
     * Actualiza una resolucion existente. No permite reducir el numero inicial por
     * debajo del consecutivo ya asignado.
     */
    @Transactional
    public ResponseEntity<?> update(Long id, DianResolutionRequest request) {
        DianResolution entity = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("La resolucion DIAN no fue encontrada"));
        validateRequest(request);
        if (repository.existsByResolutionNumberAndIdNotAndDeletedAtIsNull(
                request.getResolutionNumber(), id)) {
            throw new IllegalArgumentException("El numero de resolucion ya esta registrado");
        }
        if (request.getEndNumber() < entity.getCurrentNumber()) {
            throw new IllegalArgumentException(
                    "El numero final no puede ser menor al consecutivo ya asignado");
        }
        entity.setResolutionNumber(request.getResolutionNumber());
        entity.setPrefix(request.getPrefix());
        entity.setStartNumber(request.getStartNumber());
        entity.setEndNumber(request.getEndNumber());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setTechnicalKey(request.getTechnicalKey());
        if (request.getStatus() != null) entity.setStatus(request.getStatus());
        entity.setNotes(request.getNotes());
        entity = repository.save(entity);
        auditPublisher.publishUpdate(AuditModule.AR, "DianResolution", entity.getId(),
                "Resolucion DIAN actualizada: " + entity.getResolutionNumber());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Resolucion actualizada correctamente"), Optional.of(toDTO(entity))));
    }

    /**
     * Elimina logicamente una resolucion DIAN.
     */
    @Transactional
    public ResponseEntity<?> delete(Long id) {
        DianResolution entity = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("La resolucion DIAN no fue encontrada"));
        repository.deleteById(entity.getId());
        auditPublisher.publishDelete(AuditModule.AR, "DianResolution", entity.getId(),
                "Resolucion DIAN eliminada: " + entity.getResolutionNumber());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Resolucion eliminada correctamente"), Optional.empty()));
    }

    /**
     * AR-17: asigna el siguiente numero autorizado de la resolucion de forma atomica.
     * Lanza excepcion si la resolucion esta vencida, agotada o inactiva.
     *
     * @param resolutionId id de la resolucion
     * @return numero consecutivo asignado (dentro del rango autorizado)
     */
    @Transactional
    public Long assignNextNumber(Long resolutionId) {
        DianResolution r = repository.findByIdAndDeletedAtIsNull(resolutionId)
                .orElseThrow(() -> new IllegalArgumentException("La resolucion DIAN no fue encontrada"));
        if (r.getStatus() != DianResolutionStatus.ACTIVE) {
            throw new IllegalStateException("La resolucion DIAN no esta activa");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(r.getStartDate()) || today.isAfter(r.getEndDate())) {
            r.setStatus(DianResolutionStatus.EXPIRED);
            repository.save(r);
            auditPublisher.publishUpdate(AuditModule.AR, "DianResolution", r.getId(),
                    "Resolucion DIAN " + r.getResolutionNumber() + " marcada EXPIRED por vencimiento");
            throw new IllegalStateException("La resolucion DIAN esta vencida");
        }
        long next = r.getCurrentNumber() != null ? r.getCurrentNumber() + 1 : r.getStartNumber();
        if (next < r.getStartNumber()) next = r.getStartNumber();
        if (next > r.getEndNumber()) {
            r.setStatus(DianResolutionStatus.EXHAUSTED);
            repository.save(r);
            auditPublisher.publishUpdate(AuditModule.AR, "DianResolution", r.getId(),
                    "Resolucion DIAN " + r.getResolutionNumber() + " marcada EXHAUSTED (rango agotado)");
            throw new IllegalStateException("La resolucion DIAN agoto su rango autorizado");
        }
        r.setCurrentNumber(next);
        boolean exhausted = false;
        if (next >= r.getEndNumber()) {
            r.setStatus(DianResolutionStatus.EXHAUSTED);
            exhausted = true;
        }
        repository.save(r);
        auditPublisher.publishUpdate(AuditModule.AR, "DianResolution", r.getId(),
                "Resolucion DIAN " + r.getResolutionNumber() + " consumio consecutivo " + next
                        + (exhausted ? " (rango AGOTADO)" : ""));
        return next;
    }

    /**
     * AR-17: retorna resoluciones activas con alerta de vencimiento (&lt;30 dias)
     * o de rango disponible (&lt;5%).
     */
    public ResponseEntity<?> checkAlerts() {
        List<DianResolution> active = repository
                .findByStatusAndDeletedAtIsNull(DianResolutionStatus.ACTIVE);
        List<DianResolutionDTO> alerts = new ArrayList<>();
        for (DianResolution r : active) {
            DianResolutionDTO dto = toDTO(r);
            if (Boolean.TRUE.equals(dto.getRangeAlert())
                    || Boolean.TRUE.equals(dto.getExpirationAlert())) {
                alerts.add(dto);
            }
        }
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Alertas de resoluciones DIAN"), Optional.of(alerts)));
    }

    /**
     * Busca una resolucion activa por ID sin cambiar estado.
     */
    public DianResolution findActiveOrThrow(Long id) {
        DianResolution r = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("La resolucion DIAN no fue encontrada"));
        if (r.getStatus() != DianResolutionStatus.ACTIVE) {
            throw new IllegalStateException("La resolucion DIAN no esta activa");
        }
        return r;
    }

    private void validateRequest(DianResolutionRequest r) {
        if (r.getEndNumber() <= r.getStartNumber()) {
            throw new IllegalArgumentException(
                    "El numero final debe ser mayor al numero inicial");
        }
        if (r.getEndDate().isBefore(r.getStartDate())) {
            throw new IllegalArgumentException(
                    "La fecha final debe ser posterior a la fecha inicial");
        }
    }

    private DianResolutionDTO toDTO(DianResolution r) {
        long total = (r.getEndNumber() != null && r.getStartNumber() != null)
                ? (r.getEndNumber() - r.getStartNumber() + 1) : 0L;
        long consumed = (r.getCurrentNumber() != null && r.getStartNumber() != null
                && r.getCurrentNumber() >= r.getStartNumber())
                ? (r.getCurrentNumber() - r.getStartNumber() + 1) : 0L;
        double usage = total > 0 ? (consumed * 100.0) / total : 0.0;
        long remaining = total - consumed;
        double remainingPct = total > 0 ? (remaining * 100.0) / total : 0.0;
        long daysToExpire = r.getEndDate() != null
                ? ChronoUnit.DAYS.between(LocalDate.now(), r.getEndDate()) : 0L;
        boolean rangeAlert = remainingPct < RANGE_ALERT_THRESHOLD_PERCENT && total > 0;
        boolean expirationAlert = daysToExpire >= 0 && daysToExpire < DAYS_ALERT_THRESHOLD;

        return DianResolutionDTO.builder()
                .id(r.getId())
                .resolutionNumber(r.getResolutionNumber())
                .prefix(r.getPrefix())
                .startNumber(r.getStartNumber())
                .endNumber(r.getEndNumber())
                .currentNumber(r.getCurrentNumber())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .technicalKey(r.getTechnicalKey())
                .status(r.getStatus())
                .notes(r.getNotes())
                .usagePercent(usage)
                .daysToExpire(daysToExpire)
                .rangeAlert(rangeAlert)
                .expirationAlert(expirationAlert)
                .build();
    }

    /**
     * HU-AR-17 E4 + HU-AR-14 E2: Reserva el siguiente numero consecutivo de la
     * resolucion DIAN aplicable. Antes de incrementar valida:
     * <ul>
     *   <li>Resolucion ACTIVE</li>
     *   <li>Vigencia (issueDate <= hoy <= expirationDate)</li>
     *   <li>Rango disponible (currentNumber < endNumber)</li>
     * </ul>
     * Si alguna validacion falla, lanza {@link IllegalStateException} con mensaje
     * para bloquear la emision de XML/factura.
     *
     * @param resolutionId id de la resolucion a consumir
     * @return el numero consecutivo asignado
     * @throws IllegalStateException si la resolucion esta vencida o agotada
     */
    @org.springframework.transaction.annotation.Transactional
    public Long consumeNumber(Long resolutionId) {
        DianResolution r = repository.findById(resolutionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resolucion DIAN no encontrada: " + resolutionId));

        // Validar estado
        if (r.getStatus() != null && !"ACTIVE".equalsIgnoreCase(r.getStatus().name())) {
            throw new IllegalStateException(
                    "Resolucion DIAN " + r.getResolutionNumber()
                    + " no esta activa. No se puede emitir factura.");
        }
        // HU-AR-17 E4 / HU-AR-14 E2: validar vigencia
        java.time.LocalDate today = java.time.LocalDate.now();
        if (r.getEndDate() != null && today.isAfter(r.getEndDate())) {
            throw new IllegalStateException(
                    "Resolucion DIAN " + r.getResolutionNumber() + " esta vencida ("
                    + r.getEndDate() + "). Solicite una nueva resolucion.");
        }
        if (r.getStartDate() != null && today.isBefore(r.getStartDate())) {
            throw new IllegalStateException(
                    "Resolucion DIAN " + r.getResolutionNumber()
                    + " aun no esta vigente (inicia " + r.getStartDate() + ").");
        }
        // HU-AR-17 E4 / HU-AR-14 E2: validar rango no agotado
        Long current = r.getCurrentNumber() != null ? r.getCurrentNumber() : (r.getStartNumber() - 1);
        if (current >= r.getEndNumber()) {
            throw new IllegalStateException(
                    "Resolucion DIAN " + r.getResolutionNumber() + " agoto su rango ("
                    + r.getStartNumber() + " - " + r.getEndNumber() + "). Solicite ampliacion.");
        }

        Long next = current + 1;
        r.setCurrentNumber(next);
        repository.save(r);
        log.info("HU-AR-17: consumido numero {} de resolucion {} (rango {}-{})",
                next, r.getResolutionNumber(), r.getStartNumber(), r.getEndNumber());
        return next;
    }
}
