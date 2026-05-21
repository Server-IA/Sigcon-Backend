package com.sigcon.backend.banks.matching.domain.service;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sigcon.backend.banks.archivos_soporte.domain.model.ArchivoSoporte;
import com.sigcon.backend.banks.archivos_soporte.domain.repository.ArchivoSoporteRepository;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.matching.domain.model.FirmaElectronica;
import com.sigcon.backend.banks.matching.domain.model.PartidaConciliatoria;
import com.sigcon.backend.banks.matching.domain.model.SesionConciliacion;
import com.sigcon.backend.banks.matching.domain.repository.FirmaElectronicaRepository;
import com.sigcon.backend.banks.matching.domain.repository.PartidaConciliatoriaRepository;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * BNK-HU-077: informe de conciliación en PDF con 7 secciones obligatorias + hash
 * SHA-256 + almacenamiento en archivos_soporte (retención 10 años).
 *
 * STAND-IN documentado: se genera un PDF estándar (iText7). El cumplimiento ESTRICTO
 * PDF/A-3 (ISO 19005-3) con adjuntos embebidos (E3) y firma PAdES-LTV (E5) requiere
 * toolchain de cumplimiento + certificado digital (infra no disponible) y queda
 * diferido. El WORM/replicación (E7) también es infra (se marca replication_status).
 */
@Service
@RequiredArgsConstructor
public class ReconciliationReportPdfService {

    private final BankAccountRepository bankAccountRepository;
    private final PartidaConciliatoriaRepository partidaRepository;
    private final FirmaElectronicaRepository firmaRepository;
    private final ArchivoSoporteRepository archivoRepository;
    private final CompanyRepository companyRepository;
    private final ElectronicSignatureService signatureService;

