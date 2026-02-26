package com.sigcon.backend.exchangeRates.application.dto;

import com.sigcon.backend.exchangeRates.domain.model.ExchangeType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExchangeRateFilterRequest {

    private Long currencyId;
    private ExchangeType exchangeType;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
}