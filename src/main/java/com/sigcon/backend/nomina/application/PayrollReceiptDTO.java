package com.sigcon.backend.nomina.application;

import com.sigcon.backend.nomina.domain.model.PayrollLine;
import com.sigcon.backend.nomina.domain.model.PayrollReceipt;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Recibo de nomina con lineas de concepto (HU-NOM-03/04/06).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Recibo de nomina con lineas de concepto (HU-NOM-03/04/06)")
public class PayrollReceiptDTO {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeDocument;

    private Integer periodYear;
    private Integer periodMonth;
    private String periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer daysWorked;

    private BigDecimal totalEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal totalEmployerContributions;
    private BigDecimal netPay;

    @Schema(description = "Estado", allowableValues = {"DRAFT", "APPROVED", "CLOSED"})
    private String status;

    @Schema(description = "ID del JournalEntry generado")
    private Long journalEntryId;

    private String approvedBy;
    private LocalDateTime approvedAt;
    private String closedBy;
    private LocalDateTime closedAt;
    private String notes;

    @Schema(description = "Lineas de concepto aplicadas")
    private List<PayrollLineDTO> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Linea de concepto dentro del recibo")
    public static class PayrollLineDTO {
        private Long id;
        private String conceptCode;
        private String conceptName;
        @Schema(allowableValues = {"EARNING", "DEDUCTION", "EMPLOYER_CONTRIBUTION"})
        private String lineType;
        private BigDecimal amount;
        private Integer lineOrder;
    }

    public static PayrollReceiptDTO from(PayrollReceipt r, String employeeName,
                                           String employeeDocument, List<PayrollLine> lines) {
        return PayrollReceiptDTO.builder()
                .id(r.getId())
                .employeeId(r.getEmployeeId())
                .employeeName(employeeName)
                .employeeDocument(employeeDocument)
                .periodYear(r.getPeriodYear())
                .periodMonth(r.getPeriodMonth())
                .periodType(r.getPeriodType())
                .periodStart(r.getPeriodStart())
                .periodEnd(r.getPeriodEnd())
                .daysWorked(r.getDaysWorked())
                .totalEarnings(r.getTotalEarnings())
                .totalDeductions(r.getTotalDeductions())
                .totalEmployerContributions(r.getTotalEmployerContributions())
                .netPay(r.getNetPay())
                .status(r.getStatus())
                .journalEntryId(r.getJournalEntryId())
                .approvedBy(r.getApprovedBy())
                .approvedAt(r.getApprovedAt())
                .closedBy(r.getClosedBy())
                .closedAt(r.getClosedAt())
                .notes(r.getNotes())
                .lines(lines == null ? List.of()
                        : lines.stream().map(l -> PayrollLineDTO.builder()
                                .id(l.getId())
                                .conceptCode(l.getConceptCode())
                                .conceptName(l.getConceptName())
                                .lineType(l.getLineType())
                                .amount(l.getAmount())
                                .lineOrder(l.getLineOrder())
                                .build())
                                .collect(Collectors.toList()))
                .build();
    }
}
