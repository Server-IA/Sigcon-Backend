package com.sigcon.backend.invoices.purchase_orders.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoodsReturnDTO {
    private Long id;
    private String returnNumber;
    private Long receiptId;
    private String receiptNumber;
    private LocalDate returnDate;
    private String reason;
    private LocalDateTime createdAt;
    private List<Line> lines;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Line {
        private Long goodsReceiptLineId;
        private BigDecimal quantityReturned;
        private String notes;
    }
}
