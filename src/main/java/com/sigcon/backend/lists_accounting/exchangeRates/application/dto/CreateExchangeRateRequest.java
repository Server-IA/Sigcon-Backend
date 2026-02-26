package com.sigcon.backend.lists_accounting.exchangeRates.application.dto;

import lombok.Data;

import java.time.LocalDate;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeType;

@Data
public class CreateExchangeRateRequest {

    private Long currencyId;
    private String currencyIso;
    private ExchangeType exchangeType;
    private Double value;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}