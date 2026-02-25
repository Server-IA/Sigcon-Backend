package com.sigcon.backend.lists_accounting.depretation_rules.exception;

public class DuplicateDepretationRuleException extends RuntimeException {
    public DuplicateDepretationRuleException(String message) {
        super(message);
    }

    public DuplicateDepretationRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
