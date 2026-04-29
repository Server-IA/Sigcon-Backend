package com.sigcon.backend.lists_accounting.exchangeRates.application.dto;

import lombok.Data;

import java.time.LocalDate;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.ExchangeType;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.StatusCurrencyExchange;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
public class CreateExchangeRateRequest {

    @NotNull(message = "La moneda cambiada es requerida")
    private Long currencyId;

    @NotNull(message = "La moneda a cambiar es requerida")
    private Long currencyIso;

    @NotNull(message = "El tipo de cambio es requerido")
    private ExchangeType exchangeType;

    // HU-CFG-25 MT-01 (2026-04-27): rechazar tasas negativas o cero. Antes
    // el backend aceptaba value=-100 con HTTP 200, persistiendolo en BD.
    @NotNull(message = "El valor es requerido")
    @Positive(message = "La tasa de cambio debe ser un valor positivo mayor a cero")
    private Double value;

    @NotNull(message = "La fecha inicio es requerida")
    private LocalDate startDate;

    @NotNull(message = "La fecha fin es requerida")
    private LocalDate endDate;
    
    @NotNull(message = "El estado es requerido")
    private StatusCurrencyExchange status;
}