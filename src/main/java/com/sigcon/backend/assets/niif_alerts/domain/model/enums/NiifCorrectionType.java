package com.sigcon.backend.assets.niif_alerts.domain.model.enums;

/**
 * Tipos de correccion NIIF para activos fijos.
 * USEFUL_LIFE_ADJUSTMENT: Cambio de estimacion de vida util (NIC 8).
 * DEPRECIATION_METHOD_CHANGE: Cambio de metodo de depreciacion.
 * REVALUATION: Modelo de revaluacion (NIC 16 S31).
 * IMPAIRMENT_REVERSAL: Reversion de deterioro previamente reconocido (NIC 36).
 */
public enum NiifCorrectionType {

    USEFUL_LIFE_ADJUSTMENT,
    DEPRECIATION_METHOD_CHANGE,
    REVALUATION,
    IMPAIRMENT_REVERSAL

}