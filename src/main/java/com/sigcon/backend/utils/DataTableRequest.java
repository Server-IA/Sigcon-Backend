package com.sigcon.backend.utils;

import java.time.LocalDate;
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

    // ===== Ordenamiento =====
    private List<DataTableOrder> order;

    // ===== Filtro Global =====
    private DataTableSearch search;

    // ===== FILTROS PERSONALIZADOS (FECHAS) =====
    private LocalDate receivedDateFrom;
    private LocalDate receivedDateTo;
    private LocalDate activationDateFrom;
    private LocalDate activationDateTo;

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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DataTableOrder {
        private int column;
        private String dir; // "asc" o "desc"
    }

    /**
     * Obtiene el nombre del campo de ordenamiento basado en el índice de columna.
     * @return nombre del campo o null si no hay orden definido
     */
    public String getOrderColumnName() {
        if (order == null || order.isEmpty() || columns == null) return null;
        int colIndex = order.get(0).getColumn();
        if (colIndex < 0 || colIndex >= columns.size()) return null;
        return columns.get(colIndex).getData();
    }

    /** @return "asc" o "desc", default "asc" */
    public String getOrderDir() {
        if (order == null || order.isEmpty()) return "asc";
        return order.get(0).getDir() != null ? order.get(0).getDir() : "asc";
    }
}