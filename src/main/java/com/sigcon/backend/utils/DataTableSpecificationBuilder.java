package com.sigcon.backend.utils;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;

public class DataTableSpecificationBuilder<T> {

    public Specification<T> build(DataTableRequest request) {

        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            Predicate predicate = cb.conjunction(); // TRUE inicial

            if (request.getColumns() != null) {
                for (DataTableRequest.DataTableColumn column : request.getColumns()) {

                    if (column.isSearchable()
                            && column.getSearch() != null
                            && column.getSearch().getValue() != null
                            && !column.getSearch().getValue().isEmpty()) {

                        String field = column.getData();
                        String value = column.getSearch().getValue();

                        predicate = cb.and(
                                predicate,
                                cb.like(
                                        cb.lower(root.get(field).as(String.class)),
                                        "%" + value.toLowerCase() + "%"
                                )
                        );
                    }
                }
            }

            return predicate;
        };
    }
}
