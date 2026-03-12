package com.sigcon.backend.third_parties.ecl_segmentation.application;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO para recibir datos de AR (Accounts Receivable) necesarios para la segmentación de riesgo ECL
//Se creo para reemplazar de manera momentanea los datos que se deben recibir de AR (Accounts Receivable) 
// y que son necesarios para la segmentación de riesgo ECL, mientras se desarrolla la integración completa con AR. 
// Este DTO actúa como un contenedor temporal para los datos que se necesitan para realizar la segmentación de riesgo, 
// permitiendo avanzar en el desarrollo de la funcionalidad de segmentación sin depender completamente de la integración con AR. 
// Una vez que la integración con AR esté completa, este DTO podrá ser eliminado o adaptado según sea necesario.

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Schema(name = "ArDataDTO", description = "DTO temporal para recibir datos de Cuentas por Cobrar (AR) necesarios para la segmentación de riesgo ECL." +
                    "Reemplaza momentáneamente la integración con el módulo AR mientras este se implementa." +
                          "Una vez implementado el módulo AR, este DTO podrá ser eliminado o adaptado.")
public class ArDataDTO {

    @Schema(description = "Identificador único del cliente", example = "1")
    private Long clientId; //Identificador unico (ID) del Cliente
    @Schema(description = "Días máximos en mora del cliente", example = "30")
    private Integer overdueDays; //Dias Maximos en mora del Cliente
    @Schema(description = "Monto total vencido del cliente", example = "1500000.00")
    private BigDecimal overdueAmount; //Monto total vencido del cliente 
    @Schema(description = "Indica si los datos AR estan disponibles y si son validos", example = "true")
    private Boolean dataAvailable; //Indica si los datos AR estan disponibles y si son validos
}
