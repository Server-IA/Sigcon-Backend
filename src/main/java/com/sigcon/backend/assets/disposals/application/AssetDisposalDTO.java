package com.sigcon.backend.assets.disposals.application;

import com.sigcon.backend.assets.disposals.domain.model.enums.DisposalType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de lectura para bajas y transferencias de activos.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetDisposalDTO {

    private Long id;
    private Long assetId;
    private String assetCode;
    private String assetName;
    private DisposalType disposalType;
    private LocalDate disposalDate;
    private BigDecimal disposalAmount;
    private BigDecimal bookValueAtDisposal;
    private BigDecimal gainLoss;
    private String reason;
    private String destinationInfo;
    private Long journalEntryId;
    private LocalDateTime createdAt;
}
