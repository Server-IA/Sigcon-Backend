package com.sigcon.backend.accounting_lists.domain.model.enums;

public enum AccountClass {

    // Cada clase de cuenta tiene una naturaleza esperada (deudora o acreedora)
    // Esto se puede usar para validar que la naturaleza seleccionada para una cuenta sea coherente con su clase
    // evitando así repetir codigo de validación en el service y centralizando esa lógica en el enum
    
    ASSET(AccountNature.DEBIT),                      // Activo
    LIABILITY(AccountNature.CREDIT),                 // Pasivo
    EQUITY(AccountNature.CREDIT),                    // Patrimonio
    REVENUE(AccountNature.CREDIT),                   // Ingresos
    EXPENSE(AccountNature.DEBIT),                    // Gastos
    COST_OF_SALES(AccountNature.DEBIT),              // Costos de venta
    PRODUCTION_COST(AccountNature.DEBIT),            // Costos de produccion u operacion
    MEMORANDUM_DEBIT(AccountNature.DEBIT),           // Cuentas de orden deudoras
    MEMORANDUM_CREDIT(AccountNature.CREDIT);         // Cuentas de orden acreedoras

    private final AccountNature expectedNature;

    AccountClass(AccountNature expectedNature) {
        this.expectedNature = expectedNature;
    }

    public AccountNature getExpectedNature() {
        return expectedNature;
    }

    public boolean matchesNature(AccountNature nature) {
        return expectedNature == nature;
    }
}
