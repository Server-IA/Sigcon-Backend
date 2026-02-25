package com.sigcon.backend.depretation_rules.exception;

public class DuplicateDepretationRuleException extends RuntimeException {
    public DuplicateDepretationRuleException(String message) {
        super(message);
    }

    public DuplicateDepretationRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
