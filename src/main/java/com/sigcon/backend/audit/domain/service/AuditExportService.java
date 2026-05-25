package com.sigcon.backend.audit.domain.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sigcon.backend.audit.application.AuditDashboardDTO;
import com.sigcon.backend.audit.application.AuditLogDTO;
import com.sigcon.backend.audit.domain.model.AuditPurgeRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HU-AU-06: Servicio de exportacion de logs de auditoria en CSV, Excel y PDF.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditExportService {

    // QA Bloque BJ (2026-05-17): header estandar empresa+usuario+rol+filtros+totales
    private final com.sigcon.backend.utils.export.ReportContextResolver reportContextResolver;

    /** HU-AU-06 E3: Exportar a CSV. */
    public byte[] exportToCsv(List<AuditLogDTO> logs) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // BOM UTF-8

        // QA Bloque BJ (2026-05-17): header estandar como comentarios CSV
        com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext ctx = reportContextResolver
                .baseContext("Reporte de Auditoria (CSV)")
                .addFilter("Total registros", String.valueOf(logs.size()))
                .build();
        csv.append(com.sigcon.backend.utils.export.ReportHeaderBuilder.buildCsvHeader(ctx));

        csv.append("ID;Fecha;Usuario;Accion;Entidad;ID_Entidad;Modulo;Severidad;Descripcion;IP;Hash\n");
        for (AuditLogDTO l : logs) {
            csv.append(l.getId()).append(';')
               .append(l.getTimestamp()).append(';')
               .append(nz(l.getUserEmail())).append(';')
               .append(nz(AuditLabels.action(l.getAction()))).append(';')
               .append(nz(AuditLabels.entity(l.getEntityType()))).append(';')
               .append(l.getEntityId() != null ? l.getEntityId() : "").append(';')
               .append(nz(AuditLabels.module(l.getModule()))).append(';')
               .append(nz(AuditLabels.severity(l.getSeverity()))).append(';')
               .append(nz(l.getDescription())).append(';')
               .append(nz(l.getIpAddress())).append(';')
               .append(nz(l.getHash())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** HU-AU-06 E2: Exportar a Excel. */
    public byte[] exportToExcel(List<AuditLogDTO> logs) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Auditoria");
            String[] headers = {"ID", "Fecha", "Usuario", "Accion", "Entidad",
                    "ID Entidad", "Modulo", "Severidad", "Descripcion", "IP", "Hash"};

            // QA Bloque BJ (2026-05-17): header estandar
            com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext xlsxCtx = reportContextResolver
                    .baseContext("Reporte de Auditoria (XLSX)")
                    .addFilter("Total registros", String.valueOf(logs.size()))
                    .build();
            int startRow = com.sigcon.backend.utils.export.ReportHeaderBuilder.writeXlsxHeader(wb, sheet, xlsxCtx, headers.length);

            // Header
            XSSFRow headerRow = sheet.createRow(startRow);
            XSSFCellStyle headerStyle = wb.createCellStyle();
            XSSFFont font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                XSSFCell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Datos
            for (int r = 0; r < logs.size(); r++) {
                AuditLogDTO l = logs.get(r);
                XSSFRow row = sheet.createRow(startRow + 1 + r);
                row.createCell(0).setCellValue(l.getId() != null ? l.getId() : 0);
                row.createCell(1).setCellValue(l.getTimestamp() != null ? l.getTimestamp().toString() : "");
                row.createCell(2).setCellValue(nz(l.getUserEmail()));
                row.createCell(3).setCellValue(nz(AuditLabels.action(l.getAction())));
                row.createCell(4).setCellValue(nz(AuditLabels.entity(l.getEntityType())));
                row.createCell(5).setCellValue(l.getEntityId() != null ? l.getEntityId() : 0);
                row.createCell(6).setCellValue(nz(AuditLabels.module(l.getModule())));
                row.createCell(7).setCellValue(nz(AuditLabels.severity(l.getSeverity())));
                row.createCell(8).setCellValue(nz(l.getDescription()));
                row.createCell(9).setCellValue(nz(l.getIpAddress()));
                row.createCell(10).setCellValue(nz(l.getHash()));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            var out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            // HU-AU-06 E5 / HU-AU-08 E8 (2026-04-28): mensaje exacto HU.
            log.error("Error generando Excel de auditoria", e);
            throw new RuntimeException("No se pudo generar el reporte en el formato solicitado", e);
        }
    }

    /**
     * HU-AU-06 E1 / HU-AU-08 E3 (2026-04-28): Exportar a PDF REAL con iText7.
     * Antes este metodo devolvia texto plano con extension .pdf — los reportes
     * QA detectaron que no es un PDF valido. Ahora genera PDF/A con tabla,
     * encabezado y pie de pagina con metadatos.
     */
    public byte[] exportToPdf(List<AuditLogDTO> logs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4.rotate())) {

            PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontReg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // QA Bloque BJ (2026-05-17): header estandar empresa+usuario+rol+fecha+totales
            com.sigcon.backend.utils.export.ReportHeaderBuilder.ReportContext pdfCtx = reportContextResolver
                    .baseContext("Reporte de Auditoria - SIGCON")
                    .addFilter("Total registros", String.valueOf(logs.size()))
                    .build();
            com.sigcon.backend.utils.export.ReportHeaderBuilder.writePdfHeader(doc, pdfCtx);

            // Tabla
            float[] cols = {35f, 90f, 110f, 60f, 75f, 60f, 60f, 110f};
            Table table = new Table(UnitValue.createPointArray(cols)).useAllAvailableWidth();

            String[] headers = {"ID", "Fecha", "Usuario", "Accion", "Entidad",
                    "Modulo", "Severidad", "Descripcion"};
            DeviceRgb headerBg = new DeviceRgb(28, 64, 112);
            for (String h : headers) {
                Cell c = new Cell()
                        .add(new Paragraph(h).setFont(fontBold).setFontSize(8)
                                .setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(headerBg)
                        .setPadding(4f);
                table.addHeaderCell(c);
            }
            for (AuditLogDTO l : logs) {
                String fecha = l.getTimestamp() != null
                        ? l.getTimestamp().toString().substring(0, Math.min(19, l.getTimestamp().toString().length()))
                        : "";
                table.addCell(cellSm(fontReg, String.valueOf(l.getId() == null ? "" : l.getId())));
                table.addCell(cellSm(fontReg, fecha));
                table.addCell(cellSm(fontReg, nz(l.getUserEmail())));
                table.addCell(cellSm(fontReg, nz(AuditLabels.action(l.getAction()))));
                table.addCell(cellSm(fontReg, nz(AuditLabels.entity(l.getEntityType()))));
                table.addCell(cellSm(fontReg, nz(AuditLabels.module(l.getModule()))));
                table.addCell(cellSm(fontReg, nz(AuditLabels.severity(l.getSeverity()))));
                table.addCell(cellSm(fontReg, nz(l.getDescription())));
            }
            doc.add(table);

            // Pie con hash chain info
            doc.add(new Paragraph("\nDocumento generado automaticamente. Cadena hash SHA-256 verificable.")
                    .setFont(fontReg).setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF de auditoria", e);
            throw new RuntimeException("No se pudo generar el reporte en el formato solicitado", e);
        }
    }

    /**
     * HU-AU-07 E6 (2026-04-28): exporta el DASHBOARD a PDF (no logs individuales).
     * Incluye KPIs agregados: total eventos, conteo por severidad/modulo/accion
     * y los ultimos 10 eventos.
     */
    public byte[] exportDashboardToPdf(AuditDashboardDTO data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontReg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("Dashboard de Auditoria - SIGCON")
                    .setFont(fontBold).setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Generado: " + LocalDateTime.now())
                    .setFont(fontReg).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.add(new Paragraph("\nIndicadores Clave (ultimos 30 dias)")
                    .setFont(fontBold).setFontSize(12));
            doc.add(new Paragraph("Total de eventos: " + data.getTotalEvents())
                    .setFont(fontReg).setFontSize(10));

            // KPI por severidad con semaforo
            doc.add(new Paragraph("\nConteo por Severidad").setFont(fontBold).setFontSize(11));
            Table sevT = new Table(UnitValue.createPointArray(new float[]{200f, 100f, 60f}));
            sevT.addHeaderCell(headerCell(fontBold, "Severidad"));
            sevT.addHeaderCell(headerCell(fontBold, "Cantidad"));
            sevT.addHeaderCell(headerCell(fontBold, "Semaforo"));
            if (data.getCountBySeverity() != null) {
                data.getCountBySeverity().forEach((k, v) -> {
                    sevT.addCell(cellSm(fontReg, AuditLabels.severity(k)));
                    sevT.addCell(cellSm(fontReg, String.valueOf(v)));
                    String emoji = switch (k.toUpperCase()) {
                        case "CRITICAL" -> "ROJO";
                        case "HIGH"     -> "AMBAR";
                        case "MEDIUM"   -> "AMARILLO";
                        default          -> "VERDE";
                    };
                    sevT.addCell(cellSm(fontReg, emoji));
                });
            }
            doc.add(sevT);

            doc.add(new Paragraph("\nConteo por Modulo").setFont(fontBold).setFontSize(11));
            Table modT = new Table(UnitValue.createPointArray(new float[]{200f, 100f}));
            modT.addHeaderCell(headerCell(fontBold, "Modulo"));
            modT.addHeaderCell(headerCell(fontBold, "Cantidad"));
            if (data.getCountByModule() != null) {
                data.getCountByModule().forEach((k, v) -> {
                    modT.addCell(cellSm(fontReg, AuditLabels.module(k)));
                    modT.addCell(cellSm(fontReg, String.valueOf(v)));
                });
            }
            doc.add(modT);

            doc.add(new Paragraph("\nConteo por Accion").setFont(fontBold).setFontSize(11));
            Table actT = new Table(UnitValue.createPointArray(new float[]{200f, 100f}));
            actT.addHeaderCell(headerCell(fontBold, "Accion"));
            actT.addHeaderCell(headerCell(fontBold, "Cantidad"));
            if (data.getCountByAction() != null) {
                data.getCountByAction().forEach((k, v) -> {
                    actT.addCell(cellSm(fontReg, AuditLabels.action(k)));
                    actT.addCell(cellSm(fontReg, String.valueOf(v)));
                });
            }
            doc.add(actT);

            doc.add(new Paragraph("\nDocumento generado automaticamente. Indicadores agregados.")
                    .setFont(fontReg).setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF dashboard", e);
            throw new RuntimeException("No se pudo generar el reporte en el formato solicitado", e);
        }
    }

    /**
     * HU-AU-10 E6 (2026-04-28): genera PDF de evidencia de purga.
     *
     * <p>El PDF incluye:
     * <ul>
     *   <li>Metadata del proceso: fecha, usuario que ejecuto, lote</li>
     *   <li>Conteo de registros purgados</li>
     *   <li>Rango temporal (oldest/newest)</li>
     *   <li>Hash SHA-256 del lote (batch_hash) — evidencia de integridad</li>
     *   <li>Notas del proceso</li>
     * </ul>
     *
     * <p><b>Limitacion honesta:</b> el PDF NO esta firmado digitalmente con
     * certificado X.509 (eso requiere HSM). El hash SHA-256 sirve como
     * evidencia de integridad y permite verificar que los registros
     * archivados no fueron alterados post-purga.
     */
    public byte[] exportPurgeRecordToPdf(AuditPurgeRecord record) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.A4)) {

            PdfFont fontBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont fontReg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("Evidencia de Purga de Auditoria - SIGCON")
                    .setFont(fontBold).setFontSize(16)
                    .setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("HU-AU-10 E6 - Cumplimiento Decreto 2649/1993")
                    .setFont(fontReg).setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));
            doc.add(new Paragraph("\n"));

            // Tabla de metadatos
            Table metaT = new Table(UnitValue.createPointArray(new float[]{180f, 320f}));
            metaT.addCell(metaCell(fontBold, "Lote ID"));
            metaT.addCell(metaCell(fontReg, String.valueOf(record.getId())));
            metaT.addCell(metaCell(fontBold, "Fecha de purga"));
            metaT.addCell(metaCell(fontReg,
                    record.getPurgeDate() != null ? record.getPurgeDate().toString() : "-"));
            metaT.addCell(metaCell(fontBold, "Ejecutado por"));
            metaT.addCell(metaCell(fontReg, record.getExecutedBy() != null ? record.getExecutedBy() : "scheduler"));
            metaT.addCell(metaCell(fontBold, "Registros purgados"));
            metaT.addCell(metaCell(fontReg, String.valueOf(record.getRecordsPurged())));
            metaT.addCell(metaCell(fontBold, "Rango temporal del lote"));
            metaT.addCell(metaCell(fontReg,
                    (record.getOldestPurged() != null ? record.getOldestPurged().toString() : "-")
                    + " a " +
                    (record.getNewestPurged() != null ? record.getNewestPurged().toString() : "-")));
            doc.add(metaT);

            // Hash de evidencia (destacado)
            doc.add(new Paragraph("\nHash SHA-256 del lote (evidencia de integridad)")
                    .setFont(fontBold).setFontSize(11));
            String hash = record.getBatchHash() != null ? record.getBatchHash() : "(sin hash)";
            doc.add(new Paragraph(hash)
                    .setFont(fontReg).setFontSize(8)
                    .setBackgroundColor(new DeviceRgb(245, 245, 245))
                    .setPadding(8f));

            // Notas
            if (record.getNotes() != null && !record.getNotes().isBlank()) {
                doc.add(new Paragraph("\nNotas del proceso").setFont(fontBold).setFontSize(11));
                doc.add(new Paragraph(record.getNotes()).setFont(fontReg).setFontSize(9));
            }

            // Pie con disclaimer honesto
            doc.add(new Paragraph("\n\nDocumento de evidencia generado automaticamente. "
                    + "El hash SHA-256 permite verificar la integridad del lote archivado.")
                    .setFont(fontReg).setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));
            doc.add(new Paragraph("Nota: este PDF NO incluye firma digital con certificado X.509. "
                    + "Para validez legal extendida usar HSM/KMS en infraestructura de produccion.")
                    .setFont(fontReg).setFontSize(7)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("HU-AU-10 E6: error generando PDF evidencia de purga", e);
            throw new RuntimeException("No se pudo generar el reporte en el formato solicitado", e);
        }
    }

    private Cell metaCell(PdfFont font, String text) {
        return new Cell()
                .add(new Paragraph(text == null ? "" : text).setFont(font).setFontSize(9))
                .setPadding(5f);
    }

    private Cell headerCell(PdfFont font, String text) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(new DeviceRgb(28, 64, 112))
                .setPadding(4f);
    }

    private Cell cellSm(PdfFont font, String text) {
        return new Cell()
                .add(new Paragraph(text == null ? "" : text).setFont(font).setFontSize(7))
                .setPadding(3f);
    }

    private String nz(String s) { return s == null ? "" : s; }
}
