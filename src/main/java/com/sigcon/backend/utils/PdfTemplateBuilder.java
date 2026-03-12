package com.sigcon.backend.utils;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utilidad reutilizable para la construcción de plantillas PDF para el sistema
 * de informes de SIGCON.
 *
 * <p>
 * Esta utilidad proporciona bloques de construcción estáticos para la
 * generación de PDFs.
 * Cada sección (encabezado, cuerpo, pie de página) se expone de forma
 * independiente para que
 * los futuros servicios de informes puedan sobrescribir secciones específicas
 * reutilizando
 * el resto de la estructura.
 * </p>
 *
 * <p>
 * Ejemplo de uso:
 * </p>
 * 
 * <pre>
 * byte[] pdf = PdfTemplateBuilder.buildTemplate("Informe de Activos", "admin@sigcon.com", bodyParagraphs);
 * </pre>
 *
 * @author Nicolas Urazan
 * @since 1.0
 */
public class PdfTemplateBuilder {

        // ─── Colores de marca ───────────────────────────────────────────────────
        private static final DeviceRgb BRAND_PRIMARY = new DeviceRgb(30, 58, 138); // azul oscuro
        private static final DeviceRgb BRAND_SECONDARY = new DeviceRgb(99, 102, 241); // acento índigo
        private static final DeviceRgb BRAND_LIGHT = new DeviceRgb(238, 242, 255); // fondo lavanda suave
        private static final DeviceRgb BRAND_TEXT_DARK = new DeviceRgb(17, 24, 39); // casi negro
        private static final DeviceRgb BRAND_SUBTLE = new DeviceRgb(107, 114, 128); // gris

        private static final String SYSTEM_NAME = "SIGCON";
        private static final String SYSTEM_TAGLINE = "Sistema Integrado de Gestión y Control";
        private static final String SYSTEM_VERSION = "v1.0";

        // ─── Formateador de fecha ────────────────────────────────────────────────
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        // Constructor privado — clase de utilidad, no instanciable
        private PdfTemplateBuilder() {
        }

        // =========================================================================
        // PUBLIC ENTRY POINT
        // =========================================================================

        /**
         * Construye una plantilla PDF base completamente estructurada.
         *
         * @param reportTitle Título mostrado en el encabezado (ej., "Informe de
         *                    Activos")
         * @param generatedBy Nombre de usuario o e-mail del usuario que disparó el
         *                    informe
         * @param bodyContent Lista de elementos {@link Paragraph} para la sección del
         *                    cuerpo.
         *                    Pasar una lista vacía para obtener solo el marcador de
         *                    posición estructural.
         * @return Bytes crudos del PDF listos para ser retornados desde un endpoint
         *         REST.
         * @throws IOException si la carga de fuentes falla (no debería ocurrir con
         *                     fuentes estándar)
         */
        public static byte[] buildTemplate(String reportTitle,
                        String generatedBy,
                        List<Paragraph> bodyContent) throws IOException {

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                try (PdfWriter writer = new PdfWriter(baos);
                                PdfDocument pdf = new PdfDocument(writer);
                                Document document = new Document(pdf, PageSize.A4)) {

                        document.setMargins(40, 45, 55, 45);

                        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

                        buildHeader(document, reportTitle, generatedBy, bold, regular);
                        buildMetaRow(document, reportTitle, generatedBy, bold, regular);
                        buildBody(document, bodyContent, regular, bold);
                        buildFooter(document, bold, regular);
                }

                return baos.toByteArray();
        }

        // =========================================================================
        // CONSTRUCTORES DE SECCIÓN (públicos para que futuros servicios los reutilicen)
        // =========================================================================

        /**
         * Renderiza el encabezado institucional: nombre del sistema, eslogan y título
         * del informe.
         */
        public static void buildHeader(Document document,
                        String reportTitle,
                        String generatedBy,
                        PdfFont bold,
                        PdfFont regular) throws IOException {

                // ── Banda superior ──────────────────────────────────────────────────
                Table topBand = new Table(UnitValue.createPercentArray(new float[] { 70f, 30f }))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setBackgroundColor(BRAND_PRIMARY)
                                .setMarginBottom(0);

                // Izquierda: nombre del sistema + eslogan
                Cell leftCell = new Cell()
                                .setBorder(Border.NO_BORDER)
                                .setPadding(14)
                                .add(new Paragraph(SYSTEM_NAME)
                                                .setFont(bold)
                                                .setFontSize(22)
                                                .setFontColor(ColorConstants.WHITE)
                                                .setMarginBottom(2))
                                .add(new Paragraph(SYSTEM_TAGLINE)
                                                .setFont(regular)
                                                .setFontSize(9)
                                                .setFontColor(new DeviceRgb(196, 204, 255))
                                                .setMarginBottom(0));
                topBand.addCell(leftCell);

                // Derecha: etiqueta de versión
                Cell rightCell = new Cell()
                                .setBorder(Border.NO_BORDER)
                                .setTextAlignment(TextAlignment.RIGHT)
                                .setPaddingTop(18)
                                .setPaddingRight(14)
                                .add(new Paragraph(SYSTEM_VERSION)
                                                .setFont(bold)
                                                .setFontSize(10)
                                                .setFontColor(new DeviceRgb(196, 204, 255)));
                topBand.addCell(rightCell);

                document.add(topBand);

                // ── Barra de título ─────────────────────────────────────────────────
                Table titleBar = new Table(1)
                                .setWidth(UnitValue.createPercentValue(100))
                                .setBackgroundColor(BRAND_SECONDARY)
                                .setMarginBottom(16);

                titleBar.addCell(new Cell()
                                .setBorder(Border.NO_BORDER)
                                .setPaddingTop(8)
                                .setPaddingBottom(8)
                                .setPaddingLeft(14)
                                .add(new Paragraph(reportTitle)
                                                .setFont(bold)
                                                .setFontSize(13)
                                                .setFontColor(ColorConstants.WHITE)));
                document.add(titleBar);
        }

