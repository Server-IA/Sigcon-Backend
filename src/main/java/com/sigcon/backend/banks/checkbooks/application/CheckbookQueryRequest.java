package com.sigcon.backend.banks.checkbooks.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CheckbookQueryRequest {

    // =========================
    // DataTables
    // =========================

    @Schema(example = "1")
    private Integer draw;

    @Schema(example = "0")
    private Integer start;

    @Schema(example = "20")
    private Integer length;

    private Search search;

    // =========================
    // Custom filters
    // =========================

    @Schema(hidden = true)
    private String checkbookNumber;

    @Schema(hidden = true)
    private String issuingBank;

    @Schema(hidden = true)
    private String status;

    @Schema(hidden = true)
    private Long bankAccountId;

    @Schema(hidden = true)
    private LocalDate receivedDateFrom;

    @Schema(hidden = true)
    private LocalDate receivedDateTo;

    @Schema(hidden = true)
    private LocalDate activationDateFrom;

    @Schema(hidden = true)
    private LocalDate activationDateTo;

    @Data
    public static class Search {

        @Schema(example = "1001")
        private String value;

        @Schema(example = "true")
        private Boolean regex;
    }

    @Schema(hidden = true)
    private Object columns;

    @Schema(hidden = true)
    private Object order; 
}