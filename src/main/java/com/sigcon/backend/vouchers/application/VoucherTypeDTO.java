package com.sigcon.backend.vouchers.application;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

@Schema(description = "DTO de visualizacion del tipo de comprobante")
public class VoucherTypeDTO {

    @Schema(description = "ID interno del tipo de comprobante", example = "1")
    private Long id;

    @Schema(description = "Nombre del tipo de comprobante", example = "Factura")
    private String name;

    @Schema(description = "Descripcion del tipo de comprobante", example = "Factura de compra")
    private String description;

    @Schema(description = "Fecha de creacion", example = "2026-01-01")
    private LocalDateTime createdAt;
}
