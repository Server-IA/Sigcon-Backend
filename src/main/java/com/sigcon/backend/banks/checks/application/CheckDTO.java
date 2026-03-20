package com.sigcon.backend.banks.checks.application;

import com.sigcon.backend.banks.checks.domain.model.enums.CheckStatus;
import com.sigcon.backend.banks.checks.domain.model.enums.CheckType;
import com.sigcon.backend.banks.checks.domain.model.enums.ConciliationMethod;
import com.sigcon.backend.banks.checks.domain.model.enums.IncidentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CheckDTO {
    private Long id;
    private CheckbookDTO checkbook;
    private Integer numberCheck;
    private String beneficiary;
    private BigDecimal value;
    private String concept;
    private LocalDate issueDate;
    private LocalDate collectionDate;
    private CheckType typeCheck;
    private CheckStatus statusCheck;
    private Long financialMovementId;
    private String observations;
    private String supportDocumentPath;
    private String supportDocumentMime;
    private String voidReason;
    private LocalDateTime voidedAt;
    private IncidentType incidentType;
    private LocalDate incidentDate;
    private String incidentDetail;
    private String incidentActions;
    private Boolean blockPayment;
    private ConciliationMethod conciliationMethod;
    private String collectionReference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
