package com.sigcon.backend.lists_accounting.exchangeRates.application.dto;

import lombok.Data;

import java.time.LocalDate;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.ExchangeType;

@Data
public class ExchangeRateFilterRequest {

    private Long currencyId;
    private ExchangeType exchangeType;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
}