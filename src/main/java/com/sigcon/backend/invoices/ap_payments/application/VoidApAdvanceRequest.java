package com.sigcon.backend.invoices.ap_payments.application;

import lombok.Data;

/**
 * AP-RF-05 E7 (Bloque DV): payload para anular un anticipo o revertir una
 * aplicacion. Lleva el motivo (obligatorio para anular, min 10 caracteres).
 */
@Data
public class VoidApAdvanceRequest {
    private String reason;
}
