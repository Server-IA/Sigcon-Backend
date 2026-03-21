package com.sigcon.backend.checkbooks.application;

import com.sigcon.backend.checkbooks.domain.model.enums.CheckbookStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CheckbookRequest {

    private Long bankAccountId;
    private String checkbookNumber;
    private String issuingBank;

    private Long checkStartNumber;
    private Long checkEndNumber;

    private LocalDate receivedDate;
    private LocalDate activationDate;

    private CheckbookStatus status;

    private String observations;
}
