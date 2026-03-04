package com.sigcon.backend.lists_accounting.exchangeRates.application.dto;

import lombok.Data;

import java.time.LocalDate;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.ExchangeType;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.StatusCurrencyExchange;

import jakarta.validation.constraints.NotNull;

@Data
public class CreateExchangeRateRequest {

    @NotNull(message = "La empresa es requerida")
    private Long companyId;

    @NotNull(message = "La moneda cambiada es requerida")
    private Long currencyId;

    @NotNull(message = "La moneda a cambiar es requerida")
    private Long currencyIso;

    @NotNull(message = "El tipo de cambio es requerido")
    private ExchangeType exchangeType;

    @NotNull(message = "El valor es requerido")
    private Double value;

    @NotNull(message = "La fecha inicio es requerida")
    private LocalDate startDate;

    @NotNull(message = "La fecha fin es requerida")
    private LocalDate endDate;
    
    @NotNull(message = "El estado es requerido")
    private StatusCurrencyExchange status;
}