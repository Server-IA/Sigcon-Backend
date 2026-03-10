package com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums;

public enum AccountClass {
    
    ASSET(AccountNature.DEBIT),                      
    LIABILITY(AccountNature.CREDIT),                 
    EQUITY(AccountNature.CREDIT),                    
    REVENUE(AccountNature.CREDIT),                   
    EXPENSE(AccountNature.DEBIT),                    
    COST_OF_SALES(AccountNature.DEBIT),              
    PRODUCTION_COST(AccountNature.DEBIT),            
    MEMORANDUM_DEBIT(AccountNature.DEBIT),           
    MEMORANDUM_CREDIT(AccountNature.CREDIT);         

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