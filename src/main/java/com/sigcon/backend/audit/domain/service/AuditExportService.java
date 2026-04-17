package com.sigcon.backend.audit.domain.service;

import com.sigcon.backend.audit.application.AuditLogDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * HU-AU-06: Servicio de exportacion de logs de auditoria en CSV, Excel y PDF.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditExportService {

    /** HU-AU-06 E3: Exportar a CSV. */
    public byte[] exportToCsv(List<AuditLogDTO> logs) {
        StringBuilder csv = new StringBuilder();
        csv.append("\uFEFF"); // BOM UTF-8
        csv.append("ID;Fecha;Usuario;Accion;Entidad;ID_Entidad;Modulo;Severidad;Descripcion;IP;Hash\n");
        for (AuditLogDTO l : logs) {
            csv.append(l.getId()).append(';')
               .append(l.getTimestamp()).append(';')
               .append(nz(l.getUserEmail())).append(';')
               .append(nz(l.getAction())).append(';')
               .append(nz(l.getEntityType())).append(';')
               .append(l.getEntityId() != null ? l.getEntityId() : "").append(';')
               .append(nz(l.getModule())).append(';')
               .append(nz(l.getSeverity())).append(';')
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

            // Header
            XSSFRow headerRow = sheet.createRow(0);
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
                XSSFRow row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue(l.getId() != null ? l.getId() : 0);
                row.createCell(1).setCellValue(l.getTimestamp() != null ? l.getTimestamp().toString() : "");
                row.createCell(2).setCellValue(nz(l.getUserEmail()));
                row.createCell(3).setCellValue(nz(l.getAction()));
                row.createCell(4).setCellValue(nz(l.getEntityType()));
                row.createCell(5).setCellValue(l.getEntityId() != null ? l.getEntityId() : 0);
                row.createCell(6).setCellValue(nz(l.getModule()));
                row.createCell(7).setCellValue(nz(l.getSeverity()));
                row.createCell(8).setCellValue(nz(l.getDescription()));
                row.createCell(9).setCellValue(nz(l.getIpAddress()));
                row.createCell(10).setCellValue(nz(l.getHash()));
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            var out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel de auditoria", e);
        }
    }

    /** HU-AU-06 E1: Exportar a PDF (simplificado con texto plano). */
    public byte[] exportToPdf(List<AuditLogDTO> logs) {
        // PDF simplificado — texto plano formateado
        StringBuilder text = new StringBuilder();
        text.append("REPORTE DE AUDITORIA - SIGCON\n");
        text.append("Generado: ").append(java.time.LocalDateTime.now()).append("\n");
        text.append("Total registros: ").append(logs.size()).append("\n\n");
        text.append(String.format("%-6s %-20s %-25s %-8s %-15s %-8s %-10s%n",
                "ID", "Fecha", "Usuario", "Accion", "Entidad", "Modulo", "Severidad"));
        text.append("-".repeat(100)).append("\n");
        for (AuditLogDTO l : logs) {
            text.append(String.format("%-6s %-20s %-25s %-8s %-15s %-8s %-10s%n",
                    l.getId(),
                    l.getTimestamp() != null ? l.getTimestamp().toString().substring(0, 19) : "",
                    nz(l.getUserEmail()),
                    nz(l.getAction()),
                    nz(l.getEntityType()),
                    nz(l.getModule()),
                    nz(l.getSeverity())));
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String nz(String s) { return s == null ? "" : s; }
}
