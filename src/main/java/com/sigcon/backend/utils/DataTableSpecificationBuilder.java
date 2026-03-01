package com.sigcon.backend.utils;

import java.util.List;

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
                            globalPredicate = cb.or(
                                    globalPredicate,
                                    cb.like(
                                            cb.lower(path.as(String.class)),
                                            "%" + globalValue.toLowerCase() + "%"
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
                    else if(type.equals(Long.class) || type.equals(Integer.class)) {
                        if(globalValue.matches("\\d+")) {
                            Object convertedValue = convertValue(globalValue, type);
                            if(regex) {
                                // Convertir número a texto para LIKE en Postgres
                                globalPredicate = cb.or(
                                    globalPredicate,
                                    cb.like(cb.concat("", path.as(String.class)), "%" + convertedValue + "%")
                                );
                            } else {
                                globalPredicate = cb.or(
                                    globalPredicate,
                                    cb.equal(path, convertedValue)
                                );
                            }
                        }
                    }

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
                    boolean regex = column.getSearch().isRegex();
    
                    Path<?> path = getPath(root, field);
                    Class<?> type = path.getJavaType();
    
                    /* -------- IN (multi-select) -------- */
                    if (searchValue.contains(",")) {
    
                        List<Object> convertedValues = List.of(searchValue.split(","))
                                .stream()
                                .map(String::trim)
                                .filter(v -> !v.isBlank())
                                .map(v -> convertValue(v, type))
                                .toList();
    
                        if (!convertedValues.isEmpty()) {
                            predicate = cb.and(predicate, path.in(convertedValues));
                        }
    
                        continue;
                    }
    
                    /* -------- STRING -------- */
                    if (String.class.equals(type)) {
    
                        if (regex) {
                            predicate = cb.and(
                                    predicate,
                                    cb.like(
                                            cb.lower(path.as(String.class)),
                                            "%" + searchValue.toLowerCase() + "%"
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

    
                    /* -------- NUMERIC / BOOLEAN -------- */
                    else {
                        if(searchValue.matches("\\d+")) {
                            Object convertedValue = convertValue(searchValue, type);
    
                            if(regex) {
                                predicate = cb.and(
                                        predicate,
                                        cb.like(path.as(String.class), "%" + convertedValue + "%")
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

        return value;
    }

}
