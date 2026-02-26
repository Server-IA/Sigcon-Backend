package com.sigcon.backend.lists_accounting.exchangeRates.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.CreateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.ExchangeRateFilterRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.application.dto.UpdateExchangeRateRequest;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeRate;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.repository.ExchangeRateRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository repository;

    // CFG-RF-31 Crear tasa
    public ResponseEntity<?> create(CreateExchangeRateRequest request) {

        if (request.getValue() <= 0) {
            return ResponseEntity.badRequest().body("La tasa debe ser mayor a 0");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            return ResponseEntity.badRequest().body("La fecha inicio no puede ser mayor a la fecha fin");
        }

        boolean overlap = repository.existsOverlap(
                request.getCurrencyId(),
                request.getExchangeType(),
                request.getStartDate(),
                request.getEndDate()
        );

        if (overlap) {
            return ResponseEntity.status(409)
                    .body("Ya existe una tasa en ese rango de fechas");
        }

        ExchangeRate rate = ExchangeRate.builder()
                .currencyId(request.getCurrencyId())
                .currencyIso(request.getCurrencyIso())
                .exchangeType(request.getExchangeType())
                .value(request.getValue())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(request.getStatus())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        repository.save(rate);

        return ResponseEntity.ok(rate);
    }

    // CFG-RF-32 Consultar tasas
    public ResponseEntity<?> findAll(ExchangeRateFilterRequest filter) {

        List<ExchangeRate> rates = repository.findByDeletedAtIsNull();

        return ResponseEntity.ok(rates);
    }

    // CFG-RF-33 Editar tasa
    public ResponseEntity<?> update(Long id, UpdateExchangeRateRequest request) {

        ExchangeRate rate = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("La tasa no existe"));

        if (request.getValue() <= 0) {
            return ResponseEntity.badRequest().body("La tasa debe ser mayor a 0");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            return ResponseEntity.badRequest().body("Fechas inválidas");
        }

        boolean overlap = repository.existsOverlapForUpdate(
                request.getCurrencyId(),
                request.getExchangeType(),
                request.getStartDate(),
                request.getEndDate(),
                id
        );

        if (overlap) {
            return ResponseEntity.status(409)
                    .body("Existe conflicto con otra tasa");
        }

        rate.setCurrencyId(request.getCurrencyId());
        rate.setCurrencyIso(request.getCurrencyIso());
        rate.setExchangeType(request.getExchangeType());
        rate.setValue(request.getValue());
        rate.setStartDate(request.getStartDate());
        rate.setEndDate(request.getEndDate());
        rate.setStatus(request.getStatus());
        rate.setUpdatedAt(LocalDateTime.now());

        repository.save(rate);

        return ResponseEntity.ok(rate);
    }

    // CFG-RF-34 Eliminar tasa
    public ResponseEntity<?> delete(Long id) {

        ExchangeRate rate = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("La tasa no existe"));

        rate.setDeletedAt(LocalDateTime.now());
        rate.setUpdatedAt(LocalDateTime.now());

        repository.save(rate);

        return ResponseEntity.ok("Tasa eliminada correctamente");
    }
}