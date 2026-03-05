package com.sigcon.backend.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataTableResponse<T> {

    private int draw;
    private long recordsTotal;
    private long recordsFiltered;
    private List<T> data;
    private Boolean success;
    private Integer code;
    private String message;
    private String timestamp;

    public static <T> DataTableResponse<T> from(Page<T> page, int draw) {
        return new DataTableResponse<>(
            draw,
            page.getTotalElements(),
            page.getTotalElements(),
            page.getContent(),
            true,
            200,
            "Operación realizada correctamente.",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    public static <T> DataTableResponse<T> from(List<T> data, int draw) {
        return new DataTableResponse<>(
            draw,
            data.size(),
            data.size(),
            data,
            true,
            200,
            "Operación realizada correctamente",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
}

