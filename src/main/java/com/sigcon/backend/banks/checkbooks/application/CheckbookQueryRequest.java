package com.sigcon.backend.banks.checkbooks.application;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CheckbookQueryRequest {

    private String checkbookNumber;
    private String issuingBank;
    private String status;
    private Long bankAccountId;

    private LocalDate receivedDateFrom;
    private LocalDate receivedDateTo;

    private LocalDate activationDateFrom;
    private LocalDate activationDateTo;
}