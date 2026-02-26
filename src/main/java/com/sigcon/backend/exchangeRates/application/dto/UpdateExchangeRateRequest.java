package com.sigcon.backend.exchangeRates.application.dto;

import com.sigcon.backend.exchangeRates.domain.model.ExchangeType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateExchangeRateRequest {

    private Long currencyId;
    private String currencyIso;
    private ExchangeType exchangeType;
    private Double value;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}