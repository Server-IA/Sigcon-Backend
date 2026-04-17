package com.sigcon.backend.lists_accounting.accounting_lists.application;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de lectura para el Reporte de validacion masiva del PUC (HU-CG-09D).
 *
 * Contiene el resultado de la verificacion integral de consistencia del
 * catalogo unico de cuentas, incluyendo conteos globales y la lista
 * detallada de inconsistencias detectadas (huerfanos, naturaleza incoherente,
 * codigos duplicados, cuentas inactivas con movimientos).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PucValidationReportDTO {

    /** Total de cuentas registradas en el PUC (incluye activas e inactivas). */
    private Integer totalAccounts;

    /** Cuentas con estado ACTIVE. */
    private Integer activeAccounts;

    /** Cuentas con estado INACTIVE. */
    private Integer inactiveAccounts;

    /** Numero total de inconsistencias detectadas. */
    private Integer errorCount;

    /** Listado detallado de inconsistencias encontradas. */
    private List<PucIssueDTO> issues;

    /**
     * Detalle de una inconsistencia individual dentro del PUC.
     *
     * Tipos de inconsistencia (issueType):
     * <ul>
     *   <li>ORPHAN: cuenta sin padre en la jerarquia (grupo/cuenta/subcuenta sin ancestro)</li>
     *   <li>WRONG_NATURE: naturaleza incoherente con la clase PUC</li>
     *   <li>DUPLICATE_CODE: codigo PUC repetido entre cuentas activas</li>
     *   <li>INACTIVE_WITH_MOVEMENTS: cuenta INACTIVE con movimientos contables</li>
     * </ul>
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PucIssueDTO {
        private Long accountId;
        private String pucCode;
        private String accountName;
        private String issueType;
        private String description;
    }
}
