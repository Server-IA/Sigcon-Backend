package com.sigcon.backend.exchangeRates.infrastructure.controller;

import com.sigcon.backend.exchangeRates.application.dto.CreateExchangeRateRequest;
import com.sigcon.backend.exchangeRates.application.dto.ExchangeRateFilterRequest;
import com.sigcon.backend.exchangeRates.application.dto.UpdateExchangeRateRequest;
import com.sigcon.backend.exchangeRates.application.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService service;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateExchangeRateRequest request) {
        return service.create(request);
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody(required = false) ExchangeRateFilterRequest filter) {
        return service.findAll(filter);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @RequestBody UpdateExchangeRateRequest request
    ) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return service.delete(id);
    }
}
