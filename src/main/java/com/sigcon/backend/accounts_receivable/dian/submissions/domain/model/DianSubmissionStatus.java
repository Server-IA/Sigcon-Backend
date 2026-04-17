package com.sigcon.backend.accounts_receivable.dian.submissions.domain.model;

/**
 * Estados de la transmision al proveedor tecnologico (PSE) / DIAN.
 */
public enum DianSubmissionStatus {
    /** Pendiente de envio o esperando respuesta. */
    PENDING,
    /** Documento aceptado por la DIAN. */
    ACCEPTED,
    /** Documento rechazado por la DIAN. */
    REJECTED
}
