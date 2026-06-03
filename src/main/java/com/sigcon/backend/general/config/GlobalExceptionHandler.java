package com.sigcon.backend.general.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sigcon.backend.utils.ErrorRespondJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;

@Hidden

@RestControllerAdvice

public class GlobalExceptionHandler {

    ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> CONSTRAINT_MESSAGES = new HashMap<>();

    /**
     * HU-AU-07 E2 / HU-AU-08 E2 (QA Bloque AJ-AU): registrar en el log de
     * auditoria los intentos de acceso NO autorizado al modulo de auditoria.
     * Opcional (required=false) para no acoplar el handler global a la
     * disponibilidad del modulo de auditoria.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.audit.domain.service.AuditPublisher auditPublisher;

    private static class ConstraintMessages {
        private String code;
        private String message;

        public String getMessage() {
            return message;
        }

        public String getCode() {
            return code;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    @PostConstruct
    public void loadConstraintMessages() {

        try {

            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:jsons/*.json");

            for (Resource resource : resources) {

                System.out.println("Procesando archivo: " + resource.getFilename());

                var list = objectMapper.readValue(
                        resource.getInputStream(),
                        new TypeReference<java.util.List<ConstraintMessages>>() {
                        });

                for (ConstraintMessages item : list) {
                    CONSTRAINT_MESSAGES.put(item.code, item.message);
                }

            }

            System.out.println("✔ Mensajes de constraints cargados: " + CONSTRAINT_MESSAGES.size());

        } catch (Exception e) {
            System.out.println("⚠ Error leyendo JSON: " + e.getMessage());
            throw new RuntimeException("Error leyendo archivos JSON", e);
        }
    }

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
        if (rootMessage != null && rootMessage.contains("Debe existir al menos un usuario con ADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            ErrorRespondJson.getErrorRespondMessage(
                                    Optional.of("No se puede eliminar o modificar el único ADMIN")));
        }

        if (errorMessage.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            ErrorRespondJson.getErrorRespondMessage(errorMessage));
        }

        // if (rootMessage != null && rootMessage.contains("UP0001")) {
        // return ResponseEntity
        // .status(HttpStatus.BAD_REQUEST)
        // .body(
        // ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe existir al menos un
        // usuario con ADMIN"))
        // );
        // }

        // ERR-MNT-TER-003 + BUG-01 (TER, 2026-06-02): mensaje descriptivo que
        // corresponde a la CAUSA REAL. Antes, cualquier violacion con
        // 'third_part'/'tercero' se traducia como "tiene registros asociados"
        // (dependencia), incluso cuando era una violacion de UNICIDAD en un
        // INSERT (creacion). Ahora distinguimos: violacion de llave unica =
        // duplicidad; violacion de llave foranea = dependencia.
        boolean isUniqueViolation = rootMessage != null && (rootMessage.contains("duplicate key")
                || rootMessage.contains("unique constraint") || rootMessage.contains("llave duplicada")
                || rootMessage.contains("restricción de unicidad") || rootMessage.contains("ya existe la llave"));
        boolean isForeignKeyViolation = rootMessage != null && (rootMessage.contains("foreign key")
                || rootMessage.contains("llave foránea") || rootMessage.contains("clave foránea"));
        String descriptiveMessage = "Error de integridad de datos.";
        if (rootMessage != null) {
            if (rootMessage.contains("third_part") || rootMessage.contains("tercero")) {
                if (isUniqueViolation)
                    descriptiveMessage = "Ya existe un tercero con el mismo NIT o código en esta empresa.";
                else if (isForeignKeyViolation)
                    descriptiveMessage = "No es posible realizar esta operación porque el tercero tiene registros asociados en otros módulos. Verifique y retire las dependencias antes de continuar.";
                else
                    descriptiveMessage = "No se pudo completar la operación sobre el tercero por una restricción de integridad de datos.";
            } else if (rootMessage.contains("compan") || rootMessage.contains("empresa"))
                descriptiveMessage = "No es posible realizar esta operación porque la empresa tiene registros asociados.";
            else if (rootMessage.contains("cost_center") || rootMessage.contains("centro"))
                descriptiveMessage = "No es posible realizar esta operación porque el centro de costo tiene registros asociados.";
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(descriptiveMessage)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {

        System.out.println("ex.getMessage(): " + ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage())));
    }

    /**
     * HU-TENANT-01: al intentar acceder a un recurso de otra empresa por PK,
     * devolvemos 404 (no 403) para no revelar la existencia del recurso en
     * otra empresa. Disparado por @PostLoad listener de entidades
     * tenant-scoped.
     */
    @ExceptionHandler(com.sigcon.backend.platform.tenant.TenantIsolationException.class)
    public ResponseEntity<?> handleTenantIsolation(
            com.sigcon.backend.platform.tenant.TenantIsolationException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Recurso no encontrado")));
    }

