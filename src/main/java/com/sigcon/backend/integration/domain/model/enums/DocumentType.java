package com.sigcon.backend.integration.domain.model.enums;

/**
 * Tipos de documento soportados en el estandar AAEF (RF-INT-13 v4.0).
 *
 * <p>Cada lote AAEF puede contener multiples documentos de distintos tipos.
 * Se mapean a las entidades SIGCON correspondientes en los mappers de Fase 2.
 *
 * <p>Nota: el grupo "PAYROLL" del estandar AAEF original era un borrador del
 * grupo de documentacion y fue desestimado del alcance del proyecto.
 */
public enum DocumentType {

    /** Factura (venta o compra, segun Type.Code 01|02|03|04 del AAEF). */
    INVOICE,

    /** Transaccion (PAY, ADV, REF, ADJ segun Type.Code del AAEF). */
    TRANSACTION
}
