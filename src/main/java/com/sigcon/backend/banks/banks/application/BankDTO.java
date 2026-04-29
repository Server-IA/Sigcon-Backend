package com.sigcon.backend.banks.banks.application;

import com.sigcon.backend.banks.banks.domain.model.enums.BankStatus;
import com.sigcon.backend.banks.banks.domain.model.enums.BankType;
import com.sigcon.backend.banks.banks.domain.model.enums.FormatExtract;
import com.sigcon.backend.parametrization.resources.application.CountryDTO;
import com.sigcon.backend.utils.EntityField;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para la gestión de bancos en el catálogo")
public class BankDTO {

    @EntityField("banks.id")
    @Schema(description = "Identificador único del banco", example = "1")
    private Long id;

    @NotBlank(message = "El código del banco no puede ser nulo")
    // QA HU-006 E1: el regex ahora acepta guion para codigos como BC-001 que ya
    // existen en BD (seeds). Antes era ^[A-Za-z0-9]+$ y rechazaba guiones.
    @jakarta.validation.constraints.Pattern(
            regexp = "^[A-Za-z0-9-]+$",
            message = "El código solo admite letras, números y guion (-)")
    @jakarta.validation.constraints.Size(min = 2, max = 20)
    @Schema(description = "Código oficial del banco", example = "BC-001")
    private String code;

    @NotBlank(message = "El nombre del banco no puede ser nulo")
    @Schema(description = "Nombre completo del banco", example = "Banco de Colombia")
    private String name;

    @NotBlank(message = "El nombre corto del banco no puede ser nulo")
    @Schema(description = "Nombre corto o abreviado del banco", example = "BANCOLOMBIA")
    private String nameShort;

    @NotNull(message = "El tipo de banco no puede ser nulo")
    @Schema(description = "Tipo de banco", example = "COMERCIAL")
    private BankType typeBank;

    @NotBlank(message = "El NIT del banco no puede ser nulo")
    @Schema(description = "NIT del banco", example = "900123456")
    private String nit;

    @Schema(description = "País asociado al banco")
    private CountryDTO country;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotNull(message = "El ID del país no puede ser nulo")
    @Schema(description = "ID del país asociado", example = "1")
    private Long countryId;

    @NotBlank(message = "El código SWIFT no puede ser nulo")
    @Schema(description = "Código SWIFT del banco", example = "COLOCOBOGXXX")
    private String swift;

    @Schema(description = "Código ACH para transferencias", example = "ACH01")
    private String codeAch;

    @Schema(description = "Sucursales asociadas al banco")
    private List<BankBranchDTO> branches;

    @Schema(description = "URL del webservice del banco", example = "https://api.banco.com")
    private String urlWebservice;

    // QA HU-006 E2: dias entre 1 y 31. Antes aceptaba cualquier valor.
    @jakarta.validation.constraints.Min(value = 1, message = "Los días de conciliación deben estar entre 1 y 31")
    @jakarta.validation.constraints.Max(value = 31, message = "Los días de conciliación deben estar entre 1 y 31")
    @Schema(description = "Días de conciliación del banco (1-31)", example = "3")
    private Integer conciliationDays;

    @Schema(description = "Teléfono principal del banco", example = "6012345678")
    private String phone;

    @Schema(description = "Estado del banco", example = "ACTIVE")
    private BankStatus status;

    @Schema(description = "Formato del extracto bancario", example = "PDF")
    private FormatExtract formatExtract;

    @Schema(description = "Fecha de creación del registro")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de última actualización")
    private LocalDateTime updatedAt;

    // QA HU-008 E1: el frontend deshabilita campos criticos (codigo, NIT) si
    // el banco ya tiene cuentas bancarias asociadas. Backend calcula este flag
    // para que la UI no tenga que hacer un GET adicional.
    @Schema(description = "Tiene cuentas bancarias asociadas (vigentes)")
    private Boolean hasAssociatedAccounts;

    @Schema(description = "Tiene cuentas bancarias asociadas en estado ACTIVA")
    private Boolean hasActiveAssociatedAccounts;
}