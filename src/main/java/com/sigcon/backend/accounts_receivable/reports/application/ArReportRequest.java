package com.sigcon.backend.accounts_receivable.reports.application;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AR-05: Request generico para consultar reportes de Cuentas por Cobrar.
 * Soporta filtros por cliente, estado, centro de costo y rango de fechas.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArReportRequest {

    /** Identificador del tercero cliente (opcional). */
    private Long thirdPartyId;

    /** Estado de factura a filtrar (opcional). */
    private String status;

    /** Identificador de centro de costo (opcional, reservado). */
    private Long costCenterId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
