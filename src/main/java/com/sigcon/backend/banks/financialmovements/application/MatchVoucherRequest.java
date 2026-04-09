package com.sigcon.backend.banks.financialmovements.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchVoucherRequest {
    private Long voucherId;
    private Long bankAccountId;
}
