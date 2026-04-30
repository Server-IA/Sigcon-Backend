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
    /**
     * QA-BLOQUE-AP v2: true si el JE/Voucher tiene linea sobre la cuenta del
     * banco. Usado en el frontend para mostrar badge visual al contador.
     */
    private Boolean affectsAccount;
}
