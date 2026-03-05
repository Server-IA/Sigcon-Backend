package com.sigcon.backend.general.config;

import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden

@RestControllerAdvice

public class GlobalExceptionHandler  {
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.ofEntries(
        // Parametrización  
        Map.entry("uk_users_email_active", "Ya existe un usuario activo con ese email."),
        Map.entry("uk_users_username_active", "El nombre de usuario ya está en uso."),
        Map.entry("uk_roles_active", "Ya existe un rol activo con ese nombre."),
        Map.entry("uk_permissions_active", "Ya existe un permiso activo con ese código."),
        Map.entry("uk_modules_active", "Ya existe un módulo con esa URL."),
        Map.entry("uk_menu_permissions_active", "Ya existe un permiso de menú activo con ese menú y rol."),
        Map.entry("uk_menus_active", "Ya existe un menú activo con ese módulo y path."),
        Map.entry("uk_user_parameters_active", "Ya existe un parámetro activo con ese usuario y parámetro."),
        Map.entry("uk_parameters_active", "Ya existe un parámetro activo con ese nombre."),
        Map.entry("45000", "Debe existir al menos un usuario con SUPERADMIN."),

        // Listas de cuentas contables
        Map.entry("uk_puc_code_active", "Ya existe un código de cuenta contable (PUC) activo con ese código."),
        Map.entry("uk_puc_name_active", "Ya existe un nombre de cuenta contable (PUC) activo con ese nombre."),
        Map.entry("uk_cost_center_code_company_active", "Ya existe un centro de costo activo con ese código."),
        Map.entry("uk_depretation_rule_type_accounting_account_effective_date_acti", "Ya existe una regla de depreciación activa con ese tipo de depreciación, cuenta contable y fecha efectiva."),
        Map.entry("uk_currency_type_iso_code_active", "Ya existe un tipo de moneda activo con ese código ISO."),
        Map.entry("uk_accounting_account_custom_name_company_active", "Ya existe una cuenta contable activa con ese nombre."),
        Map.entry("no_overlapping_exchange_rates", "Ya existe una tasa de cambio activa con ese tipo de cambio, moneda de cambio, moneda cambiada y rango de fechas."),
        Map.entry("uk_ruler_tax_type_ruler_tax_name_company_active", "Ya existe una regla de impuesto activa con ese tipo de regla de impuesto y nombre."),
        Map.entry("uk_accounting_account_ruler_tax_id_active", "Ya existe una cuenta contable activa con esa regla de impuesto y cuenta contable.")
    );

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        Throwable root = ex.getRootCause();

        String rootMessage = root != null
                ? root.getMessage()
                : ex.getMessage();

        System.out.println("rootMessage: " + rootMessage);

        Optional<String> errorMessage = CONSTRAINT_MESSAGES.entrySet()
            .stream()
            .filter(entry -> rootMessage != null && rootMessage.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();

            // Detectar mensaje del trigger directamente
        if (rootMessage != null && rootMessage.contains("Debe existir al menos un usuario con SUPERADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No se puede eliminar o modificar el único SUPERADMIN")
                        )
                    );
        }

        if (errorMessage.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(errorMessage)
                    );
        }

        // if (rootMessage != null && rootMessage.contains("UP0001")) {
        //     return ResponseEntity
        //             .status(HttpStatus.BAD_REQUEST)
        //             .body(
        //                 ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe existir al menos un usuario con SUPERADMIN"))
        //             );
        // }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error de integridad de datos."))
                );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {

        System.out.println("ex.getMessage(): " + ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage()))
                );
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException ex) {

        System.out.println("ex.getMessage() IllegalStateException: " + ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage()))
                );
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {

        String message = ex.getMessage();

        System.out.println("ex.getMessage() Exception: " + message);

        if (message != null && message.contains("Debe existir al menos un usuario con SUPERADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Debe existir al menos un usuario con SUPERADMIN.")
                        )
                    );
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(
                        Optional.of(message != null ? message : "Error interno del servidor.")
                    )
                );
    }


    
    
}
