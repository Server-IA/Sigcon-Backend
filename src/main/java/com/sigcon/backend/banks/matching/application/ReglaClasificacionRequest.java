package com.sigcon.backend.banks.matching.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** BNK-HU-071: request para crear/editar una regla de clasificación. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Regla de clasificación de movimientos del extracto")
public class ReglaClasificacionRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private Integer prioridad;

    @NotBlank(message = "El patrón regex es obligatorio")
    private String patronRegex;

    /** DEBITO | CREDITO | CUALQUIERA */
    private String signo;

    private BigDecimal montoMin;
    private BigDecimal montoMax;

    @NotBlank(message = "El tipo de movimiento es obligatorio")
    private String tipoMovimiento;

    private String cuentaPucSugerida;

    /** GLOBAL | BANCO | CUENTA */
    private String alcance;
    private Long bancoId;
    private Long cuentaBancariaId;

    private Boolean activa;
}
