package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * AAEF v1.0 - Metadata del lote (RF-INT-13 seccion "metadata").
 *
 * <p>Identifica univocamente un lote AAEF y su origen. Todos los campos marcados
 * como obligatorios en el estandar son validados por Bean Validation.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AaefMetadataDTO {

    /** ID unico del lote. Formato: AF-YYYY-MM-NNNNN. */
    @JsonProperty("ExchangeId")
    @NotBlank(message = "metadata.ExchangeId es obligatorio")
    @Pattern(regexp = "^AF-\\d{4}-\\d{2}-\\d{5,}$",
            message = "ExchangeId no cumple formato AF-YYYY-MM-NNNNN")
    private String exchangeId;

    /** Timestamp de generacion del lote (ISO 8601 UTC). */
    @JsonProperty("GeneratedAt")
    private OffsetDateTime generatedAt;

    /** Version del estandar AAEF. Valor actual: "1.0". */
    @JsonProperty("StandardVersion")
    @NotBlank(message = "metadata.StandardVersion es obligatorio")
    private String standardVersion;

    /** Rango del periodo contable consultado. */
    @JsonProperty("RequestedPeriod")
    private RequestedPeriod requestedPeriod;

    /** Sistema que origino el lote. */
    @JsonProperty("SourceSystem")
    private SourceSystem sourceSystem;

    /** Usuario o servicio que ejecuto la generacion del lote. */
    @JsonProperty("GeneratedBy")
    private String generatedBy;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestedPeriod {
        @JsonProperty("From")
        private LocalDate from;

        @JsonProperty("To")
        private LocalDate to;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SourceSystem {
        @JsonProperty("SystemId")
        private String systemId;

        @JsonProperty("SystemName")
        private String systemName;

        @JsonProperty("SystemNIT")
        private String systemNIT;

        /** production | staging | development */
        @JsonProperty("Environment")
        private String environment;
    }
}
