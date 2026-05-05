package com.sigcon.backend.accounts_receivable.dian.service;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.model.DianResolution;
import com.sigcon.backend.accounts_receivable.dian.resolutions.domain.repository.DianResolutionRepository;
import com.sigcon.backend.accounts_receivable.dian.submissions.domain.model.DianInvoiceSubmission;
import com.sigcon.backend.accounts_receivable.dian.submissions.domain.model.DianSubmissionStatus;
import com.sigcon.backend.accounts_receivable.dian.submissions.domain.repository.DianInvoiceSubmissionRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceLine;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.parametrization.parameters.domain.service.SystemInfoService;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de generacion del XML UBL 2.1 de factura electronica (AR-14).
 * Produce un documento sintacticamente valido con las secciones principales
 * exigidas por el Anexo Tecnico de la DIAN (Resolucion 0042 de 2020):
 * ProfileID, AccountingSupplierParty, AccountingCustomerParty, InvoiceLine,
 * TaxTotal y LegalMonetaryTotal. No realiza firma digital ni validacion
 * estricta contra XSD; para produccion se debe integrar la firma XAdES.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DianXmlService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final DianResolutionRepository resolutionRepository;
    private final DianInvoiceSubmissionRepository submissionRepository;
    private final CufeCalculator cufeCalculator;
    private final SystemInfoService systemInfoService;
    private final AuditPublisher auditPublisher;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String UBL_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String CBC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String CAC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    /**
     * Genera el XML UBL 2.1, calcula el CUFE y crea el registro de transmision.
     *
     * @param invoiceId id de la factura de venta
     * @return envio DIAN persistido con XML, base64 y CUFE
     */
    @Transactional
    public ResponseEntity<?> generateXml(Long invoiceId) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("La factura de venta no fue encontrada"));

        if (Boolean.TRUE.equals(invoice.getXmlSent())) {
            throw new IllegalStateException("La factura ya fue enviada a la DIAN");
        }

        // HU-AR-14 E3: validar NIT cliente antes de armar XML
        if (invoice.getThirdParty() == null
                || invoice.getThirdParty().getNit() == null
                || invoice.getThirdParty().getNit().isBlank()) {
            throw new IllegalArgumentException(
                    "El cliente no tiene NIT o cedula validos. Complete los datos del cliente "
                    + "en el modulo de Terceros antes de generar la factura electronica.");
        }

        DianResolution resolution = findResolutionForInvoice(invoice);
        // HU-AR-14 E2: validar resolucion vigente y con rango disponible antes de generar XML
        if (resolution == null) {
            throw new IllegalStateException(
                    "No hay una resolucion de facturacion vigente para esta factura. "
                    + "Registre y active una resolucion DIAN antes de emitir.");
        }
        // Validacion de vigencia/rango replicando consumeNumber sin consumir todavia el numero
        java.time.LocalDate today = java.time.LocalDate.now();
        if (resolution.getEndDate() != null && resolution.getEndDate().isBefore(today)) {
            throw new IllegalStateException(
                    "La resolucion de facturacion ha vencido. Registre una nueva resolucion DIAN.");
        }
        if (resolution.getCurrentNumber() != null && resolution.getEndNumber() != null
                && resolution.getCurrentNumber() >= resolution.getEndNumber()) {
            throw new IllegalStateException(
                    "La resolucion de facturacion agoto su rango. Registre una nueva resolucion DIAN.");
        }

        String technicalKey = resolution.getTechnicalKey() != null ? resolution.getTechnicalKey() : "";

        String nitEmisor = Optional.ofNullable(systemInfoService.getCompanyNit()).orElse("900000000");
        String tipoAmb = CufeCalculator.TIPO_AMB_PRUEBAS;

        String cufe = cufeCalculator.calculate(invoice, technicalKey, nitEmisor, tipoAmb);
        String xml;
        try {
            xml = buildXml(invoice, cufe, resolution, nitEmisor);
        } catch (Exception e) {
            log.error("Error generando XML UBL para factura {}", invoice.getInvoiceNumber(), e);
            throw new IllegalStateException("Error generando el XML UBL 2.1 de la factura");
        }

        String xmlBase64 = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));

        invoice.setCufe(cufe);
        if (resolution != null) {
            invoice.setResolutionNumber(resolution.getResolutionNumber());
        }
        salesInvoiceRepository.save(invoice);

        DianInvoiceSubmission submission = DianInvoiceSubmission.builder()
                .salesInvoiceId(invoice.getId())
                .cufe(cufe)
                .xmlContent(xml)
                .xmlBase64(xmlBase64)
                .submissionStatus(DianSubmissionStatus.PENDING)
                .trackId(UUID.randomUUID().toString())
                .attemptCount(0)
                .build();
        submission = submissionRepository.save(submission);

        auditPublisher.publishCreate(AuditModule.AR, "DianSubmission", submission.getId(),
                "XML UBL 2.1 generado para factura " + invoice.getInvoiceNumber()
                        + " (CUFE: " + cufe.substring(0, Math.min(16, cufe.length())) + "...)");

        log.info("XML UBL generado para factura {} con CUFE {}", invoice.getInvoiceNumber(), cufe);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("XML UBL 2.1 generado correctamente"), Optional.of(submission)));
    }

    /**
     * Construye el XML UBL 2.1 con las secciones principales de una factura.
     */
    public String buildXml(SalesInvoice invoice, String cufe, DianResolution resolution,
                            String nitEmisor) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        Element root = doc.createElementNS(UBL_NS, "Invoice");
        root.setAttribute("xmlns:cbc", CBC_NS);
        root.setAttribute("xmlns:cac", CAC_NS);
        doc.appendChild(root);

        appendCbc(doc, root, "UBLVersionID", "UBL 2.1");
        appendCbc(doc, root, "CustomizationID", "10");
        appendCbc(doc, root, "ProfileID", "DIAN 2.1: Factura Electronica de Venta");
        appendCbc(doc, root, "ProfileExecutionID",
                CufeCalculator.TIPO_AMB_PRUEBAS.equals("2") ? "2" : "1");
        appendCbc(doc, root, "ID", invoice.getInvoiceNumber());
        appendCbc(doc, root, "UUID", cufe);
        appendCbc(doc, root, "IssueDate",
                invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().format(FECHA) : "");
        appendCbc(doc, root, "IssueTime",
                invoice.getCreatedAt() != null ? invoice.getCreatedAt().toLocalTime().format(HORA) : "00:00:00");
        appendCbc(doc, root, "InvoiceTypeCode", "01");
        appendCbc(doc, root, "DocumentCurrencyCode",
                invoice.getCurrency() != null && invoice.getCurrency().getIsoCode() != null
                        ? invoice.getCurrency().getIsoCode() : "COP");

        // Resolucion DIAN
        if (resolution != null) {
            Element authOrderRef = doc.createElementNS(CAC_NS, "cac:OrderReference");
            appendCbc(doc, authOrderRef, "ID", resolution.getResolutionNumber());
            root.appendChild(authOrderRef);
        }

        // Emisor
        Element supplier = doc.createElementNS(CAC_NS, "cac:AccountingSupplierParty");
        Element supplierParty = doc.createElementNS(CAC_NS, "cac:Party");
        Element supplierName = doc.createElementNS(CAC_NS, "cac:PartyName");
        appendCbc(doc, supplierName, "Name",
                Optional.ofNullable(systemInfoService.getCompanyName()).orElse("Empresa"));
        supplierParty.appendChild(supplierName);
        Element supplierTaxScheme = doc.createElementNS(CAC_NS, "cac:PartyTaxScheme");
        appendCbc(doc, supplierTaxScheme, "RegistrationName",
                Optional.ofNullable(systemInfoService.getCompanyName()).orElse("Empresa"));
        appendCbc(doc, supplierTaxScheme, "CompanyID", nitEmisor);
        supplierParty.appendChild(supplierTaxScheme);
        supplier.appendChild(supplierParty);
        root.appendChild(supplier);

        // Cliente
        Element customer = doc.createElementNS(CAC_NS, "cac:AccountingCustomerParty");
        Element customerParty = doc.createElementNS(CAC_NS, "cac:Party");
        if (invoice.getThirdParty() != null) {
            Element customerName = doc.createElementNS(CAC_NS, "cac:PartyName");
            appendCbc(doc, customerName, "Name",
                    Optional.ofNullable(invoice.getThirdParty().getBusinessName()).orElse(""));
            customerParty.appendChild(customerName);
            Element customerTaxScheme = doc.createElementNS(CAC_NS, "cac:PartyTaxScheme");
            appendCbc(doc, customerTaxScheme, "RegistrationName",
                    Optional.ofNullable(invoice.getThirdParty().getBusinessName()).orElse(""));
            appendCbc(doc, customerTaxScheme, "CompanyID",
                    Optional.ofNullable(invoice.getThirdParty().getNit()).orElse(""));
            customerParty.appendChild(customerTaxScheme);
        }
        customer.appendChild(customerParty);
        root.appendChild(customer);

        // Tax Total
        Element taxTotal = doc.createElementNS(CAC_NS, "cac:TaxTotal");
        appendCbc(doc, taxTotal, "TaxAmount", money(invoice.getTotalTax()));
        root.appendChild(taxTotal);

        // LegalMonetaryTotal
        Element lmt = doc.createElementNS(CAC_NS, "cac:LegalMonetaryTotal");
        appendCbc(doc, lmt, "LineExtensionAmount", money(invoice.getSubtotal()));
        appendCbc(doc, lmt, "TaxExclusiveAmount", money(invoice.getSubtotal()));
        appendCbc(doc, lmt, "TaxInclusiveAmount",
                money(orZero(invoice.getSubtotal()).add(orZero(invoice.getTotalTax()))));
        appendCbc(doc, lmt, "PayableAmount", money(invoice.getTotalAmount()));
        root.appendChild(lmt);

        // InvoiceLines
        int idx = 1;
        if (invoice.getLines() != null) {
            for (SalesInvoiceLine line : invoice.getLines()) {
                Element invLine = doc.createElementNS(CAC_NS, "cac:InvoiceLine");
                appendCbc(doc, invLine, "ID", String.valueOf(idx++));
                appendCbc(doc, invLine, "InvoicedQuantity",
                        line.getQuantity() != null ? line.getQuantity().toPlainString() : "0");
                appendCbc(doc, invLine, "LineExtensionAmount", money(line.getSubtotal()));
                Element item = doc.createElementNS(CAC_NS, "cac:Item");
                appendCbc(doc, item, "Description",
                        Optional.ofNullable(line.getDescription()).orElse("Item"));
                invLine.appendChild(item);
                Element price = doc.createElementNS(CAC_NS, "cac:Price");
                appendCbc(doc, price, "PriceAmount", money(line.getUnitPrice()));
                invLine.appendChild(price);
                root.appendChild(invLine);
            }
        }

        return documentToString(doc);
    }

    /**
     * Busca una resolucion activa compatible con la factura. Si la factura tiene
     * un resolutionNumber asignado se prioriza esa; en caso contrario se busca
     * por prefijo "FV" y fecha.
     */
    /**
     * HU-AR-14 E2: si la factura tiene resolutionNumber, busca exacta.
     * Si no, busca la activa+vigente para FV. Si no hay vigente, retorna la
     * mas reciente del prefix (aunque vencida/agotada) para que la validacion
     * de generateXml pueda emitir el mensaje literal de la HU.
     */
    private DianResolution findResolutionForInvoice(SalesInvoice invoice) {
        if (invoice.getResolutionNumber() != null && !invoice.getResolutionNumber().isBlank()) {
            return resolutionRepository.findAll().stream()
                    .filter(r -> r.getDeletedAt() == null
                            && invoice.getResolutionNumber().equals(r.getResolutionNumber()))
                    .findFirst().orElse(null);
        }
        DianResolution active = resolutionRepository
                .findActiveByPrefixAndDate("FV", invoice.getInvoiceDate())
                .stream().findFirst().orElse(null);
        if (active != null) return active;
        // Fallback: ultima resolucion del prefix (aunque vencida) para que la
        // validacion siguiente devuelva el mensaje literal correcto.
        return resolutionRepository.findAll().stream()
                .filter(r -> r.getDeletedAt() == null && "FV".equals(r.getPrefix()))
                .sorted((a, b) -> b.getId().compareTo(a.getId()))
                .findFirst().orElse(null);
    }

    private void appendCbc(Document doc, Element parent, String localName, String value) {
        Element e = doc.createElementNS(CBC_NS, "cbc:" + localName);
        e.setTextContent(value != null ? value : "");
        parent.appendChild(e);
    }

    private String money(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal orZero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private String documentToString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
}
