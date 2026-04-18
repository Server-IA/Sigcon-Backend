package com.sigcon.backend.nomina.interfaces.controller;

import com.sigcon.backend.nomina.domain.service.BenefitLiquidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * HU-NOM-05: prestaciones sociales.
 *
 * <ul>
 *   <li>Cesantias e intereses anuales (CST Art. 249, Ley 52/1975)</li>
 *   <li>Prima de servicios semestral (CST Art. 306)</li>
 *   <li>Liquidacion definitiva de contrato (CST Art. 64)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/nomina/prestaciones")
@RequiredArgsConstructor
@Tag(name = "Nomina - Prestaciones Sociales",
     description = "Cesantias, prima, liquidacion definitiva (HU-NOM-05)")
public class BenefitLiquidationController {

    private final BenefitLiquidationService service;

    @Operation(summary = "Liquidar cesantias e intereses (HU-NOM-05 E1)",
            description = "Calcula cesantias = salario * dias / 360 e intereses = cesantias * 12% * dias/360. "
                    + "Genera JournalEntry: D Gasto Cesantias / C CxP Fondo Cesantias. "
                    + "Consignacion antes del 15 de febrero (CST Art. 249).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liquidacion exitosa"),
            @ApiResponse(responseCode = "400", description = "Empleado no encontrado o sin salario base")
    })
    @PostMapping("/cesantias")
    public ResponseEntity<?> severance(
            @Parameter(description = "ID del empleado", required = true, example = "1")
            @RequestParam Long employeeId,
            @Parameter(description = "Año a liquidar", required = true, example = "2026")
            @RequestParam Integer year) {
        return ResponseEntity.ok(service.liquidateSeverance(employeeId, year));
    }

    @Operation(summary = "Liquidar prima de servicios (HU-NOM-05 E2)",
            description = "Prima = salario * dias_semestre / 360. Dos pagos por año: "
                    + "semester=1 -> junio, semester=2 -> diciembre (CST Art. 306).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prima liquidada"),
            @ApiResponse(responseCode = "400", description = "Empleado no encontrado o semester invalido")
    })
    @PostMapping("/prima")
    public ResponseEntity<?> bonus(
            @Parameter(description = "ID del empleado", required = true, example = "1")
            @RequestParam Long employeeId,
            @Parameter(description = "Año gravable", required = true, example = "2026")
            @RequestParam Integer year,
            @Parameter(description = "Semestre (1 o 2)", required = true, example = "1")
            @RequestParam Integer semester) {
        return ResponseEntity.ok(service.liquidateServiceBonus(employeeId, year, semester));
    }

    @Operation(summary = "Liquidacion definitiva de contrato (HU-NOM-05 E3)",
            description = "Calcula cesantias pendientes + intereses + prima proporcional + "
                    + "vacaciones compensadas + indemnizacion si aplica (CST Art. 64). "
                    + "terminationType: SIN_JUSTA_CAUSA | JUSTA_CAUSA | MUTUO_ACUERDO | RENUNCIA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liquidacion definitiva calculada"),
            @ApiResponse(responseCode = "400", description = "Empleado no encontrado o datos invalidos")
    })
    @PostMapping("/liquidacion-definitiva")
    public ResponseEntity<?> termination(
            @Parameter(description = "ID del empleado", required = true, example = "1")
            @RequestParam Long employeeId,
            @Parameter(description = "Fecha de retiro", required = true, example = "2026-04-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate terminationDate,
            @Parameter(description = "Tipo de terminacion", required = true,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(
                            allowableValues = {"SIN_JUSTA_CAUSA", "JUSTA_CAUSA", "MUTUO_ACUERDO", "RENUNCIA"}),
                    example = "SIN_JUSTA_CAUSA")
            @RequestParam String terminationType) {
        return ResponseEntity.ok(service.liquidateTermination(employeeId, terminationDate, terminationType));
    }
}