    /**
     * HU-AP-02 E3: Optimistic locking. Cuando dos usuarios cargan la misma
     * factura simultaneamente y uno guarda primero, el segundo recibe
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * (wrapper de Spring sobre {@link jakarta.persistence.OptimisticLockException}).
     * Devolvemos HTTP 409 Conflict con el mensaje exacto de la HU para que el
     * usuario sepa que debe recargar y reintentar.
     */
    @ExceptionHandler({
            org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<?> handleOptimisticLock(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Esta factura fue modificada por otro usuario. "
                                + "Recarga los datos y vuelve a intentarlo.")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException ex) {

        System.out.println("ex.getMessage() IllegalStateException: " + ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage())));
    }

    /** Captura RuntimeException como HTTP 400 (errores de negocio) */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        System.out.println("RuntimeException: " + ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of(ex.getMessage() != null ? ex.getMessage() : "Error en la operación.")));
    }

    /** Captura NoSuchElementException como HTTP 404 (recurso no encontrado) */
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<?> handleNoSuchElement(java.util.NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of(ex.getMessage() != null ? ex.getMessage() : "Recurso no encontrado.")));
    }

    /**
     * Captura AccessDeniedException como HTTP 403 (sin permisos).
     *
     * <p>HU-AU-07 E2 / HU-AU-08 E2 / HU-AU-09 E6 (2026-04-28): mensaje contextual
     * segun el endpoint accedido. Para rutas del modulo Auditoria devuelve
     * el mensaje exacto que pide la HU correspondiente.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "";
        String message;
        if (path.contains("/audit/dashboard")) {
            message = "Acceso restringido al dashboard";
        } else if (path.contains("/audit/logs/journal-entry")
                || path.contains("/audit/logs/entity")) {
            message = "Acceso restringido para vinculacion";
        } else if (path.contains("/audit")) {
            message = "Acceso denegado";
        } else {
            message = "No tiene permisos para realizar esta acción.";
        }
        // HU-AU-07 E2 / HU-AU-08 E2: dejar traza del intento NO autorizado al
        // modulo de auditoria. Defensivo: jamas debe romper la respuesta 403.
        if (auditPublisher != null && path.contains("/audit")) {
            try {
                String user = "anonimo";
                var auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null && auth.getName() != null) user = auth.getName();
                auditPublisher.publish(
                        com.sigcon.backend.audit.domain.model.enums.AuditAction.VIEW,
                        com.sigcon.backend.audit.domain.model.enums.AuditModule.AU,
                        com.sigcon.backend.audit.domain.model.enums.AuditSeverity.HIGH,
                        "AccessDenied", null,
                        "Intento de acceso NO autorizado a " + path + " por usuario '" + user + "'",
                        null, null, null);
            } catch (Exception ignored) { /* la auditoria no debe romper el 403 */ }
        }
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(message)));
    }

    /**
     * QA Bloque BE (2026-05-17): handler dedicado para errores de validacion
     * Bean Validation (@Valid). Antes caia al handler generico Exception.class
     * y devolvia 500. La semantica correcta es 400 con detalle de los field
     * errors (mismo formato que ErrorRespondJson.getErrorRespondJson para
     * BindingResult). Asi la UI puede pintar errores por campo y el log no
     * ensucia con stacktraces de validation comunes.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorRespondJson.getErrorRespondJson(ex.getBindingResult()));
    }

    /**
     * QA Bloque BE (2026-05-17): body request invalido o ausente (JSON
     * corrupto, body vacio en endpoint que lo requiere). Antes Exception ->
     * 500. Ahora devuelve 400 con mensaje legible (sin filtrar el internal
     * de Jackson).
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        String raw = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : (ex.getMessage() != null ? ex.getMessage() : "Body de la solicitud invalido o ausente");

        // QA Activos (2026-05-25) Error 01: cuando un campo numerico recibe un
        // valor fuera del rango del tipo (ej. vida util = 1e+26 -> Integer),
        // Jackson falla ANTES de la validacion Bean con un mensaje tecnico
        // incomprensible ("Numeric value (1e+26) out of range of int..."). Lo
        // traducimos a un mensaje claro para el usuario final.
        String msg;
        if (raw != null && (raw.contains("out of range") || raw.contains("Numeric value"))) {
            msg = "Uno de los valores numericos ingresados esta fuera del rango permitido. "
                    + "Revise los campos numericos (por ejemplo la vida util en meses debe ser un numero entero razonable).";
        } else if (raw != null && (raw.contains("Cannot coerce") || raw.contains("Cannot deserialize")
                || raw.contains("not a valid") || raw.contains("JSON parse"))) {
            msg = "Hay un valor con formato invalido en la solicitud. Verifique los campos e intente de nuevo.";
        } else {
            msg = raw != null ? raw : "Body de la solicitud invalido o ausente";
            // Quitar la referencia tecnica de Jackson "at [Source: ...]"
            int srcIdx = msg.indexOf(" at [Source");
            if (srcIdx > 0) msg = msg.substring(0, srcIdx);
            if (msg.length() > 300) msg = msg.substring(0, 300) + "...";
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(msg)));
    }

    /**
     * QA Bloque BE (2026-05-17): metodo HTTP no soportado por el endpoint
     * (ej. POST a una ruta GET). 405 Method Not Allowed en lugar de 500.
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Metodo HTTP no soportado para este endpoint: " + ex.getMethod())));
    }

    /**
     * QA Bloque BE (2026-05-17): ruta inexistente (no static resource ...).
     * Antes Exception -> 500. Ahora 404 limpio.
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Recurso no encontrado")));
    }

    /**
     * QA Bloque BE (2026-05-17): parametros requeridos del request faltantes
     * (ej. @RequestParam sin defaultValue). Antes Exception -> 500.
     */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParam(
            org.springframework.web.bind.MissingServletRequestParameterException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Falta el parametro requerido: " + ex.getParameterName())));
    }

    /**
     * QA Bloque BE (2026-05-17): error al convertir un path/query parameter
     * (ej. "abc" como Long). Antes Exception -> 500.
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "tipo correcto";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Valor invalido para el parametro '" + ex.getName()
                                + "': se esperaba " + expected)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {

        String message = ex.getMessage();

        System.out.println("ex.getMessage() Exception: " + message);

        if (message != null && message.contains("Debe existir al menos un usuario con ADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            ErrorRespondJson.getErrorRespondMessage(
                                    Optional.of("Debe existir al menos un usuario con ADMIN.")));
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of(message != null ? message : "Error interno del servidor.")));
    }

}
