package com.sigcon.backend.banks.checkbooks.application;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CheckbookQueryRequest {

    // DataTables
    private Integer draw;
    private Integer start;
    private Integer length;

    private Search search;

    // filtros específicos
    private String checkbookNumber;
    private String issuingBank;
    private String status;
    private Long bankAccountId;

    private LocalDate receivedDateFrom;
    private LocalDate receivedDateTo;

    private LocalDate activationDateFrom;
    private LocalDate activationDateTo;

    @Data
    public static class Search {
        private String value;
        private Boolean regex;
    }
}