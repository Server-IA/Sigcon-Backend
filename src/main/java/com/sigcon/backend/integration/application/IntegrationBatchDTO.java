package com.sigcon.backend.integration.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta para exponer un lote AAEF almacenado (consultas internas y frontend).
 *
 * <p>NO incluye el payloadJson (es grande y se sirve por separado en el endpoint de
 * descarga).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationBatchDTO {

    private Long id;
    private String exchangeId;
    private String standardVersion;
    private String sourceSystemId;
    private String sourceSystemName;
    private String sourceSystemNit;
    private String environment;
    private LocalDate periodFrom;
    private LocalDate periodTo;

    private Integer totalDocuments;
    private Integer totalInvoices;
    private Integer totalTransactions;
    // Nota: totalPayroll fue removido - el bloque payroll del estandar AAEF
    // original era un borrador desestimado del alcance del proyecto.
    private BigDecimal totalGrossAmount;
    private BigDecimal totalTaxes;
    private BigDecimal totalNet;
    private String currency;

    private String status;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime ackSentAt;
    private Integer ackRetryCount;
    private String errorMessage;
}
