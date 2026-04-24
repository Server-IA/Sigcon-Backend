package com.sigcon.backend.parametrization.resources.domain.service;

import com.sigcon.backend.parametrization.resources.application.AssignWithholdingRequest;
import com.sigcon.backend.parametrization.resources.application.SystemWithholdingAssignmentDTO;
import com.sigcon.backend.parametrization.resources.domain.model.SystemWithholdingAssignment;
import com.sigcon.backend.parametrization.resources.domain.model.Withholding;
import com.sigcon.backend.parametrization.resources.domain.repository.SystemWithholdingAssignmentRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.WithholdingRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Servicio para gestionar las asignaciones de retenciones del sistema (mono-empresa).
 * Reemplaza la logica anterior de CompanyWithholdingAssignment sin dependencia de Company.
 */
@Service
@RequiredArgsConstructor
public class SystemWithholdingAssignmentService {

    private final SystemWithholdingAssignmentRepository assignmentRepository;
    private final WithholdingRepository withholdingRepository;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<SystemWithholdingAssignment> specificationBuilder =
            new DataTableSpecificationBuilder<>();

    /**
     * Obtiene la lista paginada de asignaciones de retenciones del sistema.
     *
     * @param dtRequest parametros de paginacion, filtrado y ordenamiento
     * @return ResponseEntity con DataTableResponse de SystemWithholdingAssignmentDTO
     */
    public ResponseEntity<?> getAssignments(DataTableRequest dtRequest) {
        if (dtRequest == null) {
            dtRequest = new DataTableRequest();
            dtRequest.setDraw(1);
            dtRequest.setStart(0);
            dtRequest.setLength(10);
        }

        try {
            int page = dtRequest.getStart() / Math.max(dtRequest.getLength(), 1);
            Pageable pageable = PageRequest.of(page, Math.max(dtRequest.getLength(), 1));

            Specification<SystemWithholdingAssignment> spec = specificationBuilder.build(dtRequest);
            Page<SystemWithholdingAssignment> resultPage = assignmentRepository.findAll(spec, pageable);

            Page<SystemWithholdingAssignmentDTO> dtoPage = resultPage.map(this::toDTO);
            return ResponseEntity.ok(DataTableResponse.from(dtoPage, dtRequest.getDraw()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error al obtener asignaciones de retenciones: " + e.getMessage()))
            );
        }
    }

    /**
     * Asigna una retencion al sistema validando que exista y que no este ya asignada como ACTIVE.
     *
     * @param request datos de la asignacion
     * @return ResponseEntity con la asignacion creada o error de validacion
     */
    @Transactional
    public ResponseEntity<?> assignWithholding(AssignWithholdingRequest request) {
        // Validar que la retencion existe
        Optional<Withholding> withholdingOpt = withholdingRepository.findById(request.getWithholdingId());
        if (withholdingOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La retencion con ID " + request.getWithholdingId() + " no existe."))
            );
        }

        // Validar que no exista ya una asignacion activa para esta retencion
        boolean alreadyAssigned = assignmentRepository
                .existsByWithholdingIdAndStatusAndDeletedAtIsNull(request.getWithholdingId(), "ACTIVE");
        if (alreadyAssigned) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La retencion ya se encuentra asignada como ACTIVE."))
            );
        }

        // Validar fechas de vigencia
        if (request.getEffectiveTo() != null && request.getEffectiveTo().isBefore(request.getEffectiveFrom())) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La fecha fin de vigencia no puede ser anterior a la fecha de inicio."))
            );
        }

        // Crear la asignacion
        SystemWithholdingAssignment assignment = SystemWithholdingAssignment.builder()
                .withholding(withholdingOpt.get())
                .effectiveFrom(request.getEffectiveFrom())
                .effectiveTo(request.getEffectiveTo())
                .status("ACTIVE")
                .build();

        SystemWithholdingAssignment saved = assignmentRepository.save(assignment);
        auditPublisher.publishCreate(AuditModule.PA, "SystemWithholdingAssignment", saved.getId(),
                "Retencion asignada: " + saved.getWithholding().getName()
                        + " (" + saved.getWithholding().getCode() + "), vigencia desde " + saved.getEffectiveFrom());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Retencion asignada correctamente."),
                        Optional.of(toDTO(saved))
                )
        );
    }

    /**
     * Desasigna (soft delete) una asignacion de retencion del sistema.
     *
     * @param id ID de la asignacion a eliminar
     * @return ResponseEntity con mensaje de exito o error
     */
    @Transactional
    public ResponseEntity<?> unassignWithholding(Long id) {
        Optional<SystemWithholdingAssignment> assignmentOpt = assignmentRepository.findById(id);
        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La asignacion con ID " + id + " no existe."))
            );
        }

        assignmentRepository.deleteById(id);
        SystemWithholdingAssignment entity = assignmentOpt.get();
        auditPublisher.publishDelete(AuditModule.PA, "SystemWithholdingAssignment", id,
                "Asignacion de retencion eliminada: "
                        + (entity.getWithholding() != null ? entity.getWithholding().getName() : "id=" + id));

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Asignacion de retencion eliminada correctamente."),
                        Optional.empty()
                )
        );
    }

    /**
     * Convierte una entidad SystemWithholdingAssignment a su DTO correspondiente.
     *
     * @param entity entidad a convertir
     * @return DTO con los datos de la asignacion
     */
    private SystemWithholdingAssignmentDTO toDTO(SystemWithholdingAssignment entity) {
        return SystemWithholdingAssignmentDTO.builder()
                .id(entity.getId())
                .withholdingId(entity.getWithholding().getId())
                .withholdingName(entity.getWithholding().getName())
                .withholdingCode(entity.getWithholding().getCode())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .status(entity.getStatus())
                .build();
    }
}
