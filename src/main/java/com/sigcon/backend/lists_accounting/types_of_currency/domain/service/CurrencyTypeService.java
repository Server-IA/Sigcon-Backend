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
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El código ISO ingresado ya está registrado en el sistema")));
            }

            // Validar duplicado de nombre
            if (currencyTypeRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.getName())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El nombre de la moneda ya existe en el sistema")));
            }

            CurrencyType currencyType = CurrencyType.builder()
                    .isoCode(request.getIsoCode())
                    .name(request.getName())
                    .status(request.getStatus())
                    .build();

            currencyTypeRepository.save(currencyType);
            auditPublisher.publishCreate(AuditModule.CFG, "CurrencyType", currencyType.getId(), "CurrencyType creado id=" + currencyType.getId());

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tipo de moneda creada exitosamente"), Optional.empty()));

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
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tipo de moneda actualizada exitosamente"), Optional.empty())
            );
    
        }catch (Exception e) {
            return ResponseEntity.internalServerError().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }   

    }

    @Transactional
    public CurrencyTypeDeleteResponseDTO deleteCurrencyType(Long id) {
        CurrencyType existing = currencyTypeRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Moneda no encontrada"));

        String errorMessage = isCurrencyUsed(id);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }

        // Soft delete en vez de eliminación física para mantener trazabilidad
        existing.setDeletedAt(java.time.LocalDateTime.now());
        currencyTypeRepository.save(existing);
        auditPublisher.publishDelete(AuditModule.CFG, "CurrencyType", existing.getId(), "CurrencyType eliminado id=" + existing.getId());

        return CurrencyTypeDeleteResponseDTO.builder()
                .id(existing.getId())
                .isoCode(existing.getIsoCode())
                .name(existing.getName())
                .deletedAt(existing.getDeletedAt())
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

    private String isCurrencyUsed(Long currencyId) {
        Optional<ExchangeRate> exchangeRate = exchangeRateRepository.findByCurrencyExchangeOrCurrencyExchanged(currencyId);
        if (exchangeRate.isPresent()) {
            return "No se puede eliminar: moneda asociada a tasas de cambio.";
        }
        
        Optional<AccountingAccount> accountingAccount = accountingAccountRepository.findByCurrencyType_Id(currencyId);
        if (accountingAccount.isPresent()) {
            return "No se puede eliminar: moneda asociada a cuentas contables.";
        }
        return "";
    }
}
