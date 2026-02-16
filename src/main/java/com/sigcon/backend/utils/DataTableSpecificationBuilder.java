package com.sigcon.backend.utils;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;

public class DataTableSpecificationBuilder<T> {

    public Specification<T> build(DataTableRequest request) {

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            Predicate predicate = cb.conjunction();

            // 🔹 Search Global

            if (request.getSearch() != null
                && request.getSearch().getValue() != null
                && !request.getSearch().getValue().isBlank()
            ){
                String globalValue = request.getSearch().getValue();
                boolean regex = request.getSearch().isRegex();

                Predicate globalPredicate = cb.disjunction(); // OR
                
                for (DataTableRequest.DataTableColumn column : request.getColumns()) {

                    if (!column.isSearchable()) {
                        continue;
                    }
            
                    String field = column.getData();
                    Path<?> path = getPath(root, field);
            
                    Class<?> type = path.getJavaType();
            
                    // LIKE solo para String
                    if (regex && type.equals(String.class)) {
            
                        globalPredicate = cb.or(
                                globalPredicate,
                                cb.like(
                                        cb.lower(path.as(String.class)),
                                        "%" + globalValue.toLowerCase() + "%"
                                )
                        );
            
                    } else {
                        // Exact match
                        globalPredicate = cb.or(
                                globalPredicate,
                                cb.equal(path, globalValue)
                        );
                    }
                }
            
                predicate = cb.and(predicate, globalPredicate);

            } else if (request.getColumns() != null) {

                for (DataTableRequest.DataTableColumn column : request.getColumns()) {

                    if (column.getSearch() == null || column.getSearch().getValue() == null) {
                        continue;
                    }

                    String field = column.getData();
                    String searchValue = column.getSearch().getValue();
                    boolean regex = column.getSearch().isRegex();

                    Path<?> path = getPath(root, field);

                    // 🔹 IN (select múltiple)
                    if (searchValue.contains(",")) {

                        List<String> values = List.of(searchValue.split(","))
                                .stream()
                                .map(String::trim)
                                .filter(v -> !v.isBlank())
                                .toList();
                    
                        if (!values.isEmpty()) {
                            predicate = cb.and(predicate, path.in(values));
                        }
                    }

                    // 🔹 String
                    else if (!searchValue.isBlank()) {

                        Class<?> type = path.getJavaType();

                        // LIKE solo si es String
                        if (regex && type.equals(String.class)) {

                            predicate = cb.and(
                                    predicate,
                                    cb.like(
                                            cb.lower(path.as(String.class)),
                                            "%" + searchValue.toLowerCase() + "%"
                                    )
                            );

                        } else {
                            // Exact match
                            predicate = cb.and(predicate, cb.equal(path, searchValue));
                        }
                    }
                }
            }

            return predicate;
        };
    }

    private Path<?> getPath(Root<T> root, String field) {

        if (field.contains(".")) {

            String[] parts = field.split("\\.");
            Path<?> path = root.get(parts[0]);

            for (int i = 1; i < parts.length; i++) {
                path = path.get(parts[i]);
            }

            return path;
        }

        return root.get(field);
    }
}
