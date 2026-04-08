package com.sigcon.backend.vouchers.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class CreateVoucherDTO {

    private Long voucherTypeId;
    private String number;
    private LocalDate date;
    private BigDecimal amount;
    private String description;
    private Long paymentFormId;
    private Long bankAccountId;
    private Long cashAccountId;
    private Long checkId;
    private Long assetId;

}
