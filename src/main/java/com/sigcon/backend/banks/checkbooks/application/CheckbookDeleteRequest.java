package com.sigcon.backend.banks.checkbooks.application;

import lombok.Data;

@Data
public class CheckbookDeleteRequest {
    private Long id;
    private String reason;
}