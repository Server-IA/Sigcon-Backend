package com.sigcon.backend.utils;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;

public class DataTableSpecificationBuilder<T> {

    public Specification<T> build(DataTableRequest request) {

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
    
            Predicate predicate = cb.conjunction();
    
            /* ============================================================
               🔹 GLOBAL SEARCH
            ============================================================ */
            if (request.getSearch() != null
                    && request.getSearch().getValue() != null
                    && !request.getSearch().getValue().isBlank()) {
    
                String globalValue = request.getSearch().getValue().trim();

                // HU-CFG (Bloque AQ, 2026-05-04): permitir caracteres en espaniol (tildes,
                // ñ, ü) y parentesis. Usar \p{L}\p{N} (Unicode letters + numbers) en
                // modo Unicode para evitar issues con encoding del literal en el .class.
                // Antes el regex con literales `áéíóúÁÉÍÓÚñÑüÜ` rechazaba datos validos
                // del seed con tildes / ñ.
                if (!globalValue.matches("(?U)^[\\p{L}\\p{N} ()_\\-%,.]+$")) {
                    throw new IllegalArgumentException("Entrada de busqueda inválida");
                }

                boolean regex = request.getSearch().isRegex();
    
                Predicate globalPredicate = cb.disjunction();
    
                for (DataTableRequest.DataTableColumn column : request.getColumns()) {
    
                    if (!column.isSearchable()) continue;

                    String field = resolveFieldName(column);
                    Path<?> path;
                    try {
                        path = getPath(root, field);
                    } catch (IllegalArgumentException ex) {
                        // Campo virtual del DTO sin atributo real en la entidad
                        // (ej. column.data='thirdPartyName' sin name='thirdParty.businessName').
                        // Lo saltamos en busqueda en lugar de tumbar la query con HTTP 400.
                        continue;
                    }
                    Class<?> type = path.getJavaType();

                    /* -------- STRING -------- */
                    if (String.class.equals(type)) {
                        if (regex) {
                            String palabra = verifyValue(globalValue);
                            globalPredicate = cb.or(
                                    globalPredicate,
                                    cb.like(
                                        cb.lower(path.as(String.class)),
                                        palabra
                                    )
                            );
                        } else {
                            globalPredicate = cb.or(
                                    globalPredicate,
                                    cb.equal(path, globalValue)
                            );
                        }
                    }
    
                    else if (type.isEnum()) {

                        Object[] enumConstants = type.getEnumConstants();
                        Predicate enumGlobalPredicate = cb.disjunction();
                    
                        for (Object constant : enumConstants) {
                            String enumName = constant.toString();
                    
                            if (regex) {
                                if (enumName.toLowerCase().contains(globalValue.toLowerCase())) {
                                    enumGlobalPredicate = cb.or(enumGlobalPredicate, cb.equal(path, constant));
                                }
                            } else {
                                if (enumName.equalsIgnoreCase(globalValue)) {
                                    enumGlobalPredicate = cb.or(enumGlobalPredicate, cb.equal(path, constant));
                                }
                            }
                        }
                    
                        globalPredicate = cb.or(globalPredicate, enumGlobalPredicate);
                    }
                    
                    /* -------- NUMERIC / BOOLEAN -------- */
                    else if(type.equals(Long.class) || type.equals(Integer.class) || type.equals(Double.class)) {
                        // Permitir enteros y decimales
                        if (globalValue.matches("\\d+(\\.\\d+)?")) {

                            Object convertedValue = convertValue(globalValue, type);

                            if (regex) {

                                // 🔥 Convertir número a texto usando to_char (Postgres)
                                Expression<String> numberAsString = cb.function(
                                        "to_char",
                                        String.class,
                                        path,
                                        cb.literal("FM999999999999.000000")
                                );
                                
                                String palabra = verifyValue(globalValue);

                                globalPredicate = cb.or(
                                        globalPredicate,
                                        cb.like(numberAsString, palabra)
                                );

                            } else {

                                globalPredicate = cb.or(
                                        globalPredicate,
                                        cb.equal(path, convertedValue)
                                );
                            }
                        }
                    }

                    

                    // else if(type.equals(LocalDate.class)) {
                    //     globalPredicate = cb.or(
                    //         globalPredicate,
                    //         cb.equal(path, LocalDate.parse(globalValue))
                    //     );
                    // }

                }
    
                predicate = cb.and(predicate, globalPredicate);
            }
    
            /* ============================================================
               🔹 COLUMN SEARCH
            ============================================================ */
            else if (request.getColumns() != null) {
    
                for (DataTableRequest.DataTableColumn column : request.getColumns()) {
    
                    if (column.getSearch() == null
                            || column.getSearch().getValue() == null
                            || column.getSearch().getValue().isBlank()) {
                        continue;
                    }
    
                    String field = resolveFieldName(column);
                    String searchValue = column.getSearch().getValue().trim();

                    boolean regex = column.getSearch().isRegex();
                    // HU-CFG (Bloque AQ, 2026-05-04): permitir caracteres en espaniol (tildes,
                    // ñ, ü) y parentesis. Usar \p{L}\p{N} en modo Unicode (?U) para
                    // matchear letras/numeros de cualquier idioma sin depender del
                    // encoding del literal compilado.
                    // Cuando regex=true, permitir ademas `|` como separador OR.
                    // QA-BLOQUE-AY (2026-05-05): permitir '|' tambien sin regex
                    // (filtros multi-select de assets/supplier.id envian ids
                    // numericos unidos por '|' con regex=false).
                    String allowedPattern = regex
                            ? "(?U)^[\\p{L}\\p{N} ()_\\-%,.|]+$"
                            : "(?U)^[\\p{L}\\p{N} ()_\\-%,.|]+$";
                    if (!searchValue.matches(allowedPattern)) {
                        throw new IllegalArgumentException("Entrada de busqueda inválida");
                    }

                    Path<?> path;
                    try {
                        path = getPath(root, field);
                    } catch (IllegalArgumentException ex) {
                        // Campo virtual sin mapping a entidad: ignorar este filtro de columna.
                        continue;
                    }
                    Class<?> type = path.getJavaType();
    
                    /* -------- IN (multi-select) --------
                     * QA-BLOQUE-AY (2026-05-05): aceptar tambien '|' como separador
                     * (varios filtros del frontend - assets, AR, AP - lo usan al
                     * hacer value.join('|')). Antes el split solo entendia ',' y
                     * el filtro se ignoraba silenciosamente cuando llegaban
                     * varias opciones unidas por '|'.
                     * Tambien soporta enums y numericos en la rama IN (antes solo
                     * strings con LIKE), critico para filtrar por classification,
                     * type, status (enums) o supplier.id (Long).
                     */
                    if (searchValue.contains(",") || searchValue.contains("|")) {

                        List<String> values = Arrays
                        .stream(searchValue.split("[,|]"))
                                .map(String::trim)
                                .filter(v -> !v.isBlank())
                                .toList();

                        if (!values.isEmpty()) {

                            if (type.isEnum()) {
                                @SuppressWarnings({"unchecked","rawtypes"})
                                List<Object> enumValues = values.stream()
                                        .map(v -> {
                                            try {
                                                return (Object) Enum.valueOf((Class<Enum>) type, v.toUpperCase());
                                            } catch (IllegalArgumentException ex) {
                                                return null;
                                            }
                                        })
                                        .filter(v -> v != null)
                                        .toList();
                                if (!enumValues.isEmpty()) {
                                    predicate = cb.and(predicate, path.in(enumValues));
                                }
                            } else if (String.class.equals(type)) {
                                if (regex) {
                                    List<Predicate> likePredicates = values.stream()
                                            .map(v -> cb.like(cb.lower(path.as(String.class)), verifyValue(v)))
                                            .toList();
                                    predicate = cb.and(predicate, cb.or(likePredicates.toArray(new Predicate[0])));
                                } else {
                                    predicate = cb.and(predicate, path.in(values));
                                }
                            } else {
                                // Numericos y otros: convertir y usar IN
                                List<Object> convertedValues = values.stream()
                                        .map(v -> {
                                            try {
                                                return convertValue(v, type);
                                            } catch (Exception ex) {
                                                return null;
                                            }
                                        })
                                        .filter(v -> v != null)
                                        .toList();
                                if (!convertedValues.isEmpty()) {
                                    predicate = cb.and(predicate, path.in(convertedValues));
                                }
                            }
                        }

                        continue;
                    }
    
                    /* -------- STRING -------- */
                    if (String.class.equals(type)) {
    
                        if (regex) {
                            String palabra = verifyValue(searchValue);
                            predicate = cb.and(
                                    predicate,
                                    cb.like(
                                            cb.lower(path.as(String.class)),
                                            palabra
                                    )
                            );
                        } else {
                            predicate = cb.and(
                                    predicate,
                                    cb.equal(path, searchValue)
                            );
                        }
                    }
    
                    /* -------- ENUM --------
                     * QA-BLOQUE-AY (2026-05-05): match exacto siempre, ignorar
                     * regex flag. Antes con regex=true se hacia
                     * `enumName.contains(searchValue)` que matcheaba enum
                     * pares con substring comun (ej. CURRENT esta dentro de
                     * NON_CURRENT, asi que filtrar por CURRENT devolvia TODOS
                     * los activos). El frontend siempre envia el nombre
                     * exacto del enum como value, asi que el match exacto es
                     * el comportamiento correcto.
                     */
                    else if (type.isEnum()) {
                        Object[] enumConstants = type.getEnumConstants();
                        Predicate enumPredicate = cb.disjunction();
                        for (Object constant : enumConstants) {
                            if (constant.toString().equalsIgnoreCase(searchValue)) {
                                enumPredicate = cb.or(enumPredicate, cb.equal(path, constant));
                                break;
                            }
                        }
                        predicate = cb.and(predicate, enumPredicate);
                    }

                    else if(type.equals(Boolean.class)){
                        predicate = cb.and(predicate, cb.equal(path, Boolean.valueOf(searchValue)));
                    }

                    /* -------- DATE / DATETIME -------- */
                    // QA-BLOQUE-AT (2026-04-30): el filtro de fecha pasaba a la rama
                    // numerica, no matcheaba el regex \\d+ y se ignoraba silenciosamente.
                    // Soporte: yyyy-MM-dd para LocalDate, yyyy-MM-dd[ HH:mm[:ss]] para
                    // LocalDateTime. Coincidencia exacta del dia.
                    else if (java.time.LocalDate.class.equals(type)) {
                        try {
                            java.time.LocalDate parsed = java.time.LocalDate.parse(searchValue);
                            predicate = cb.and(predicate, cb.equal(path, parsed));
                        } catch (Exception ex) {
                            // formato invalido -> ignorar filtro
                        }
                    }
                    else if (java.time.LocalDateTime.class.equals(type)) {
                        try {
                            java.time.LocalDate parsed = java.time.LocalDate.parse(searchValue);
                            // rango: [00:00:00, 23:59:59.999999999] del dia indicado
                            java.time.LocalDateTime start = parsed.atStartOfDay();
                            java.time.LocalDateTime end = parsed.atTime(23, 59, 59, 999_999_999);
                            predicate = cb.and(predicate,
                                    cb.between(path.as(java.time.LocalDateTime.class), start, end));
                        } catch (Exception ex) {
                            // formato invalido -> ignorar filtro
                        }
                    }

                    /* -------- NUMERIC -------- */
                    else {
                        // QA-BLOQUE-AT (2026-04-30): nunca usar LIKE en numericos.
                        // Hibernate lo traduce a SQL `<col> LIKE ?` y PostgreSQL
                        // falla con "operator does not exist: double precision ~~ text"
                        // (ej. al filtrar AP invoices por totalPayment).
                        // Acepta numeros con separadores (. , espacios) y ejecuta
                        // siempre comparacion exacta.
                        String cleaned = searchValue.replaceAll("[^0-9.\\-]", "");
                        if (!cleaned.isEmpty() && cleaned.matches("-?\\d+(\\.\\d+)?")) {
                            try {
                                Object convertedValue = convertValue(cleaned, type);
                                predicate = cb.and(predicate, cb.equal(path, convertedValue));
                            } catch (Exception ex) {
                                // tipo no soportado o conversion invalida -> ignorar filtro
                            }
                        }
                    }
                }
            }
    
            return predicate;
        };
    }
    

    /**
     * Resuelve el nombre del atributo a usar para construir la query.
     * Prioridad:
     *   1. column.name (cuando es un dot-path explicito tipo "thirdParty.businessName")
     *   2. column.data (cuando es un atributo plano)
     * El name se prefiere porque permite que el frontend mantenga `data` igual al campo
     * del DTO de respuesta (para render con DataTables) y al mismo tiempo apunte a
     * la columna real de la entidad para filtrar/buscar.
     */
    private String resolveFieldName(DataTableRequest.DataTableColumn column) {
        String name = column.getName();
        String data = column.getData();
        if (name != null && !name.isBlank() && !name.equals(data)) {
            return name;
        }
        return data;
    }

    private Path<?> getPath(Root<T> root, String field) {
        if(field.equals("roles")) {
            return root.join("roles", JoinType.LEFT).get("name"); // filtramos por role.name
        }

        if (!field.contains(".")) {
            return root.get(field);
        }
    
        String[] parts = field.split("\\.");
    
        From<?, ?> from = root;
    
        for (int i = 0; i < parts.length - 1; i++) {
            from = from.join(parts[i], JoinType.LEFT);
        }
    
        return from.get(parts[parts.length - 1]);
    }
    

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object convertValue(String value, Class<?> type) {

        if (String.class.equals(type)) return value;

        if (Long.class.equals(type)) return Long.valueOf(value);

        if (Integer.class.equals(type)) return Integer.valueOf(value);

        if (Boolean.class.equals(type)) return Boolean.valueOf(value);

        if (type.isEnum()) {
            try {
                return Enum.valueOf((Class<Enum>) type, value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null; // 👈 IMPORTANTE
            }
        }

        if (Double.class.equals(type)) return Double.valueOf(value);

        // if (LocalDate.class.equals(type)) return LocalDate.parse(value);

        return value;
    }

    private String verifyValue(String value){
        String palabra = value.toLowerCase();
        long count = palabra.chars().filter(ch -> ch == '%').count();
        if (count == 0) {
            // no tiene %, agregar a ambos lados
            palabra = "%" + palabra + "%";
        } else {
            // validar posición de %
            if (
                !(palabra.startsWith("%") || palabra.endsWith("%"))
                || palabra.indexOf('%') != palabra.lastIndexOf('%') && !(palabra.startsWith("%") && palabra.endsWith("%"))
            ) {
                throw new IllegalArgumentException("Parámetro de búsqueda no válido");
            }
        }
        return palabra;
    }

    private Map<String, String> buildFieldMapping(Class<?> dtoClass) {
        Map<String, String> map = new HashMap<>();
    
        for (Field field : dtoClass.getDeclaredFields()) {
            EntityField annotation = field.getAnnotation(EntityField.class);
    
            if (annotation != null) {
                map.put(field.getName(), annotation.value());
            } else {
                map.put(field.getName(), field.getName());
            }
        }
    
        return map;
    }

}
