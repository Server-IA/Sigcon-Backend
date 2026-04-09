package com.sigcon.backend.banks.financialmovements.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherMatchSuggestionDTO {
    private Long id;
    private String number;
    private LocalDate date;
    private BigDecimal amount;
    private String description;
}
