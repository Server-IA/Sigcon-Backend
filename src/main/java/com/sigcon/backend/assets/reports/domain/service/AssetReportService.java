package com.sigcon.backend.assets.reports.domain.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.assets.reports.application.AssetReportDTO;
import com.sigcon.backend.reports.domain.service.ReportPdfService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ACT-04/ACT-07 - Servicio de generacion de reportes de activos fijos.
 *
 * <p>Permite generar reportes en formato JSON (datos estructurados) o PDF,
 * filtrando por rango de fechas de adquisicion y con opcion de agrupamiento
 * por clasificacion, periodo mensual o sin agrupar.</p>
 *
 * @see com.sigcon.backend.assets.reports.interfaces.controller.AssetReportController
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetReportService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DeviceRgb TABLE_HEADER_BG = new DeviceRgb(30, 58, 138);
    private static final DeviceRgb TABLE_ALT_ROW = new DeviceRgb(238, 242, 255);

    private final AssetsRepository assetsRepository;
    private final ReportPdfService reportPdfService;

    /**
     * Genera el reporte de activos como datos estructurados agrupados.
     *
     * @param startDate fecha de inicio del rango de adquisicion
     * @param endDate   fecha de fin del rango de adquisicion
     * @param groupBy   criterio de agrupamiento: "classification", "period" o "asset" (sin agrupar)
     * @return mapa con clave de grupo y lista de DTOs de activos; si no hay agrupamiento,
     *         la clave es "todos"
     */
    public Map<String, List<AssetReportDTO>> generateAssetReport(LocalDate startDate,
                                                                  LocalDate endDate,
                                                                  String groupBy) {
        List<Assets> assets = fetchAssetsByDateRange(startDate, endDate);
        List<AssetReportDTO> dtos = assets.stream()
                .map(this::toReportDTO)
                .collect(Collectors.toList());

        if ("classification".equalsIgnoreCase(groupBy)) {
            return dtos.stream()
                    .collect(Collectors.groupingBy(
                            AssetReportDTO::getClassification,
                            LinkedHashMap::new,
                            Collectors.toList()));
        }

        if ("period".equalsIgnoreCase(groupBy)) {
            return dtos.stream()
                    .collect(Collectors.groupingBy(
                            dto -> dto.getAcquisitionDate() != null
                                    ? dto.getAcquisitionDate().format(MONTH_FMT)
                                    : "sin-fecha",
                            LinkedHashMap::new,
                            Collectors.toList()));
        }

        // Sin agrupamiento: clave unica "todos"
        Map<String, List<AssetReportDTO>> result = new LinkedHashMap<>();
        result.put("todos", dtos);
        return result;
    }

    /**
     * Genera el reporte de activos en formato PDF.
     *
     * <p>Construye una tabla con los datos de cada activo y delega el ensamblaje
     * final del documento al {@link ReportPdfService} para mantener la plantilla
     * institucional de SIGCON.</p>
     *
     * @param startDate fecha de inicio del rango de adquisicion
     * @param endDate   fecha de fin del rango de adquisicion
     * @param groupBy   criterio de agrupamiento
     * @return bytes crudos del PDF generado
     * @throws IOException si la construccion del PDF falla
     */
    public byte[] generateAssetReportPdf(LocalDate startDate,
                                          LocalDate endDate,
                                          String groupBy) throws IOException {
        Map<String, List<AssetReportDTO>> grouped = generateAssetReport(startDate, endDate, groupBy);
        List<Paragraph> bodyContent = buildPdfBody(grouped, startDate, endDate);

        String title = "Reporte de Activos Fijos ("
                + startDate.format(DATE_FMT) + " - " + endDate.format(DATE_FMT) + ")";
        return reportPdfService.generateReport(title, bodyContent);
    }

    // ─── Metodos privados ───────────────────────────────────────────────────

    /**
     * Consulta activos con fecha de adquisicion en el rango dado y sin eliminacion logica.
     */
    private List<Assets> fetchAssetsByDateRange(LocalDate startDate, LocalDate endDate) {
        Specification<Assets> spec = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("acquisitionDate"), startDate),
                cb.lessThanOrEqualTo(root.get("acquisitionDate"), endDate),
                cb.isNull(root.get("deletedAt"))
        );
        return assetsRepository.findAll(spec);
    }

    /**
     * Convierte una entidad {@link Assets} a un {@link AssetReportDTO}.
     */
    private AssetReportDTO toReportDTO(Assets asset) {
        BigDecimal bookValue = asset.getCurrentBookValue() != null
                ? asset.getCurrentBookValue()
                : asset.getAcquisitionValue();
        BigDecimal depreciation = asset.getAcquisitionValue().subtract(bookValue);

        String supplierName = asset.getSupplier() != null
                ? asset.getSupplier().getBusinessName()
                : null;

        // HU-ACT-07 E2: incluir cuenta contable PUC en formato "codigo - nombre".
        String accountInfo = null;
        if (asset.getAccountingAccount() != null
                && asset.getAccountingAccount().getPucAccount() != null) {
            String code = asset.getAccountingAccount().getPucAccount().getCode();
            String name = asset.getAccountingAccount().getPucAccount().getName();
            if (code != null && name != null) accountInfo = code + " - " + name;
            else if (code != null) accountInfo = code;
            else if (name != null) accountInfo = name;
        }

        return AssetReportDTO.builder()
                .assetCode(asset.getAssetCode())
                .assetName(asset.getAssetName())
                .classification(asset.getClassification() != null
                        ? asset.getClassification().name()
                        : "N/A")
                .acquisitionDate(asset.getAcquisitionDate())
                .acquisitionValue(asset.getAcquisitionValue())
                .currentBookValue(bookValue)
                .depreciation(depreciation)
                .status(asset.getStatus() != null ? asset.getStatus().name() : "N/A")
                .supplierName(supplierName)
                .description(asset.getDescription())
                .accountInfo(accountInfo)
                .build();
    }

    /**
     * Construye el contenido del cuerpo del PDF con tablas de activos agrupados.
     */
    private List<Paragraph> buildPdfBody(Map<String, List<AssetReportDTO>> grouped,
                                          LocalDate startDate,
                                          LocalDate endDate) throws IOException {
        List<Paragraph> body = new ArrayList<>();
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        int totalAssets = grouped.values().stream().mapToInt(List::size).sum();
        body.add(new Paragraph("Total de activos en el reporte: " + totalAssets)
                .setFont(regular)
                .setFontSize(10)
                .setMarginBottom(12));

        for (Map.Entry<String, List<AssetReportDTO>> entry : grouped.entrySet()) {
            // Titulo del grupo
            body.add(new Paragraph("Grupo: " + entry.getKey())
                    .setFont(bold)
                    .setFontSize(10)
                    .setFontColor(TABLE_HEADER_BG)
                    .setMarginTop(10)
                    .setMarginBottom(6));

            // Tabla de activos
            String[] headers = {"Codigo", "Nombre", "Clasificacion", "Fecha Adq.", "Valor Adq.",
                    "Valor Libros", "Depreciacion", "Estado", "Proveedor"};
            float[] colWidths = {8f, 14f, 10f, 9f, 11f, 11f, 11f, 9f, 17f};

            Table table = new Table(UnitValue.createPercentArray(colWidths))
                    .setWidth(UnitValue.createPercentValue(100))
                    .setFontSize(7);

            // Encabezados
            for (String header : headers) {
                table.addHeaderCell(new Cell()
                        .setBackgroundColor(TABLE_HEADER_BG)
                        .setPadding(4)
                        .add(new Paragraph(header)
                                .setFont(bold)
                                .setFontSize(7)
                                .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                                .setTextAlignment(TextAlignment.CENTER)));
            }

            // Filas de datos
            int rowIdx = 0;
            for (AssetReportDTO dto : entry.getValue()) {
                DeviceRgb rowBg = (rowIdx % 2 == 0) ? null : TABLE_ALT_ROW;
                addDataCell(table, dto.getAssetCode(), regular, rowBg);
                addDataCell(table, dto.getAssetName(), regular, rowBg);
                addDataCell(table, dto.getClassification(), regular, rowBg);
                addDataCell(table, dto.getAcquisitionDate() != null
                        ? dto.getAcquisitionDate().format(DATE_FMT) : "", regular, rowBg);
                addDataCell(table, formatMoney(dto.getAcquisitionValue()), regular, rowBg);
                addDataCell(table, formatMoney(dto.getCurrentBookValue()), regular, rowBg);
                addDataCell(table, formatMoney(dto.getDepreciation()), regular, rowBg);
                addDataCell(table, dto.getStatus(), regular, rowBg);
                addDataCell(table, dto.getSupplierName() != null ? dto.getSupplierName() : "N/A",
                        regular, rowBg);
                rowIdx++;
            }

            // Se agrega la tabla como un parrafo contenedor
            body.add(new Paragraph().add(table).setMarginBottom(14));
        }

        return body;
    }

    /**
     * Agrega una celda de datos a la tabla del PDF.
     */
    private void addDataCell(Table table, String value, PdfFont font, DeviceRgb bgColor) {
        Cell cell = new Cell()
                .setPadding(3)
                .add(new Paragraph(value != null ? value : "")
                        .setFont(font)
                        .setFontSize(7));
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        table.addCell(cell);
    }

    /**
     * Formatea un valor monetario para presentacion en el reporte.
     */
    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "$0.00";
        }
        return "$" + String.format("%,.2f", value);
    }
}
