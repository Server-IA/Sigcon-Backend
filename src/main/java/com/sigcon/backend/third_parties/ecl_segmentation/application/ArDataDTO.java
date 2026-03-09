package com.sigcon.backend.third_parties.ecl_segmentation.application;

import java.math.BigDecimal;

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
public class ArDataDTO {

    private Long clientId; //Identificador unico (ID) del Cliente
    private Integer overdueDays; //Dias Maximos en mora del Cliente
    private BigDecimal overdueAmount; //Monto total vencido del cliente 
    private Boolean dataAvailable; //Indica si los datos AR estan disponibles y si son validos


}
