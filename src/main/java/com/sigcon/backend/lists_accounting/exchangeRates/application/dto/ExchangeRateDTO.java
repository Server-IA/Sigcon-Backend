package com.sigcon.backend.lists_accounting.exchangeRates.application.dto;

import java.time.LocalDate;

import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.ExchangeType;
import com.sigcon.backend.lists_accounting.exchangeRates.domain.model.Enums.StatusCurrencyExchange;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class ExchangeRateDTO {

    private Long id;
    private CurrencyTypeResponseDTO currencyExchange;
    private CurrencyTypeResponseDTO currencyExchanged;
    private ExchangeType exchangeType;
    private Double value;
    private LocalDate startDate;
    private LocalDate endDate;
    private StatusCurrencyExchange status;
}
