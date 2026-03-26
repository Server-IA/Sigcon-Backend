package com.sigcon.backend.bnk_cash_flow.domain.service;

import com.sigcon.backend.bnk_cash_flow.application.CreateCashFlowProjectionDTO;
import com.sigcon.backend.bnk_cash_flow.application.UpdateCashFlowProjectionDTO;
import com.sigcon.backend.bnk_cash_flow.application.ViewCashFlowProjectionDTO;
import com.sigcon.backend.bnk_cash_flow.domain.model.CashFlowProjection;
import com.sigcon.backend.bnk_cash_flow.domain.model.enums.ProjectionStatus;
import com.sigcon.backend.bnk_cash_flow.domain.repository.CashFlowProjectionRepository;

import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * BNK-RF-29 / BNK-RF-30 / BNK-RF-31 / BNK-RF-32
 *
 * Servicio de negocio para la gestión de proyecciones de flujo de caja.
 *
 * Principios aplicados:
 * - Sin dependencia de empresa/companyId.
 * - Eliminación 100% lógica: nunca se llama repository.delete().
 * - Sin integración de auditoría avanzada.
 * - Reutiliza utils existentes del proyecto.
 *
 * // TODO: integrar con módulo de auditoría en el futuro
 */
@Service
@RequiredArgsConstructor
public class CashFlowProjectionService {

    private final CashFlowProjectionRepository projectionRepository;

    private final DataTableSpecificationBuilder<CashFlowProjection> specificationBuilder =
            new DataTableSpecificationBuilder<>();

