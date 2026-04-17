package com.sigcon.backend.integration.domain.model.enums;

/**
 * Origen de un documento contable en SIGCON.
 *
 * <p>Se almacena en el campo {@code source} de las entidades existentes
 * (SalesInvoice, Invoices, ArPayment, ApPayment, etc.) para permitir
 * diferenciar documentos creados manualmente por el contador vs recibidos
 * por la integracion AAEF con AgroFusion.
 *
 * <p>Por defecto todos los documentos historicos quedan como {@code MANUAL}
 * (campo con DEFAULT 'MANUAL' en la migracion V32).
 */
public enum SourceOrigin {

    /** Documento creado manualmente por un usuario desde el frontend de SIGCON. */
    MANUAL,

    /** Documento recibido via integracion AAEF con AgroFusion. */
    AAEF
}
