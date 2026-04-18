package com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model;

/**
 * Estados posibles de una resolucion de numeracion emitida por la DIAN.
 * Controla si la resolucion puede asignar consecutivos de facturacion electronica.
 */
public enum DianResolutionStatus {
    /** Resolucion vigente y con rango de numeracion disponible. */
    ACTIVE,
    /** Resolucion con fecha de vigencia finalizada. */
    EXPIRED,
    /** Se agoto el rango de numeracion autorizado. */
    EXHAUSTED
}
