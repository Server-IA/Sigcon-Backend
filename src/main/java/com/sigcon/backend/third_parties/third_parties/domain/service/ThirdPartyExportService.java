package com.sigcon.backend.third_parties.third_parties.domain.service;

import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de exportacion de terceros a CSV y Excel (TER-09).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPartyExportService {

    private final ThirdPartyRepository thirdPartyRepository;

    private static final String[] HEADERS = {
            "Codigo", "NIT", "DV", "Razon Social", "Estado", "Roles",
            "Municipio", "Regimen", "Organizacion", "Limite Credito"
    };

    /**
     * Exporta todos los terceros activos en formato CSV.
     */
    public byte[] exportCsv() {
        return exportCsv(null, null);
    }

    /**
     * HU-TER-09 E1/E3 (2026-04-27): exportar con filtros opcionales por rol
     * (CLIENTE/PROVEEDOR/EMPLEADO/ACREEDOR/DEUDOR/OTRO) y estado
     * (ACTIVO/INACTIVO/BLOQUEADO). Si no se aplican filtros, exporta todos.
     * Si los filtros no devuelven resultados, retorna archivo vacio con
     * solo headers (frontend mostrara mensaje "No se encontraron registros").
     */
    public byte[] exportCsv(String roleFilter, String statusFilter) {
        List<ThirdParty> terceros = applyFilters(thirdPartyRepository.findAll(), roleFilter, statusFilter);
        StringBuilder sb = new StringBuilder();

        // BOM UTF-8 para compatibilidad con Excel
        sb.append('\uFEFF');

        // Headers
        sb.append(String.join(",", HEADERS)).append("\n");

        // Data
        for (ThirdParty tp : terceros) {
            sb.append(escapeCsv(tp.getThirdPartyCode())).append(",");
            sb.append(escapeCsv(tp.getNit())).append(",");
            sb.append(escapeCsv(tp.getDv())).append(",");
            sb.append(escapeCsv(tp.getBusinessName())).append(",");
            sb.append(escapeCsv(tp.getStatus() != null ? tp.getStatus().getName() : "")).append(",");
            sb.append(escapeCsv(tp.getRoles() != null ?
                    tp.getRoles().stream().map(r -> r.getName()).collect(Collectors.joining("; ")) : "")).append(",");
            sb.append(escapeCsv(tp.getMunicipality() != null ? tp.getMunicipality().getName() : "")).append(",");
            sb.append(escapeCsv(tp.getTypeRegimen() != null ? tp.getTypeRegimen().getName() : "")).append(",");
            sb.append(escapeCsv(tp.getTypeOrganization() != null ? tp.getTypeOrganization().getName() : "")).append(",");
            sb.append(tp.getCreditLimit() != null ? tp.getCreditLimit().toPlainString() : "0");
            sb.append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Exporta todos los terceros activos en formato Excel (XLSX).
     */
    public byte[] exportExcel() {
        return exportExcel(null, null);
    }

    public byte[] exportExcel(String roleFilter, String statusFilter) {
        List<ThirdParty> terceros = applyFilters(thirdPartyRepository.findAll(), roleFilter, statusFilter);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Terceros");

            // Estilo para encabezados
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowIdx = 1;
            for (ThirdParty tp : terceros) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(safe(tp.getThirdPartyCode()));
                row.createCell(1).setCellValue(safe(tp.getNit()));
                row.createCell(2).setCellValue(safe(tp.getDv()));
                row.createCell(3).setCellValue(safe(tp.getBusinessName()));
                row.createCell(4).setCellValue(tp.getStatus() != null ? tp.getStatus().getName() : "");
                row.createCell(5).setCellValue(tp.getRoles() != null ?
                        tp.getRoles().stream().map(r -> r.getName()).collect(Collectors.joining("; ")) : "");
                row.createCell(6).setCellValue(tp.getMunicipality() != null ? tp.getMunicipality().getName() : "");
                row.createCell(7).setCellValue(tp.getTypeRegimen() != null ? tp.getTypeRegimen().getName() : "");
                row.createCell(8).setCellValue(tp.getTypeOrganization() != null ? tp.getTypeOrganization().getName() : "");
                row.createCell(9).setCellValue(tp.getCreditLimit() != null ? tp.getCreditLimit().doubleValue() : 0);
            }

            // Auto-size columns
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Error generando Excel de terceros", e);
            throw new RuntimeException("Error al generar el archivo Excel");
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    /**
     * HU-TER-09 E1/E3: filtra el listado por rol y/o estado (case-insensitive).
     * Acepta tanto el code (ej. CLIENT) como el nombre traducido (ej. Cliente).
     * Si los filtros son null o vacios, devuelve la lista intacta.
     */
    private List<ThirdParty> applyFilters(List<ThirdParty> source, String roleFilter, String statusFilter) {
        List<ThirdParty> result = source;
        if (statusFilter != null && !statusFilter.isBlank()) {
            String needle = statusFilter.trim();
            result = result.stream().filter(tp -> tp.getStatus() != null
                    && needle.equalsIgnoreCase(tp.getStatus().getName()))
                    .collect(Collectors.toList());
        }
        if (roleFilter != null && !roleFilter.isBlank()) {
            String needle = roleFilter.trim();
            result = result.stream().filter(tp -> tp.getRoles() != null && tp.getRoles().stream()
                    .anyMatch(r -> needle.equalsIgnoreCase(r.getName())))
                    .collect(Collectors.toList());
        }
        return result;
    }
}
