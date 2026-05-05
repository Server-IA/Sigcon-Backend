package com.sigcon.backend.lists_accounting.types_of_currency.domain.service;

import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeRequestDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeUpdateRequestDTO;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.repository.ExchangeRateRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeDeleteResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import org.springframework.data.domain.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyTypeService {

    private final CurrencyTypeRepository currencyTypeRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<CurrencyType> specificationBuilder = new DataTableSpecificationBuilder<>();

    @Transactional
    public ResponseEntity<?> createCurrencyType(CurrencyTypeRequestDTO request, BindingResult bindingResult) {
        try {
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
            }

            // Validar duplicado de código ISO
            if (currencyTypeRepository.existsByIsoCodeAndDeletedAtIsNull(request.getIsoCode())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Codigo ISO de moneda ya existe")));
            }

            // Validar duplicado de nombre
            if (currencyTypeRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.getName())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Nombre de moneda ya registrado")));
            }

            CurrencyType currencyType = CurrencyType.builder()
                    .isoCode(request.getIsoCode())
                    .name(request.getName())
                    .status(request.getStatus())
                    .build();

            currencyTypeRepository.save(currencyType);
            auditPublisher.publishCreate(AuditModule.CFG, "CurrencyType", currencyType.getId(), "CurrencyType creado id=" + currencyType.getId());

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Moneda creada exitosamente"), Optional.empty()));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
        //     return ResponseEntity.internalServerError().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        // }
    }

    @Transactional
    public ResponseEntity<?> updateCurrencyType(Long id, CurrencyTypeUpdateRequestDTO request, BindingResult bindingResult) {

        try{

            CurrencyType existing = currencyTypeRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("La moneda no existe"));
    
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

            if (request.getStatus() != null) {
                if (!request.getStatus().equals(existing.getStatus())) {
                    existing.setStatus(request.getStatus());
                    isModified = true;
                }
            }
    
            if (!isModified) {
                throw new IllegalArgumentException("No se enviaron datos nuevos para actualizar");
            }
    
            currencyTypeRepository.save(existing);
            auditPublisher.publishUpdate(AuditModule.CFG, "CurrencyType", existing.getId(), "CurrencyType actualizado id=" + existing.getId());

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Moneda actualizada exitosamente"), Optional.empty())
            );
    
        }catch (Exception e) {
            return ResponseEntity.internalServerError().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }   

    }

    @Transactional
    public CurrencyTypeDeleteResponseDTO deleteCurrencyType(Long id, String reason) {
        // HU-CFG-RF-24 E2/E3: motivo obligatorio + persistido en audit log
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException(
                    "Debe especificar el motivo de eliminacion (minimo 10 caracteres).");
        }
        CurrencyType existing = currencyTypeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("La moneda no existe"));

        String errorMessage = isCurrencyUsed(id);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }

        // Soft delete en vez de eliminación física para mantener trazabilidad
        existing.setDeletedAt(java.time.LocalDateTime.now());
        currencyTypeRepository.save(existing);
        // HU-CFG-RF-24 E3: motivo en descripcion del audit log
        auditPublisher.publishDelete(AuditModule.CFG, "CurrencyType", existing.getId(),
                "CurrencyType eliminado id=" + existing.getId() + " | motivo=" + reason.trim());

        return CurrencyTypeDeleteResponseDTO.builder()
                .id(existing.getId())
                .isoCode(existing.getIsoCode())
                .name(existing.getName())
                .deletedAt(existing.getDeletedAt())
                .message("Moneda eliminada exitosamente")
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
                    .status(c.getStatus())
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

    /**
     * HU-CFG-RF-21 E5: exporta el listado de monedas en CSV o XLSX (multi-tenant).
     */
    public byte[] exportAll(String format) {
        java.util.List<CurrencyType> all = currencyTypeRepository.findAll().stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(java.util.stream.Collectors.toList());
        java.util.List<String> headers = java.util.List.of(
                "Id", "Codigo ISO", "Nombre", "Estado", "Creado");
        java.util.List<java.util.function.Function<CurrencyType, Object>> cols = java.util.List.of(
                CurrencyType::getId,
                CurrencyType::getIsoCode,
                CurrencyType::getName,
                c -> c.getStatus() != null ? c.getStatus().name() : "",
                CurrencyType::getCreatedAt);
        if ("xlsx".equalsIgnoreCase(format)) {
            return com.sigcon.backend.utils.export.SimpleTableExporter
                    .toXlsx("Monedas", headers, cols, all);
        }
        return com.sigcon.backend.utils.export.SimpleTableExporter
                .toCsv(headers, cols, all);
    }

    private String isCurrencyUsed(Long currencyId) {
        // HU-CFG-RF-24 E4: mensaje literal de la HU
        Optional<ExchangeRate> exchangeRate = exchangeRateRepository.findByCurrencyExchangeOrCurrencyExchanged(currencyId);
        if (exchangeRate.isPresent()) {
            return "No se puede eliminar el Registro, porque esta vinculada a registros activos en otros modulos. Retire las dependencias e intente de nuevo.";
        }

        Optional<AccountingAccount> accountingAccount = accountingAccountRepository.findByCurrencyType_Id(currencyId);
        if (accountingAccount.isPresent()) {
            return "No se puede eliminar el Registro, porque esta vinculada a registros activos en otros modulos. Retire las dependencias e intente de nuevo.";
        }
        return "";
    }
}
