package com.sigcon.backend.invoices.ap_reconciliation.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * AP-09: DTO que representa un pago AP y sus candidatos de conciliacion en BNK.
 *
 * <p>Se usa para presentar al usuario las opciones de match encontradas entre
 * un pago a proveedor y los movimientos financieros bancarios, ordenadas
 * por cercania de fecha y exactitud de monto.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApReconciliationCandidateDTO {

    /** ID del pago AP a conciliar. */
    private Long apPaymentId;

    /** Factura asociada. */
    private Long invoiceId;

    /** Numero de resolucion de la factura. */
    private String invoiceNumber;

    /** Monto del pago AP. */
    private BigDecimal apAmount;

    /** Fecha del pago AP. */
    private LocalDate apPaymentDate;

    /** Cuenta bancaria del pago (si aplica). */
    private Long apBankAccountId;

    /** Referencia de pago (numero transferencia, etc.). */
    private String apReference;

    /** Lista de candidatos BNK propuestos para match. */
    private List<BankMovementCandidate> candidates;

    /** Resumen: "MATCH_EXACT", "MATCH_APPROX", "NO_CANDIDATES", "ALREADY_RECONCILED". */
    private String matchStatus;

    /**
     * Candidato individual de movimiento financiero BNK.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BankMovementCandidate {
        private Long movementId;
        private LocalDate movementDate;
        private BigDecimal amount;
        private String externalReference;
        private String description;
        /** Score de match (0.0 a 1.0): 1.0 = exact date+amount. */
        private Double matchScore;
    }
}
