package com.sigcon.backend.lists_accounting.types_of_currency.domain.service;

import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeRequestDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeUpdateRequestDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeDeleteResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyTypeService {

    private final CurrencyTypeRepository currencyTypeRepository;
    private final DataTableSpecificationBuilder<CurrencyType> specificationBuilder = new DataTableSpecificationBuilder<>();

    @Transactional
    public CurrencyTypeResponseDTO createCurrencyType(CurrencyTypeRequestDTO request) {

        // Validar formato ISO 4217
        if (request.getIsoCode() == null || request.getName() == null
                || request.getIsoCode().isBlank() || request.getName().isBlank()
                || !request.getIsoCode().matches("[A-Z]{3}")) {
            throw new IllegalArgumentException(
                    "Debe ingresar un código ISO válido (ej. USD) y un nombre de moneda");
        }

        // Validar duplicado de código ISO
        if (currencyTypeRepository.existsByIsoCodeAndDeletedAtIsNull(request.getIsoCode())) {
            throw new IllegalArgumentException("El código ISO ingresado ya está registrado en el sistema");
        }

        // Validar duplicado de nombre
        if (currencyTypeRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.getName())) {
            throw new IllegalArgumentException("El nombre de la moneda ya existe en el sistema");
        }

        // Mapear DTO → Entity
        CurrencyType currencyType = CurrencyType.builder()
                .isoCode(request.getIsoCode())
                .name(request.getName())
                .build();

        CurrencyType saved = currencyTypeRepository.save(currencyType);

        // Mapear Entity → ResponseDTO
        return CurrencyTypeResponseDTO.builder()
                .id(saved.getId())
                .isoCode(saved.getIsoCode())
                .name(saved.getName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public CurrencyTypeResponseDTO updateCurrencyType(Long id, CurrencyTypeUpdateRequestDTO request) {

        CurrencyType existing = currencyTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La moneda seleccionada no existe en el sistema"));

        boolean isModified = false;

        // Validar e inyectar isoCode
        if (request.getIsoCode() != null && !request.getIsoCode().isBlank()) {
            if (!request.getIsoCode().matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Datos inválidos. Verifique el formato de entrada");
            }
            if (!existing.getIsoCode().equals(request.getIsoCode())) {
                if (currencyTypeRepository.existsByIsoCodeAndIdNotAndDeletedAtIsNull(request.getIsoCode(), id)) {
                    throw new IllegalArgumentException("El código/nombre ingresado ya se encuentra en uso");
                }
                existing.setIsoCode(request.getIsoCode());
                isModified = true;
            }
        }

        // Validar e inyectar name
        if (request.getName() != null && !request.getName().isBlank()) {
            if (!existing.getName().equals(request.getName())) {
                if (currencyTypeRepository.existsByNameIgnoreCaseAndIdNotAndDeletedAtIsNull(request.getName(), id)) {
                    throw new IllegalArgumentException("El código/nombre ingresado ya se encuentra en uso");
                }
                existing.setName(request.getName());
                isModified = true;
            }
        }

        if (!isModified) {
            throw new IllegalArgumentException("No se enviaron datos nuevos para actualizar");
        }

        CurrencyType updated = currencyTypeRepository.save(existing);

        return CurrencyTypeResponseDTO.builder()
                .id(updated.getId())
                .isoCode(updated.getIsoCode())
                .name(updated.getName())
                .createdAt(updated.getCreatedAt())
                .build();
    }

    @Transactional
    public CurrencyTypeDeleteResponseDTO deleteCurrencyType(Long id) {
        CurrencyType existing = currencyTypeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Moneda no encontrada"));

        if (currencyTypeRepository.isCurrencyUsed(id)) {
            throw new IllegalStateException(
                    "No se puede eliminar: moneda asociada a transacciones/configuraciones. Ver detalles.");
        }

        existing.setDeletedAt(java.time.LocalDateTime.now());
        existing.setActive(false);

        // Append a suffix to release the unique constraints for future records
        existing.setIsoCode(existing.getIsoCode() + "_DEL" + existing.getId());

        String deletedSuffix = " (DEL " + existing.getId() + ")";
        String newName = existing.getName();
        if (newName.length() + deletedSuffix.length() > 100) {
            newName = newName.substring(0, 100 - deletedSuffix.length());
        }
        existing.setName(newName + deletedSuffix);

        CurrencyType deleted = currencyTypeRepository.save(existing);

        return CurrencyTypeDeleteResponseDTO.builder()
                .id(deleted.getId())
                .isoCode(deleted.getIsoCode())
                .name(deleted.getName())
                .deletedAt(deleted.getDeletedAt())
                .message("El tipo de moneda ha sido eliminado exitosamente")
                .build();
    }

    public ResponseEntity<?> getCurrencyTypesDataTable(DataTableRequest request) {
        try {
            int start = Math.max(0, request.getStart());
            int length = request.getLength();
            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);

            Specification<CurrencyType> spec = specificationBuilder.build(request);
            Page<CurrencyType> currencyTypes = currencyTypeRepository.findAll(spec, pageable);

            Page<CurrencyTypeResponseDTO> dtoPage = currencyTypes.map(c -> CurrencyTypeResponseDTO.builder()
                    .id(c.getId())
                    .isoCode(c.getIsoCode())
                    .name(c.getName())
                    .createdAt(c.getCreatedAt())
                    .build());

            DataTableResponse<CurrencyTypeResponseDTO> response = DataTableResponse.from(dtoPage, request.getDraw());

            if (response.getData().isEmpty()) {
                long totalInDb = currencyTypeRepository.count();
                if (totalInDb == 0) {
                    response.setMessage("No hay monedas registradas");
                } else {
                    response.setMessage("No se encontraron resultados para la búsqueda realizada");
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error consultando tipos de moneda: ", e);
            return ResponseEntity.internalServerError().body(
                    java.util.Map.of(
                            "success", false,
                            "message", "Error al consultar monedas. Intente nuevamente o contacte al administrador"));
        }
    }
}
