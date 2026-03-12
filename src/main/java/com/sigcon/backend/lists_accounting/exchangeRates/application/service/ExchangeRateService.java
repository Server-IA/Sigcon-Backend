package com.sigcon.backend.lists_accounting.exchangeRates.application.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.CreateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.ExchangeRateDTO;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.ExchangeRateFilterRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.UpdateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.StatusCurrencyExchange;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.repository.ExchangeRateRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository repository;
    private final CurrencyTypeRepository currencyRepository;
    private final DataTableSpecificationBuilder<ExchangeRate> exchangeRateSpecificationBuilder = new DataTableSpecificationBuilder<>();

    // CFG-RF-31 Crear tasa
    public ResponseEntity<?> create(CreateExchangeRateRequest request, BindingResult bindingResult) {
        // try{
            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
            }

            if (request.getStartDate().isAfter(request.getEndDate())) {
                return ResponseEntity.badRequest().body("La fecha inicio no puede ser mayor a la fecha fin");
            }

            // boolean overlap = repository.existsOverlap(
            //         request.getCompanyId(),
            //         request.getCurrencyId(),
            //         request.getCurrencyIso(),
            //         request.getExchangeType(),
            //         request.getStartDate(),
            //         request.getEndDate()
            // );

            // if (overlap) {
            //     return ResponseEntity.badRequest().body(
            //         ErrorRespondJson.getErrorRespondMessage(Optional.of("Ya existe una tasa en ese rango de fechas"))
            //     );
            // }

            CurrencyType currencyExchange = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyId())
                    .orElseThrow(() -> new RuntimeException("La moneda cambiada no existe"));

            CurrencyType currencyExchanged = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyIso())
                    .orElseThrow(() -> new RuntimeException("La moneda a cambiar no existe"));

            ExchangeRate rate = ExchangeRate.builder()
                    .companyId(request.getCompanyId())
                    .currencyExchange(currencyExchange)
                    .currencyExchanged(currencyExchanged)
                    .exchangeType(request.getExchangeType())
                    .value(request.getValue())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            repository.save(rate);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tasa de cambio creado con exito"), Optional.empty())
            );
        // }catch(Exception e){
        //     return ResponseEntity.badRequest().body(
        //         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
        //     );
        // }
    }

    // CFG-RF-32 Consultar tasas
    public ResponseEntity<?> findAll(DataTableRequest request) {

        int start = Math.max(0, request.getStart());
        int length = request.getLength();

        int safeLength = length <= 0 ? 10 : length;
        int page = start / safeLength;

        Pageable pageable = length == -1
            ? Pageable.unpaged()
            : PageRequest.of(page, safeLength);

        Specification<ExchangeRate> spec = exchangeRateSpecificationBuilder.build(request)
            .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

        Page<ExchangeRate> exchangeRates = repository.findAll(spec, pageable);

        return ResponseEntity.ok(
            DataTableResponse.from(
                exchangeRates.map(exchangeRate -> ExchangeRateDTO.builder()
                    .id(exchangeRate.getId())
                    .currencyExchange(
                        CurrencyTypeResponseDTO.builder()
                            .id(exchangeRate.getCurrencyExchange().getId())
                            .isoCode(exchangeRate.getCurrencyExchange().getIsoCode())
                            .name(exchangeRate.getCurrencyExchange().getName())
                            .createdAt(exchangeRate.getCurrencyExchange().getCreatedAt())
                            .build()
                    ).currencyExchanged(
                        CurrencyTypeResponseDTO.builder()
                            .id(exchangeRate.getCurrencyExchanged().getId())
                            .isoCode(exchangeRate.getCurrencyExchanged().getIsoCode())
                            .name(exchangeRate.getCurrencyExchanged().getName())
                            .createdAt(exchangeRate.getCurrencyExchanged().getCreatedAt())
                            .build()
                    )
                    .exchangeType(exchangeRate.getExchangeType())
                    .value(exchangeRate.getValue())
                    .startDate(exchangeRate.getStartDate())
                    .endDate(exchangeRate.getEndDate())
                    .status(exchangeRate.getStatus())
                    .build())
                , request.getDraw())
        );
    }

    // CFG-RF-33 Editar tasa
    public ResponseEntity<?> update(Long id, UpdateExchangeRateRequest request, BindingResult bindingResult) {

        // try{

            if (bindingResult.hasErrors()) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondJson(bindingResult)
                );
            }
    
            ExchangeRate rate = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("La tasa no existe"));
    
            if (request.getValue() <= 0) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("La tasa debe ser mayor a 0"))
                );
            }
    
            if (request.getStartDate().isAfter(request.getEndDate())) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Fechas inválidas"))
                );
            }
    
            // boolean overlap = repository.existsOverlapForUpdate(
            //         request.getCurrencyId(),
            //         request.getCurrencyIso(),
            //         request.getExchangeType(),
            //         request.getStartDate(),
            //         request.getEndDate(),
            //         id
            // );
    
            // if (overlap) {
            //     return ResponseEntity.badRequest().body(
            //         ErrorRespondJson.getErrorRespondMessage(Optional.of("Existe conflicto con otra tasa"))
            //     );
            // }
    
            CurrencyType currencyExchange = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyId())
                    .orElseThrow(() -> new RuntimeException("La moneda cambiada no existe"));
            CurrencyType currencyExchanged = currencyRepository.findByIdAndDeletedAtIsNull(request.getCurrencyIso())
                    .orElseThrow(() -> new RuntimeException("La moneda a cambiar no existe"));
    
            rate.setCurrencyExchange(currencyExchange);
            rate.setCurrencyExchanged(currencyExchanged);
            rate.setExchangeType(request.getExchangeType());
            rate.setValue(request.getValue());
            rate.setStartDate(request.getStartDate());
            rate.setEndDate(request.getEndDate());
            rate.setStatus(request.getStatus());
            rate.setUpdatedAt(LocalDateTime.now());
    
            repository.save(rate);
    
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tasa de cambio actualizada con exito"), Optional.empty())
            );
            
        // }catch(Exception e){
        //     return ResponseEntity.badRequest().body(
        //         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
        //     );
        // }
    }

    // CFG-RF-34 Eliminar tasa
    public ResponseEntity<?> delete(Long id) {

        // try{

            ExchangeRate rate = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("La tasa no existe"));
    
            rate.setDeletedAt(LocalDateTime.now());
            rate.setUpdatedAt(LocalDateTime.now());
    
            repository.save(rate);
    
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Tasa de cambio eliminada con exito"), Optional.empty())
            );

        // }catch(Exception e){
        //     return ResponseEntity.badRequest().body(
        //         ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
        //     );
        // }
    }
}