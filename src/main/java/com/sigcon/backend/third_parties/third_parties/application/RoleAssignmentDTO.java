package com.sigcon.backend.third_parties.third_parties.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * TER-04: DTO de respuesta para asignaciones de roles con fechas de vigencia.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleAssignmentDTO {

    private Long id;
    private Long thirdPartyId;
    private Long roleId;
    private String roleName;
    private LocalDate validFrom;
    private LocalDate validTo;
}
