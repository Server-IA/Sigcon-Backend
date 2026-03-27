package com.sigcon.backend.banks.checkbooks.application;

import com.sigcon.backend.banks.checkbooks.domain.model.enums.CheckbookStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CheckbookDTO {

    private Long id;
    private Long bankAccountId;

    private String checkbookNumber;
    private String issuingBank;

    private Long checkStartNumber;
    private Long checkEndNumber;

    private Integer totalChecks;
    private Integer usedChecks;
    private Integer availableChecks;

    private LocalDate receivedDate;
    private LocalDate activationDate;

    private CheckbookStatus status;
    private String observations;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}