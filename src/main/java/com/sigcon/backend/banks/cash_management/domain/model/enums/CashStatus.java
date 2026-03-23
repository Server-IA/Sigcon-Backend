package com.sigcon.backend.banks.cash_management.domain.model.enums;

public enum CashStatus {

    ACTIVE,        // Caja Activa.
    INACTIVE,     // Caja Inactiva, solo se puede realizar consultas, no admite operaciones.
    CLOSED       //Caja Cerrada, estado final de la caja irreversible.

}
