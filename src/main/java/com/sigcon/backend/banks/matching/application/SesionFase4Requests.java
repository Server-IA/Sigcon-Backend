package com.sigcon.backend.banks.matching.application;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BNK FASE 4: DTOs de request agrupados para sesiones de conciliación firmadas
 * (HU-066/067/075/077).
 */
public class SesionFase4Requests {

    @Data
    public static class CreateSesionRequest {
        @NotNull(message = "La cuenta bancaria es obligatoria.")
        private Long bankAccountId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal saldoExtracto;
    }

    /** HU-066: firma (elaborador o revisor). 2 pasos OTP (sin otp = solicita código). */
    @Data
    public static class FirmarRequest {
        private String documento;
        private String tarjetaProfesional;
        private String metodo;   // OTP | CERTIFICADO | BIOMETRICA
        private String otp;
    }

    /** HU-075 E1: solicitud de reapertura. */
    @Data
    public static class SolicitudReaperturaRequest {
        private String motivo;
        private String tipoCambioEsperado;
        private String evidenciaFileName;
        private String evidenciaHash;
    }

    /** HU-075 E4: aprobación de reapertura (REABRIR + 2ª firma). */
    @Data
    public static class AprobarReaperturaRequest {
        private String confirmText;
        private String documento;
        private String tarjetaProfesional;
        private String motivoRechazo; // usado en rechazo
    }

    /** HU-066 E1 / HU-067 E5: configuración de firma por empresa. */
    @Data
    public static class ConfigFirmaRequest {
        private String metodosPermitidos;
        private Boolean exigeCertRevisor;
        private Boolean modoFlexible;
    }
}
