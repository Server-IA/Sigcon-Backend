package com.sigcon.backend.utils;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class DataTableRequest {
    // ===== DataTables =====
    private int draw;
    private int start;
    private int length;

    // ===== Filtros =====
    private List<DataTableColumn> columns;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DataTableColumn {
        private String data;
        private String name;
        private boolean searchable;
        private boolean orderable;
        private DataTableSearch search;
    }
    
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DataTableSearch {
        private String value;
        private boolean regex;
    }
}