    // ═══════════════════════════════════════════════════════════════════
    // BNK-RF-29 — Crear proyección
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Crea una nueva proyección de flujo de caja.
     *
     * Validaciones:
     * 1. BindingResult: campos obligatorios y formato.
     * 2. Nombre único (entre registros activos).
     * 3. endDate posterior a startDate.
     * 4. initialBalance >= 0.
     * 5. finalBalance = initialBalance + netFlow (calculado en backend).
     *
     * Estado inicial: BORRADOR.
     */
    public ResponseEntity<?> create(CreateCashFlowProjectionDTO request, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        // Validar nombre único
        if (projectionRepository.existsByNameAndDeletedAtIsNull(request.getName().trim())) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Ya existe una proyección con el nombre \"" + request.getName().trim() + "\".")));
        }

        // Validar rango de fechas
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La fecha de fin debe ser posterior a la fecha de inicio.")));
        }

        // Calcular saldo final en el backend (no se acepta del cliente)
        BigDecimal finalBalance = request.getInitialBalance().add(request.getNetFlow());

        CashFlowProjection projection = CashFlowProjection.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .periodicity(request.getPeriodicity())
                .projectionType(request.getProjectionType())
                .initialBalance(request.getInitialBalance())
                .netFlow(request.getNetFlow())
                .finalBalance(finalBalance)
                .currency(request.getCurrency().toUpperCase().trim())
                .build();
        // status = BORRADOR se asigna en @PrePersist

        projectionRepository.save(projection);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Proyección de flujo de caja registrada correctamente."),
                        Optional.of(toDto(projection))
                )
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // BNK-RF-30 — Modificar proyección
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Modifica una proyección existente.
     *
     * Restricciones por estado:
     * - EJECUTADA | INACTIVA → no se permite modificar.
     * - APROBADA → se requiere modificationReason.
     * - BORRADOR → edición libre.
     *
     * También recalcula finalBalance si cambian initialBalance o netFlow.
     */
    public ResponseEntity<?> update(Long id, UpdateCashFlowProjectionDTO request, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        CashFlowProjection projection = getProjectionOrThrow(id);

        // BNK-RF-30: estados que bloquean modificación
        if (projection.getStatus() == ProjectionStatus.EJECUTADA
                || projection.getStatus() == ProjectionStatus.INACTIVA) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No se puede modificar una proyección en estado "
                                    + projection.getStatus().name() + ".")));
        }

        // BNK-RF-30: proyección APROBADA requiere motivo
        if (projection.getStatus() == ProjectionStatus.APROBADA) {
            if (request.getModificationReason() == null
                    || request.getModificationReason().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("El motivo de modificación es obligatorio para proyecciones en estado APROBADA.")));
            }
        }

        // Validar nombre único (si cambió)
        if (request.getName() != null && !request.getName().isBlank()) {
            String newName = request.getName().trim();
            if (!newName.equalsIgnoreCase(projection.getName())
                    && projectionRepository.existsByNameAndIdNotAndDeletedAtIsNull(newName, id)) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Ya existe una proyección con el nombre \"" + newName + "\".")));
            }
            projection.setName(newName);
        }

        // Validar coherencia de fechas (con la combinación resultante)
        if (request.getStartDate() != null) projection.setStartDate(request.getStartDate());
        if (request.getEndDate() != null)   projection.setEndDate(request.getEndDate());

        if (!projection.getEndDate().isAfter(projection.getStartDate())) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La fecha de fin debe ser posterior a la fecha de inicio.")));
        }

        // Aplicar cambios opcionales
        if (request.getDescription() != null)    projection.setDescription(request.getDescription());
        if (request.getPeriodicity() != null)    projection.setPeriodicity(request.getPeriodicity());
        if (request.getProjectionType() != null) projection.setProjectionType(request.getProjectionType());
        if (request.getCurrency() != null)       projection.setCurrency(request.getCurrency().toUpperCase().trim());
        if (request.getStatus() != null)         projection.setStatus(request.getStatus());

        // Recalcular finalBalance si cambia alguno de los saldos
        if (request.getInitialBalance() != null) projection.setInitialBalance(request.getInitialBalance());
        if (request.getNetFlow() != null)        projection.setNetFlow(request.getNetFlow());
        projection.setFinalBalance(projection.getInitialBalance().add(projection.getNetFlow()));

        // Registrar motivo de modificación
        if (request.getModificationReason() != null && !request.getModificationReason().isBlank()) {
            projection.setModificationReason(request.getModificationReason().trim());
        }

        projectionRepository.save(projection);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Proyección actualizada correctamente."),
                        Optional.of(toDto(projection))
                )
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // BNK-RF-31 — Eliminación lógica / Inactivación
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Eliminación lógica de una proyección.
     *
     * Se establece:
     * - deletedAt = now()
     * - status = INACTIVA
     *
     * NO se llama repository.delete() en ningún caso.
     * La anotación @SQLDelete de la entidad también aplica este comportamiento
     * si se usara el método delete nativo de JPA, pero aquí se hace manualmente
     * para mayor control y claridad.
     *
     * Restricciones:
     * - EJECUTADA: No se puede eliminar (requiere inactivación previa).
     * - Ya INACTIVA: No se puede procesar de nuevo.
     */
    public ResponseEntity<?> delete(Long id) {

        CashFlowProjection projection = getProjectionOrThrow(id);

        if (projection.getStatus() == ProjectionStatus.INACTIVA) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La proyección ya se encuentra inactiva.")));
        }

        if (projection.getStatus() == ProjectionStatus.EJECUTADA) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No se puede eliminar una proyección en estado EJECUTADA. Primero debe inactivarla.")));
        }

        // Eliminación 100% lógica: NO se llama repository.delete()
        projection.setDeletedAt(LocalDateTime.now());
        projection.setStatus(ProjectionStatus.INACTIVA);
        projectionRepository.save(projection);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Proyección eliminada correctamente."),
                        Optional.empty()
                )
        );
    }

    /**
     * BNK-RF-31 — Inactivación (sin eliminación del registro).
     *
     * Permite inactivar una proyección que tiene dependencias o
     * que se quiere conservar en el historial sin eliminar.
     * A diferencia del delete(), el registro permanently visible en admin queries.
     */
    public ResponseEntity<?> inactivate(Long id) {

        CashFlowProjection projection = getProjectionOrThrow(id);

        if (projection.getStatus() == ProjectionStatus.INACTIVA) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("La proyección ya se encuentra inactiva.")));
        }

        projection.setStatus(ProjectionStatus.INACTIVA);
        projectionRepository.save(projection);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Proyección inactivada correctamente."),
                        Optional.of(toDto(projection))
                )
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // BNK-RF-32 — Consultas
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Consulta paginada de proyecciones usando DataTable (patrón del proyecto).
     *
     * Soporta filtros por: nombre, estado, tipo, moneda, fecha (via DataTableSpecificationBuilder).
     * Los registros con deletedAt IS NOT NULL son excluidos automáticamente por @Where.
     *
     * // TODO: Agregar filtros de exportación PDF/Excel en una fase futura
     */
    public ResponseEntity<?> findAllPaged(DataTableRequest request) {

        int start     = Math.max(0, request.getStart());
        int length    = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page      = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<CashFlowProjection> specification = specificationBuilder.build(request);
        Page<CashFlowProjection> projectionPage = projectionRepository.findAll(specification, pageable);

        if (projectionPage.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron proyecciones de flujo de caja.");
        }

        return ResponseEntity.ok(
                DataTableResponse.from(projectionPage.map(this::toDto), request.getDraw())
        );
    }

    /**
     * Detalle de una proyección por ID.
     * Excluye eliminados lógicamente gracias al @Where de la entidad.
     */
    public ResponseEntity<?> getDetail(Long id) {

        CashFlowProjection projection = getProjectionOrThrow(id);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Detalle de la proyección obtenido correctamente."),
                        Optional.of(toDto(projection))
                )
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // Métodos privados de soporte
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Busca una proyección por ID garantizando que no haya sido eliminada lógicamente.
     * Utiliza findByIdAndDeletedAtIsNull para mayor seguridad en endpoints GET/PUT/DELETE.
     */
    private CashFlowProjection getProjectionOrThrow(Long id) {
        return projectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("La proyección no existe o fue eliminada."));
    }

    /**
     * Mapea la entidad al DTO de vista.
     * finalBalance ya está persistido (calculado en create/update).
     */
    private ViewCashFlowProjectionDTO toDto(CashFlowProjection projection) {
        return ViewCashFlowProjectionDTO.builder()
                .id(projection.getId())
                .name(projection.getName())
                .description(projection.getDescription())
                .startDate(projection.getStartDate())
                .endDate(projection.getEndDate())
                .periodicity(projection.getPeriodicity())
                .projectionType(projection.getProjectionType())
                .initialBalance(projection.getInitialBalance())
                .netFlow(projection.getNetFlow())
                .finalBalance(projection.getFinalBalance())
                .currency(projection.getCurrency())
                .status(projection.getStatus())
                .modificationReason(projection.getModificationReason())
                .createdAt(projection.getCreatedAt())
                .updatedAt(projection.getUpdatedAt())
                .build();
    }
}