        /**
         * Renderiza una fila de metadatos: fecha de generación y usuario que generó el
         * informe.
         */
        public static void buildMetaRow(Document document,
                        String reportTitle,
                        String generatedBy,
                        PdfFont bold,
                        PdfFont regular) throws IOException {

                String now = LocalDateTime.now().format(DATE_FMT);

                Table metaTable = new Table(UnitValue.createPercentArray(new float[] { 50f, 50f }))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setBackgroundColor(BRAND_LIGHT)
                                .setBorder(new SolidBorder(BRAND_SECONDARY, 1f))
                                .setMarginBottom(20);

                metaTable.addCell(metaCell("Fecha de generación:", now, bold, regular));
                metaTable.addCell(metaCell("Generado por:", generatedBy, bold, regular));

                document.add(metaTable);
        }

        /**
         * Renderiza la sección de contenido del cuerpo.
         * Si {@code bodyContent} está vacío, se muestra un párrafo de marcador de
         * posición en su lugar.
         */
        public static void buildBody(Document document,
                        List<Paragraph> bodyContent,
                        PdfFont regular,
                        PdfFont bold) throws IOException {

                // Título de sección
                document.add(new Paragraph("Contenido del Informe")
                                .setFont(bold)
                                .setFontSize(11)
                                .setFontColor(BRAND_PRIMARY)
                                .setBorderBottom(new SolidBorder(BRAND_SECONDARY, 1.5f))
                                .setPaddingBottom(4)
                                .setMarginBottom(12));

                if (bodyContent == null || bodyContent.isEmpty()) {
                        // Marcador de posición — los futuros servicios de informes inyectarán contenido
                        // real aquí
                        document.add(new Paragraph(
                                        "[ Sección de contenido — Este espacio será completado por el módulo de informe específico. ]")
                                        .setFont(regular)
                                        .setFontSize(10)
                                        .setFontColor(BRAND_SUBTLE)
                                        .setItalic()
                                        .setTextAlignment(TextAlignment.CENTER)
                                        .setMarginTop(30)
                                        .setMarginBottom(30));
                } else {
                        for (Paragraph p : bodyContent) {
                                document.add(p);
                        }
                }
        }

        /**
         * Renderiza el pie de página institucional con información del sistema y una
         * línea horizontal.
         */
        public static void buildFooter(Document document,
                        PdfFont bold,
                        PdfFont regular) throws IOException {

                // Línea separadora
                document.add(new Paragraph()
                                .setBorderTop(new SolidBorder(BRAND_SUBTLE, 0.5f))
                                .setMarginTop(30)
                                .setMarginBottom(6));

                Table footer = new Table(UnitValue.createPercentArray(new float[] { 60f, 40f }))
                                .setWidth(UnitValue.createPercentValue(100));

                footer.addCell(new Cell()
                                .setBorder(Border.NO_BORDER)
                                .add(new Paragraph(SYSTEM_NAME + " — " + SYSTEM_TAGLINE)
                                                .setFont(bold)
                                                .setFontSize(8)
                                                .setFontColor(BRAND_SUBTLE))
                                .add(new Paragraph("Documento generado automáticamente · No requiere firma")
                                                .setFont(regular)
                                                .setFontSize(7)
                                                .setFontColor(BRAND_SUBTLE)));

                footer.addCell(new Cell()
                                .setBorder(Border.NO_BORDER)
                                .setTextAlignment(TextAlignment.RIGHT)
                                .add(new Paragraph("© " + LocalDateTime.now().getYear() + " SIGCON " + SYSTEM_VERSION)
                                                .setFont(regular)
                                                .setFontSize(8)
                                                .setFontColor(BRAND_SUBTLE)));

                document.add(footer);
        }

        // =========================================================================
        // HELPERS PRIVADOS
        // =========================================================================

        private static Cell metaCell(String label, String value, PdfFont bold, PdfFont regular) {
                return new Cell()
                                .setBorder(Border.NO_BORDER)
                                .setPadding(8)
                                .add(new Paragraph(label)
                                                .setFont(bold)
                                                .setFontSize(8)
                                                .setFontColor(BRAND_SUBTLE)
                                                .setMarginBottom(1))
                                .add(new Paragraph(value)
                                                .setFont(regular)
                                                .setFontSize(9)
                                                .setFontColor(BRAND_TEXT_DARK));
        }
}
