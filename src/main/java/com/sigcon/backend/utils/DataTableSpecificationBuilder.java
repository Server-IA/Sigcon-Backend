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

                if (!globalValue.matches("^[a-zA-Z0-9_\\-\\s%,.]+$")) {
                    throw new IllegalArgumentException("Entrada de busqueda inválida");
                }

                boolean regex = request.getSearch().isRegex();
    
                Predicate globalPredicate = cb.disjunction();
    
                for (DataTableRequest.DataTableColumn column : request.getColumns()) {
    
                    if (!column.isSearchable()) continue;
    
                    String field = column.getData();
                    Path<?> path = getPath(root, field);
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
    
                    String field = column.getData();
                    String searchValue = column.getSearch().getValue().trim();

                    if (!searchValue.matches("^[a-zA-Z0-9_\\-\\s%,.]+$")) {
                        throw new IllegalArgumentException("Entrada de busqueda inválida");
                    }

                    boolean regex = column.getSearch().isRegex();
    
                    Path<?> path = getPath(root, field);
                    Class<?> type = path.getJavaType();
    
                    /* -------- IN (multi-select) -------- */
                    if (searchValue.contains(",")) {

                        List<String> values = Arrays
                        .stream(searchValue.split(","))
                                .map(String::trim)
                                .filter(v -> !v.isBlank())
                                .toList();
                    
                        if (!values.isEmpty()) {
                    
                            if (regex) {
                                List<Predicate> likePredicates = values.stream()
                                        .map(v -> cb.like(cb.lower(path.as(String.class)),  verifyValue(v)))
                                        .toList();
                    
                                predicate = cb.and(predicate, cb.or(likePredicates.toArray(new Predicate[0])));
                            } else {
                                List<Object> convertedValues = values.stream()
                                        .map(v -> convertValue(v, type))
                                        .toList();
                    
                                predicate = cb.and(predicate, path.in(convertedValues));
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
    
                    /* -------- ENUM -------- */
                    else if (type.isEnum()) {

                        Object[] enumConstants = type.getEnumConstants();
                        Predicate enumPredicate = cb.disjunction(); // OR

                        for (Object constant : enumConstants) {
                            String enumName = constant.toString();

                            if (regex) {
                                if (enumName.toLowerCase().contains(searchValue.toLowerCase())) {
                                    enumPredicate = cb.or(enumPredicate, cb.equal(path, constant));
                                }
                            } else {
                                if (enumName.equalsIgnoreCase(searchValue)) {
                                    enumPredicate = cb.or(enumPredicate, cb.equal(path, constant));
                                }
                            }
                        }

                        predicate = cb.and(predicate, enumPredicate);
                    }

                    else if(type.equals(Boolean.class)){
                        predicate = cb.and(predicate, cb.equal(path, Boolean.valueOf(searchValue)));
                    }

    
                    /* -------- NUMERIC / BOOLEAN -------- */
                    else {
                        if(searchValue.matches("\\d+")) {
                            Object convertedValue = convertValue(searchValue, type);
    
                            if(regex) {
                                String palabra = verifyValue(searchValue);
                                
                                predicate = cb.and(
                                        predicate,
                                        cb.like(path.as(String.class), palabra)
                                );
                            } else {
                                predicate = cb.and(
                                        predicate,
                                        cb.equal(path, convertedValue)  
                                );
                            }
                        }
    
                    }
                }
            }
    
            return predicate;
        };
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
