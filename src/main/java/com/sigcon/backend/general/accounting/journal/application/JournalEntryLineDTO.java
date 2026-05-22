package com.sigcon.backend.general.accounting.journal.application;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para una linea de detalle de asiento contable.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JournalEntryLineDTO {

    private Long id;
    private Integer lineOrder;
    private Long accountingAccountId;
    private String accountCode;
    private String accountName;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String description;
    private String thirdPartyNit;
    private Long costCenterId;
    private String costCenterName;

    /**
     * HU-CG-05C E3: true si el NIT de la linea corresponde a un tercero que
     * actualmente esta INACTIVO en el catalogo de Terceros (para mostrar el
     * aviso en la consulta del comprobante). null/false si no aplica.
     */
    private Boolean thirdPartyInactive;
}
