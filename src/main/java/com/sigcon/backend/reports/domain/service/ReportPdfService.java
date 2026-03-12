package com.sigcon.backend.reports.domain.service;

import com.itextpdf.layout.element.Paragraph;
import com.sigcon.backend.utils.PdfTemplateBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Servicio responsable de generar informes PDF en SIGCON.
 *
 * <h2>Guía de Extensión</h2>
 * <p>Para agregar un nuevo informe específico de negocio (ej. activos, contabilidad):</p>
 * <ol>
 *   <li>Crear un nuevo servicio que extienda o delegue en este.</li>
 *   <li>Construir una {@code List<Paragraph>} con el contenido específico del informe.</li>
 *   <li>Llamar a {@link #generateTemplateReport()} o invocar directamente
 *       {@link PdfTemplateBuilder#buildTemplate(String, String, List)}
 *       con su título personalizado y párrafos de cuerpo.</li>
 *   <li>Exponer el resultado a través de un nuevo endpoint en un controlador dedicado.</li>
 * </ol>
 *
 * <p>Ejemplo para un informe de activos:</p>
 * <pre>
 *   List&lt;Paragraph&gt; body = buildAssetTable(assets);
 *   return PdfTemplateBuilder.buildTemplate("Informe de Activos", username, body);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private static final String TEMPLATE_REPORT_TITLE = "Plantilla Base de Informe SIGCON";

    /**
     * Genera la plantilla base estructural del PDF.
     *
     * <p>Retorna un PDF completamente renderizado con encabezado, fila de metadatos,
     * cuerpo de marcador de posición y pie de página — pero sin datos específicos de negocio.</p>
     *
     * @return bytes crudos del PDF
     * @throws IOException si la construcción del PDF falla (carga de fuentes, etc.)
     */
    public byte[] generateTemplateReport() throws IOException {

        String username = resolveCurrentUsername();

        log.info("Generando plantilla base de informe PDF para el usuario '{}'", username);

        // Cuerpo vacío → PdfTemplateBuilder renderiza la sección de marcador de posición
        List<Paragraph> emptyBody = Collections.emptyList();

        byte[] pdfBytes = PdfTemplateBuilder.buildTemplate(TEMPLATE_REPORT_TITLE, username, emptyBody);

        log.info("Plantilla PDF generada exitosamente ({} bytes)", pdfBytes.length);

        return pdfBytes;
    }

    /**
     * Genera un informe PDF con un título personalizado y contenido de cuerpo personalizado.
     *
     * <p>Diseñado para ser llamado por servicios de informes especializados que construyen
     * su propia lista de {@link Paragraph} y delegan el ensamblaje del PDF aquí.</p>
     *
     * @param reportTitle  título legible para el informe
     * @param bodyContent  lista de párrafos pre-construidos para incrustar en la sección del cuerpo
     * @return bytes crudos del PDF
     * @throws IOException si la construcción del PDF falla
     */
    public byte[] generateReport(String reportTitle, List<Paragraph> bodyContent) throws IOException {

        String username = resolveCurrentUsername();

        log.info("Generando informe '{}' para el usuario '{}'", reportTitle, username);

        byte[] pdfBytes = PdfTemplateBuilder.buildTemplate(reportTitle, username, bodyContent);

        log.info("Informe '{}' generado exitosamente ({} bytes)", reportTitle, pdfBytes.length);

        return pdfBytes;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Extrae el nombre de usuario/email del contexto de seguridad JWT actual.
     * Utiliza "sistema" si el contexto no está disponible (ej. en pruebas).
     */
    private String resolveCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return auth.getName();
            }
        } catch (Exception e) {
            log.warn("No se pudo resolver el usuario del contexto de seguridad: {}", e.getMessage());
        }
        return "sistema";
    }
}
