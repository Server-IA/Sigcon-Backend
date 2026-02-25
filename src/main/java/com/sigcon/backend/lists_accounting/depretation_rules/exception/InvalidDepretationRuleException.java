package com.sigcon.backend.lists_accounting.depretation_rules.exception;

public class InvalidDepretationRuleException extends RuntimeException {
    public InvalidDepretationRuleException(String message) {
        super(message);
    }

    public InvalidDepretationRuleException(String message, Throwable cause) {
        super(message, cause);
    }

}
