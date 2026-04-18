package com.sigcon.backend.lists_accounting.cost_centers.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.cost_centers.domain.model.CostCenter;
import com.sigcon.backend.lists_accounting.cost_centers.domain.repository.CostCenterRepository;
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
        private final AccountingAccountRepository accountingAccountRepository;

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

                        // company filter removed

                        Page<CostCenter> costCenters = costCenterRepository.findAll(spec, pageable);

                        return ResponseEntity.ok(
                                        DataTableResponse.from(costCenters.map(this::convertToDTO), request.getDraw()));
                } catch (Exception e) {
                        return ResponseEntity.badRequest().body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                }
        }

        /**
         * CFG-RF-17: Crea un nuevo centro de costo.
         * Valida unicidad de codigo y nombre antes de guardar.
         */
        public ResponseEntity<?> storeCostCenter(CostCenterDTO request, BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                // CFG-RF-17: validar unicidad de codigo
                if (costCenterRepository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
                        return ResponseEntity.badRequest().body(
                                ErrorRespondJson.getErrorRespondMessage(
                                        Optional.of("Ya existe un centro de costo con el codigo: " + request.getCode())));
                }

                // CFG-RF-17: validar unicidad de nombre
                if (costCenterRepository.existsByNameAndDeletedAtIsNull(request.getName())) {
                        return ResponseEntity.badRequest().body(
                                ErrorRespondJson.getErrorRespondMessage(
                                        Optional.of("Ya existe un centro de costo con el nombre: " + request.getName())));
                }

                CostCenter costCenter = CostCenter.builder()
                        .code(request.getCode())
                        .name(request.getName())
                        .description(request.getDescription())
                        .status(request.getStatus())
                        .build();

                costCenterRepository.save(costCenter);
                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Centro de costo creado exitosamente"),
                                                Optional.empty()));
        }

        /**
         * CFG-RF-19: Actualiza un centro de costo existente.
         * Valida unicidad de codigo y nombre excluyendo el registro actual.
         */
        public ResponseEntity<?> updateCostCenter(Long id, CostCenter request, BindingResult bindingResult) {
                if (bindingResult.hasErrors()) {
                        return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
                }

                CostCenter costCenter = costCenterRepository.findByIdAndDeletedAtIsNull(id)
                                .orElseThrow(() -> new RuntimeException("Centro de costo no encontrado"));

                // CFG-RF-19: validar unicidad de codigo (excluyendo registro actual)
                if (costCenterRepository.existsByCodeAndIdNotAndDeletedAtIsNull(request.getCode(), id)) {
                        return ResponseEntity.badRequest().body(
                                ErrorRespondJson.getErrorRespondMessage(
                                        Optional.of("Ya existe otro centro de costo con el codigo: " + request.getCode())));
                }

                // CFG-RF-19: validar unicidad de nombre (excluyendo registro actual)
                if (costCenterRepository.existsByNameAndIdNotAndDeletedAtIsNull(request.getName(), id)) {
                        return ResponseEntity.badRequest().body(
                                ErrorRespondJson.getErrorRespondMessage(
                                        Optional.of("Ya existe otro centro de costo con el nombre: " + request.getName())));
                }

                costCenter.setCode(request.getCode());
                costCenter.setName(request.getName());
                costCenter.setDescription(request.getDescription());
                costCenter.setStatus(request.getStatus());

                costCenterRepository.save(costCenter);
                return ResponseEntity.ok(
                                SuccessRespondJson.getSuccessRespondMessage(
                                                Optional.of("Centro de costo actualizado exitosamente"),
                                                Optional.empty()));
        }

        public ResponseEntity<?> deleteCostCenter(Long id, String reason) {
                try {
                        CostCenter costCenter = costCenterRepository.findByIdAndDeletedAtIsNull(id)
                                        .orElseThrow(() -> new RuntimeException("Centro de costo no encontrado"));

                        AccountingAccount accountingAccount = accountingAccountRepository.findByCostCenter_Id(id);
                        if (accountingAccount != null) {
                                return ResponseEntity.badRequest().body(
                                        ErrorRespondJson.getErrorRespondMessage(
                                                Optional.of("No se puede eliminar el centro de costo porque tiene cuentas contables asociadas. Puede inactivarlo en su lugar.")));
                        }

                        String dependency = hasActiveDependencies(id);
                        if (dependency != null) {
                                return ResponseEntity.badRequest().body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(dependency)));
                        }

                        costCenter.setDeletedAt(LocalDateTime.now());
                        costCenter.setDeletionReason(reason);
                        costCenterRepository.save(costCenter);

                        return ResponseEntity.ok(
                                        SuccessRespondJson.getSuccessRespondMessage(
                                                        Optional.of("Centro de costo eliminado exitosamente"),
                                                        Optional.empty()));
                } catch (Exception e) {
                        return ResponseEntity.badRequest().body(
                                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
                }
        }

        private CostCenterDTO convertToDTO(CostCenter costCenter) {
                return CostCenterDTO.builder()
                                .id(costCenter.getId())
                                .code(costCenter.getCode())
                                .name(costCenter.getName())
                                .description(costCenter.getDescription())
                                .status(costCenter.getStatus())
                                .createdAt(costCenter.getCreatedAt())
                                .updatedAt(costCenter.getUpdatedAt())
                                .deletionReason(costCenter.getDeletionReason())
                                .build();
        }

        private String hasActiveDependencies(Long costCenterId) {
                AccountingAccount accountingAccount = accountingAccountRepository.findByCostCenter_Id(costCenterId);
                if (accountingAccount != null) {
                        return "No se puede eliminar el centro de costo, porque está vinculada a cuentas contables activas. Retire las dependencias e intente de nuevo";
                }
                return null;  
        }
}
