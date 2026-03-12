package com.sigcon.backend.lists_accounting.cost_centers.domain.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.cost_centers.domain.repository.CostCenterRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CostCenterService {

        private final CostCenterRepository costCenterRepository;
        private final DataTableSpecificationBuilder<CostCenter> costCenterSpecificationBuilder = new DataTableSpecificationBuilder<>();

        public ResponseEntity<?> getCostCentersPaged(DataTableRequest request) {
                try {
                        int start = Math.max(0, request.getStart());
                        int length = request.getLength();
                        int safeLength = length <= 0 ? 10 : length;
                        int page = start / safeLength;

                        Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);

                        Specification<CostCenter> spec = costCenterSpecificationBuilder.build(request)
                                        .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

                        // Filter by companyId if applicable
                        Long companyId = getCurrentCompanyId();
                        if (companyId != null) {
                                spec = spec.and((root, query, cb) -> cb.equal(root.get("companyId"), companyId));
                        }

                        Page<CostCenter> costCenters = costCenterRepository.findAll(spec, pageable);

                        return ResponseEntity.ok(
                                        DataTableResponse.from(costCenters.map(this::convertToDTO), request.getDraw()));
                } catch (Exception e) {
                        return ResponseEntity.badRequest().body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                }
        }

        public ResponseEntity<?> storeCostCenter(CostCenterDTO request, BindingResult bindingResult) {
                // try {
                        if (bindingResult.hasErrors()) {
                                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                        }
                        
                        Long companyId = request.getCompanyId();

                        // if (costCenterRepository.existsByCodeAndCompanyIdAndDeletedAtIsNull(request.getCode(),
                        //                 companyId)) {
                        //         return ResponseEntity.badRequest().body(
                        //                         ErrorRespondJson.getErrorRespondMessage(
                        //                                         Optional.of("El código del centro de costo ya existe para esta empresa")));
                        // }

                        // if (costCenterRepository.existsByNameAndCompanyIdAndDeletedAtIsNull(request.getName(),
                        //                 companyId)) {
                        //         return ResponseEntity.badRequest().body(
                        //                         ErrorRespondJson.getErrorRespondMessage(
                        //                                         Optional.of("El nombre del centro de costo ya existe para esta empresa")));
                        // }

                        CostCenter costCenter = CostCenter.builder()
                                .code(request.getCode())
                                .name(request.getName())
                                .description(request.getDescription())
                                .status(request.getStatus())
                                .companyId(companyId)
                                .build();

                        costCenterRepository.save(costCenter);
                        return ResponseEntity.ok(
                                        SuccessRespondJson.getSuccessRespondMessage(
                                                        Optional.of("Centro de costo creado exitosamente"),
                                                        Optional.empty()));
                // } catch (Exception e) {
                //         return ResponseEntity.badRequest().body(
                //                         ErrorRespondJson.getErrorRespondMessage(
                //                                         Optional.of("Error al guardar el centro de costo")));
                // }
        }

        public ResponseEntity<?> updateCostCenter(Long id, CostCenter request, BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                // try {
                        CostCenter costCenter = costCenterRepository.findByIdAndDeletedAtIsNull(id)
                                        .orElseThrow(() -> new RuntimeException("Centro de costo no encontrado"));

                        Long companyId = request.getCompanyId();

                        // if (costCenterRepository.existsByCodeAndCompanyIdAndIdNotAndDeletedAtIsNull(request.getCode(),
                        //                 companyId, id)) {
                        //         return ResponseEntity.badRequest().body(
                        //                         ErrorRespondJson.getErrorRespondMessage(
                        //                                         Optional.of("El código del centro de costo ya existe para esta empresa")));
                        // }

                        // if (costCenterRepository.existsByNameAndCompanyIdAndIdNotAndDeletedAtIsNull(request.getName(),
                        //                 companyId, id)) {
                        //         return ResponseEntity.badRequest().body(
                        //                         ErrorRespondJson.getErrorRespondMessage(
                        //                                         Optional.of("El nombre del centro de costo ya existe para esta empresa")));
                        // }

                        costCenter.setCode(request.getCode());
                        costCenter.setName(request.getName());
                        costCenter.setDescription(request.getDescription());
                        costCenter.setStatus(request.getStatus());
                        costCenter.setCompanyId(companyId);

                        costCenterRepository.save(costCenter);
                        return ResponseEntity.ok(
                                        SuccessRespondJson.getSuccessRespondMessage(
                                                        Optional.of("Centro de costo actualizado exitosamente"),
                                                        Optional.empty()));
                // } catch (Exception e) {
                //         return ResponseEntity.badRequest().body(
                //                         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                // }
        }

        public ResponseEntity<?> deleteCostCenter(Long id, String reason) {
                // try {
                        CostCenter costCenter = costCenterRepository.findByIdAndDeletedAtIsNull(id)
                                        .orElseThrow(() -> new RuntimeException("Centro de costo no encontrado"));

                        // TODO: Check for active transactions before deletion if required by business
                        // logic

                        costCenter.setDeletedAt(LocalDateTime.now());
                        costCenter.setDeletionReason(reason);
                        costCenterRepository.save(costCenter);

                        return ResponseEntity.ok(
                                        SuccessRespondJson.getSuccessRespondMessage(
                                                        Optional.of("Centro de costo eliminado exitosamente"),
                                                        Optional.empty()));
                // } catch (Exception e) {
                //         return ResponseEntity.badRequest().body(
                //                         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                // }
        }

        private Long getCurrentCompanyId() {
                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (principal instanceof User user) {
                        // Uncomment when User entity has companyId:
                        // return user.getCompanyId();
                }
                return null;
        }

        private CostCenterDTO convertToDTO(CostCenter costCenter) {
                return CostCenterDTO.builder()
                                .id(costCenter.getId())
                                .code(costCenter.getCode())
                                .name(costCenter.getName())
                                .description(costCenter.getDescription())
                                .status(costCenter.getStatus())
                                .companyId(costCenter.getCompanyId())
                                .createdAt(costCenter.getCreatedAt())
                                .updatedAt(costCenter.getUpdatedAt())
                                .deletionReason(costCenter.getDeletionReason())
                                .build();
        }
}
