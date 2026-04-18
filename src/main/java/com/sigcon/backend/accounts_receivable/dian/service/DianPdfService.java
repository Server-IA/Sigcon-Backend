package com.sigcon.backend.accounts_receivable.dian.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sigcon.backend.accounts_receivable.dian.submissions.domain.model.DianInvoiceSubmission;
import com.sigcon.backend.accounts_receivable.dian.submissions.domain.repository.DianInvoiceSubmissionRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceLine;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.parametrization.parameters.domain.service.SystemInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de representacion grafica de la factura electronica en formato PDF
 * (AR-16). Incluye datos del emisor, cliente, lineas, totales, numero de
 * resolucion DIAN, CUFE y codigo QR de verificacion segun el Anexo Tecnico
 * de facturacion electronica (Resolucion 0042 de 2020).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DianPdfService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final DianInvoiceSubmissionRepository submissionRepository;
    private final SystemInfoService systemInfoService;

    /** URL base de consulta publica del CUFE. */
    private static final String QR_URL_PREFIX = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=";

    /**
     * Genera el PDF binario de la representacion grafica de la factura.
     *
     * @param invoiceId id de la factura de venta
     * @return arreglo de bytes con el PDF
     */
    public byte[] generatePdf(Long invoiceId) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("La factura de venta no fue encontrada"));

        String cufe = Optional.ofNullable(invoice.getCufe()).orElseGet(() ->
                submissionRepository.findFirstBySalesInvoiceIdAndDeletedAtIsNullOrderByIdDesc(invoiceId)
                        .map(DianInvoiceSubmission::getCufe).orElse(""));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);

            // Encabezado emisor
            String companyName = Optional.ofNullable(systemInfoService.getCompanyName()).orElse("Empresa");
            String nit = Optional.ofNullable(systemInfoService.getCompanyNit()).orElse("");
            String dv = Optional.ofNullable(systemInfoService.getCompanyDv()).orElse("");

            doc.add(new Paragraph(companyName).setBold().setFontSize(14));
            doc.add(new Paragraph("NIT: " + nit + (dv.isEmpty() ? "" : "-" + dv)).setFontSize(10));
            doc.add(new Paragraph("Factura Electronica de Venta").setBold().setFontSize(12)
                    .setTextAlignment(TextAlignment.CENTER));

            // Datos factura
            Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            header.addCell(cell("No. Factura: " + safe(invoice.getInvoiceNumber())));
            header.addCell(cell("Fecha: " + (invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : "")));
            header.addCell(cell("Resolucion DIAN: " + safe(invoice.getResolutionNumber())));
            header.addCell(cell("Vencimiento: " + (invoice.getDueDate() != null ? invoice.getDueDate() : "")));
            doc.add(header);

            // Datos cliente
            if (invoice.getThirdParty() != null) {
                doc.add(new Paragraph("Cliente").setBold().setFontSize(11));
                Table t = new Table(UnitValue.createPercentArray(new float[]{1, 2})).useAllAvailableWidth();
                t.addCell(cell("Razon social:"));
                t.addCell(cell(safe(invoice.getThirdParty().getBusinessName())));
                t.addCell(cell("NIT:"));
                t.addCell(cell(safe(invoice.getThirdParty().getNit())));
                doc.add(t);
            }

            // Lineas
            doc.add(new Paragraph("Detalle").setBold().setFontSize(11));
            Table lines = new Table(UnitValue.createPercentArray(new float[]{4, 1, 2, 2, 2}))
                    .useAllAvailableWidth();
            lines.addHeaderCell(headerCell("Descripcion"));
            lines.addHeaderCell(headerCell("Cant."));
            lines.addHeaderCell(headerCell("Vr. Unit"));
            lines.addHeaderCell(headerCell("Subtotal"));
            lines.addHeaderCell(headerCell("Total"));
            if (invoice.getLines() != null) {
                for (SalesInvoiceLine line : invoice.getLines()) {
                    lines.addCell(cell(safe(line.getDescription())));
                    lines.addCell(cell(line.getQuantity() != null ? line.getQuantity().toPlainString() : "-"));
                    lines.addCell(cell(money(line.getUnitPrice())));
                    lines.addCell(cell(money(line.getSubtotal())));
                    lines.addCell(cell(money(line.getTotal())));
                }
            }
            doc.add(lines);

            // Totales
            Table totals = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth();
            totals.addCell(cell("Subtotal"));
            totals.addCell(cell(money(invoice.getSubtotal())));
            totals.addCell(cell("IVA"));
            totals.addCell(cell(money(invoice.getTotalTax())));
            totals.addCell(cell("Retenciones"));
            totals.addCell(cell(money(invoice.getTotalWithholding())));
            totals.addCell(cell("Total factura"));
            totals.addCell(cell(money(invoice.getTotalAmount())));
            doc.add(totals);

            // CUFE y QR
            doc.add(new Paragraph("CUFE").setBold().setFontSize(10));
            doc.add(new Paragraph(safe(cufe)).setFontSize(7));

            byte[] qrBytes = generateQrPng(QR_URL_PREFIX + safe(cufe), 200);
            if (qrBytes != null) {
                Image qr = new Image(ImageDataFactory.create(qrBytes));
                qr.setWidth(120);
                qr.setHeight(120);
                doc.add(qr);
                doc.add(new Paragraph("Consulte esta factura escaneando el codigo QR.")
                        .setFontSize(8).setFontColor(ColorConstants.DARK_GRAY));
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF para factura {}", invoiceId, e);
            throw new IllegalStateException("Error generando la representacion grafica PDF");
        }
    }

    private byte[] generateQrPng(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            java.util.Map<EncodeHintType, Object> hints = new java.util.HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("No se pudo generar el codigo QR: {}", e.getMessage());
            return null;
        }
    }

    private Cell cell(String text) { return new Cell().add(new Paragraph(text == null ? "" : text).setFontSize(9)); }
    private Cell headerCell(String text) {
        return new Cell().add(new Paragraph(text).setBold().setFontSize(9))
                .setBackgroundColor(ColorConstants.LIGHT_GRAY);
    }
    private String money(java.math.BigDecimal v) {
        if (v == null) return "-";
        return NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(v);
    }
    private String safe(String s) { return s == null ? "" : s; }
}