    /** HU-077 E1-E4: arma el PDF de 7 secciones, calcula su hash y lo almacena. */
    public ArchivoSoporte generateAndStore(SesionConciliacion s, Long uploadedBy) {
        byte[] pdf = buildPdf(s);
        String hash = signatureService.sha256Hex(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1));
        ArchivoSoporte a = ArchivoSoporte.builder()
                .companyId(s.getCompanyId())
                .tipo("INFORME_CONCILIACION")
                .fileName("informe_conciliacion_s" + s.getId() + "_v" + s.getVersion() + ".pdf")
                .mimeType("application/pdf")
                .fileContent(pdf)
                .hashSha256(hash)
                .fileSize((long) pdf.length)
                .bankAccountId(s.getBankAccountId())
                .reconciliationSessionId(s.getId())
                .uploadedBy(uploadedBy)
                .uploadedAt(LocalDateTime.now())
                .retenerHasta(LocalDateTime.now().plusYears(10)) // HU-077 E7
                .replicationStatus("PENDING") // WORM/replicación es infra (diferido)
                .build();
        return archivoRepository.save(a);
    }

    /** HU-077 E8: recupera el informe almacenado para descarga. */
    public ArchivoSoporte fetchInforme(Long archivoId) {
        return archivoRepository.findById(archivoId)
                .orElseThrow(() -> new IllegalArgumentException("Informe no encontrado: " + archivoId));
    }

    private byte[] buildPdf(SesionConciliacion s) {
        BankAccount ba = bankAccountRepository.findById(s.getBankAccountId()).orElse(null);
        String bancoNombre = (ba != null && ba.getBank() != null) ? ba.getBank().getName() : "-";
        String cuentaMask = ba != null ? maskAccount(ba.getAccountNumber()) : "-";
        String empresaNombre = "-", empresaNit = "-";
        var comp = companyRepository.findById(s.getCompanyId());
        if (comp.isPresent()) { empresaNombre = comp.get().getBusinessName(); empresaNit = comp.get().getNit(); }

        List<PartidaConciliatoria> partidas = partidaRepository.findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(s.getBankAccountId());
        List<FirmaElectronica> firmas = firmaRepository.findBySesionIdOrderByIdAsc(s.getId());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.add(new Paragraph("INFORME DE CONCILIACIÓN BANCARIA")
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER));

            // (1) Encabezado
            section(doc, "1. Encabezado");
            Table h = kv();
            kvRow(h, "Empresa", empresaNombre); kvRow(h, "NIT", empresaNit);
            kvRow(h, "Banco", bancoNombre); kvRow(h, "Cuenta", cuentaMask);
            kvRow(h, "Período", String.valueOf(s.getPeriodStart()) + " a " + s.getPeriodEnd());
            kvRow(h, "ID conciliación", String.valueOf(s.getId()));
            kvRow(h, "Versión", String.valueOf(s.getVersion()));
            kvRow(h, "Fecha elaboración", String.valueOf(s.getCreatedAt()));
            doc.add(h);

            // (2) Saldos
            section(doc, "2. Saldos");
            Table sb = kv();
            kvRow(sb, "Saldo extracto", money(s.getSaldoExtracto()));
            kvRow(sb, "Saldo libros", money(s.getSaldoLibros()));
            kvRow(sb, "Diferencia bruta", money(s.getDiferencia()));
            doc.add(sb);

            // (3) Partidas conciliatorias agrupadas por tipo
            section(doc, "3. Partidas conciliatorias");
            Map<String, List<PartidaConciliatoria>> porTipo = new TreeMap<>();
            for (PartidaConciliatoria p : partidas) porTipo.computeIfAbsent(p.getTipo(), k -> new ArrayList<>()).add(p);
            if (porTipo.isEmpty()) doc.add(new Paragraph("Sin partidas conciliatorias.").setFontSize(9));
            for (var e : porTipo.entrySet()) {
                doc.add(new Paragraph(e.getKey() + " (" + e.getValue().size() + ")").setBold().setFontSize(10));
                Table t = new Table(UnitValue.createPercentArray(new float[]{2, 4, 2, 2})).useAllAvailableWidth();
                th(t, "Mov"); th(t, "Descripción"); th(t, "Monto"); th(t, "Estado");
                for (PartidaConciliatoria p : e.getValue()) {
                    td(t, String.valueOf(p.getFinancialMovementId())); td(t, nz(p.getDescripcion()));
                    td(t, money(p.getMonto())); td(t, p.getEstado());
                }
                doc.add(t);
            }

            // (4) Cuadre aritmético
            section(doc, "4. Cuadre aritmético");
            BigDecimal totalPartidas = partidas.stream().map(p -> p.getMonto() == null ? BigDecimal.ZERO : p.getMonto())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            doc.add(new Paragraph("Total partidas conciliatorias: " + money(totalPartidas)).setFontSize(9));
            doc.add(new Paragraph("Diferencia bruta extracto vs libros: " + money(s.getDiferencia())).setFontSize(9));

            // (5) Asientos de ajuste generados
            section(doc, "5. Asientos de ajuste generados");
            Table aj = new Table(UnitValue.createPercentArray(new float[]{3, 3, 4})).useAllAvailableWidth();
            th(aj, "Partida"); th(aj, "Comprobante"); th(aj, "Tipo");
            boolean anyAj = false;
            for (PartidaConciliatoria p : partidas) {
                if (p.getComprobanteAjusteId() != null) {
                    anyAj = true;
                    td(aj, String.valueOf(p.getId())); td(aj, "#" + p.getComprobanteAjusteId()); td(aj, p.getTipo());
                }
            }
            doc.add(aj);
            if (!anyAj) doc.add(new Paragraph("Sin asientos de ajuste.").setFontSize(9));

            // (6) Archivo de extracto original
            section(doc, "6. Extracto original");
            doc.add(new Paragraph("Hash SHA-256 del extracto conciliado: " + nz(s.getHashExtracto())).setFontSize(8));

            // (7) Firmas
            section(doc, "7. Firmas");
            if (firmas.isEmpty()) doc.add(new Paragraph("Sin firmas registradas.").setFontSize(9));
            for (FirmaElectronica f : firmas) {
                doc.add(new Paragraph(f.getRolFirma() + ": " + nz(f.getFirmanteNombre())
                        + " | T.P. " + nz(f.getFirmanteTp()) + " | " + f.getMetodoFirma()
                        + " | " + (f.getSelloTiempo() != null ? f.getSelloTiempo().toString() : "")
                        + " | hash " + nz(f.getHashDocumento())).setFontSize(8));
            }
            doc.add(new Paragraph("\nDocumento generado por SIGCON. PDF/A-3 estricto y firma PAdES-LTV "
                    + "quedan diferidos por infraestructura (certificado digital/TSA).").setFontSize(7).setItalic());
        } catch (Exception e) {
            throw new IllegalStateException("Error generando el informe PDF: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    // ---- helpers iText ----
    private void section(Document d, String t) { d.add(new Paragraph(t).setBold().setFontSize(11).setMarginTop(8)); }
    private Table kv() { return new Table(UnitValue.createPercentArray(new float[]{3, 7})).useAllAvailableWidth(); }
    private void kvRow(Table t, String k, String v) {
        t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(k).setBold().setFontSize(9)));
        t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nz(v)).setFontSize(9)));
    }
    private void th(Table t, String s) { t.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(s).setBold().setFontSize(8))); }
    private void td(Table t, String s) { t.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(nz(s)).setFontSize(8))); }

    private String maskAccount(String acc) {
        if (acc == null || acc.length() <= 4) return "****";
        return "****" + acc.substring(acc.length() - 4);
    }
    private String money(BigDecimal b) { return b == null ? "$0.00" : "$" + b.toPlainString(); }
    private String nz(String s) { return s == null ? "" : s; }
}
