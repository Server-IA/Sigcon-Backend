package com.sigcon.backend.reports.interfaces;

import com.sigcon.backend.reports.domain.service.ReportPdfService;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Controlador para el módulo de Informes.
 *
 * <p>Todos los puntos de enlace (endpoints) en este controlador están restringidos al rol {@code ROLE_ADMIN}.</p>
 *
 * <h2>Guía de Extensión</h2>
 * <p>Para agregar un nuevo endpoint de informe (ej. reporte de activos):</p>
 * <ol>
 *   <li>Inyectar o crear un servicio especializado (ej. {@code AssetReportService}).</li>
 *   <li>Construir el cuerpo del informe como una {@code List<Paragraph>} con datos de negocio.</li>
 *   <li>Llamar a {@code reportPdfService.generateReport("Informe de Activos", body)}.</li>
 *   <li>Retornar el resultado con el mismo helper {@code buildPdfResponse(...)} de abajo.</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Informes", description = "Endpoints para la generación de informes PDF del sistema SIGCON")
public class ReportController {

    private final ReportPdfService reportPdfService;

    // ─── Endpoints ───────────────────────────────────────────────────────────

    @GetMapping("/template")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
    @Operation(
            summary = "Generar plantilla base PDF",
            description = "Genera y retorna la plantilla institucional base en formato PDF. " +
                    "Esta plantilla sirve como estructura reutilizable para todos los " +
                    "informes futuros del sistema (activos, contabilidad, administrativos, etc.)."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "PDF generado exitosamente",
                    content = @Content(mediaType = "application/pdf")
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "No autenticado — se requiere token JWT válido",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Acceso denegado — se requiere ROLE_ADMIN",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno al generar el PDF",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorRespondJson.class))
            )
    })
    public ResponseEntity<?> generateTemplatePdf() {

        try {
            byte[] pdfBytes = reportPdfService.generateTemplateReport();
            return buildPdfResponse(pdfBytes, "sigcon_plantilla_base.pdf");

        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            log.error("Error al generar la plantilla PDF: ", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Error al generar el informe PDF. Contacte al administrador.")));
        }
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    /**
     * Envuelve los bytes crudos del PDF en una {@link ResponseEntity} con las
     * cabeceras HTTP apropiadas para visualización en navegador y descarga.
     *
     * @param pdfBytes contenido crudo del PDF
     * @param filename nombre de archivo sugerido para la cabecera Content-Disposition
     * @return ResponseEntity 200 OK con la carga útil del PDF
     */
    private ResponseEntity<byte[]> buildPdfResponse(byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", filename);
        headers.setContentLength(pdfBytes.length);
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
