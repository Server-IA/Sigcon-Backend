package com.sigcon.backend.accounts_receivable.dian.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceLine;

import lombok.extern.slf4j.Slf4j;

/**
 * Calculadora del Codigo Unico de Factura Electronica (CUFE).
 * Implementa el algoritmo definido por la DIAN en la Resolucion 0042 de 2020
 * y el Anexo Tecnico de facturacion electronica: se concatenan los campos
 * obligatorios y se aplica funcion hash SHA-384.
 */
@Component
@Slf4j
public class CufeCalculator {

    /** Ambiente por defecto: 2 = pruebas. En produccion se debe usar 1. */
    public static final String TIPO_AMB_PRUEBAS = "2";

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HORA_FMT = DateTimeFormatter.ofPattern("HH:mm:ss-05:00");

    /**
     * Calcula el CUFE concatenando:
     * NumFac + FecFac + HoraFac + ValFac + CodImp1 + ValImp1 + CodImp2 + ValImp2
     * + CodImp3 + ValImp3 + NitOFE + NumAdq + ClTec + TipoAmb
     * y aplicando SHA-384 sobre el resultado.
     *
     * @param invoice      factura de venta emitida
     * @param technicalKey clave tecnica entregada por la DIAN para la resolucion
     * @param nitOFE       NIT del obligado a facturar (emisor)
     * @param tipoAmb      "1" produccion o "2" pruebas
     * @return CUFE en hexadecimal (96 caracteres)
     */
    public String calculate(SalesInvoice invoice, String technicalKey, String nitOFE, String tipoAmb) {
        String numFac = safe(invoice.getInvoiceNumber());
        String fecFac = invoice.getInvoiceDate() != null
                ? invoice.getInvoiceDate().format(FECHA_FMT) : "";
        String horaFac = invoice.getCreatedAt() != null
                ? invoice.getCreatedAt().toLocalTime().format(HORA_FMT) : "00:00:00-05:00";
        String valFac = money(invoice.getSubtotal());

        // Impuestos: se separan por codigo. Codigos DIAN: 01=IVA, 02=IC, 03=ICA.
        BigDecimal iva = totalTaxByCode(invoice.getLines(), "01");
        BigDecimal ic = totalTaxByCode(invoice.getLines(), "02");
        BigDecimal ica = totalTaxByCode(invoice.getLines(), "03");
        // Para simplificar se considera totalTax de la factura como IVA si no hay desglose.
        if (iva.compareTo(BigDecimal.ZERO) == 0 && invoice.getTotalTax() != null) {
            iva = invoice.getTotalTax();
        }

        String codImp1 = "01"; String valImp1 = money(iva);
        String codImp2 = "04"; String valImp2 = money(ic);
        String codImp3 = "03"; String valImp3 = money(ica);

        String numAdq = invoice.getThirdParty() != null
                ? safe(invoice.getThirdParty().getNit()) : "";
        String clTec = safe(technicalKey);
        String tAmb = safe(tipoAmb != null ? tipoAmb : TIPO_AMB_PRUEBAS);

        String concat = numFac + fecFac + horaFac + valFac
                + codImp1 + valImp1
                + codImp2 + valImp2
                + codImp3 + valImp3
                + safe(nitOFE) + numAdq + clTec + tAmb;

        return sha384Hex(concat);
    }

    /**
     * Genera SHA-384 en hexadecimal minusculo.
     */
    public String sha384Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-384");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Algoritmo SHA-384 no disponible", e);
            throw new IllegalStateException("No se pudo calcular el CUFE: algoritmo SHA-384 no disponible");
        }
    }

    private BigDecimal totalTaxByCode(List<SalesInvoiceLine> lines, String code) {
        // Placeholder: el modelo actual no desglosa por codigo, retornamos 0.
        return BigDecimal.ZERO;
    }

    private String money(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
