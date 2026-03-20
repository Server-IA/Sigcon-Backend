package com.sigcon.backend.checkbooks.application;

import lombok.Data;

@Data
public class CheckbookQueryRequest {

    private String checkbookNumber;
    private String issuingBank;
    private String status;
    private Long bankAccountId;
}