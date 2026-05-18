package com.sigcon.backend.integration.domain.service;

import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceLineRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceRequest;
import com.sigcon.backend.integration.application.AaefInvoiceDTO;
import com.sigcon.backend.invoices.application.InvoiceFCRequestDTO;
import com.sigcon.backend.invoices.application.LineInvoiceRequestDTO;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * HU-INT-RF-04: Mapper de facturas AAEF a DTOs internos de SIGCON.
 *
 * <p>Reglas de mapeo:
 * <ul>
 *   <li>Type.Code=01 (Venta) → {@link CreateSalesInvoiceRequest}</li>
 *   <li>Type.Code=02 (Compra) → (en Paso posterior, con InvoiceService)</li>
 *   <li>Type.Code=03 (NC) / 04 (ND) → Pull+Diff (Fase 4)</li>
 * </ul>
 *
 * <p>Validaciones aplicadas:
 * <ul>
 *   <li>Status debe ser ACTIVE | PAID | CANCELLED | PARTIAL.</li>
 *   <li>TotalPayment debe coincidir con Subtotal + TotalVAT - Retenciones - Descuentos.</li>
 *   <li>Cada linea debe tener LineType (no vacio).</li>
 *   <li>Tercero se resuelve via {@link ThirdPartyResolver} (auto-crea si no existe).</li>
 * </ul>
 *
 * <p>NOTA: por ahora NO se mapean los taxRuleIds porque requiere resolver reglas
 * tributarias (CFG) por codigo o porcentaje. El motor interno calculara el JE con
 * las cuentas PUC reales via AccountMappingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AaefInvoiceMapper {

    // QA Bloque PA Bug 70 (HU-INT-13, 2026-05-09): AgroFusion comunico que
    // los unicos status validos para facturas y nominas de Sigma/Disriego son
    // PAID y PENDING. Antes aceptabamos ACTIVE/CANCELLED/PARTIAL pero AgroFusion
    // no los emite — generaban INVALID_STATUS innecesarios. Mantenemos validacion
    // estricta para detectar payloads malformados temprano.
    private static final Set<String> VALID_STATUSES =
            Set.of("PAID", "PENDING");

    /** AgroFusion feedback v1.1 (2026-04-28): set completo de Type.Code validos. */
    public static final String SALES_TYPE_CODE = "01";          // Factura venta
    public static final String PURCHASE_TYPE_CODE = "02";       // Factura compra
    public static final String FEES_TYPE_CODE = "03";           // Honorarios (NUEVO)
    public static final String CREDIT_NOTE_TYPE_CODE = "04";    // Nota credito (movido de 03)
    public static final String DEBIT_NOTE_TYPE_CODE = "05";     // Nota debito (movido de 04)

    public static final Set<String> VALID_TYPE_CODES =
            Set.of(SALES_TYPE_CODE, PURCHASE_TYPE_CODE,
                   FEES_TYPE_CODE, CREDIT_NOTE_TYPE_CODE, DEBIT_NOTE_TYPE_CODE);

    /**
     * QA Bloque AX (HU-INT-13 tolerancia Type.Code, 2026-05-16): AgroFusion
     * envia ocasionalmente alias literales ("INVOICE", "VENTA", "COMPRA")
     * en lugar de los codes 01..05. Para evitar rechazos masivos por una
     * variacion menor del cliente, normalizamos los alias hacia el code
     * correspondiente ANTES de validar. La normalizacion sigue dos reglas:
     * <ol>
     *   <li>Si el code crudo coincide con un alias conocido (case-insensitive),
     *       se mapea a su code 01..05 directo.</li>
     *   <li>Si el code es ambiguo (ej. "INVOICE") y existe {@code Name},
     *       se inspecciona el name buscando palabras clave: "venta"→01,
     *       "compra"→02, "honorarios"→03, "credito"→04, "debito"→05.</li>
     * </ol>
     * Si ningun mapeo aplica, se respeta el code crudo y se valida; si no
     * pertenece a {@link #VALID_TYPE_CODES} se lanza
     * {@link AaefMappingException#INVALID_TYPE_CODE}.
     */
    private static final java.util.Map<String, String> TYPE_CODE_ALIASES =
            java.util.Map.ofEntries(
                    java.util.Map.entry("VENTA",              SALES_TYPE_CODE),
                    java.util.Map.entry("SALES",              SALES_TYPE_CODE),
                    java.util.Map.entry("FACTURA_VENTA",      SALES_TYPE_CODE),
                    java.util.Map.entry("COMPRA",             PURCHASE_TYPE_CODE),
                    java.util.Map.entry("PURCHASE",           PURCHASE_TYPE_CODE),
                    java.util.Map.entry("FACTURA_COMPRA",     PURCHASE_TYPE_CODE),
                    java.util.Map.entry("HONORARIOS",         FEES_TYPE_CODE),
                    java.util.Map.entry("FEES",               FEES_TYPE_CODE),
                    java.util.Map.entry("NC",                 CREDIT_NOTE_TYPE_CODE),
                    java.util.Map.entry("NOTA_CREDITO",       CREDIT_NOTE_TYPE_CODE),
                    java.util.Map.entry("CREDIT_NOTE",        CREDIT_NOTE_TYPE_CODE),
                    java.util.Map.entry("ND",                 DEBIT_NOTE_TYPE_CODE),
                    java.util.Map.entry("NOTA_DEBITO",        DEBIT_NOTE_TYPE_CODE),
                    java.util.Map.entry("DEBIT_NOTE",         DEBIT_NOTE_TYPE_CODE)
            );

    private final ThirdPartyResolver thirdPartyResolver;
    private final AccountMappingService accountMappingService;
    private final PaymentFormRepository paymentFormRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository accountingAccountRepository;

    /**
     * Valida y mapea una factura AAEF de venta (Type=01) a CreateSalesInvoiceRequest.
     *
     * @throws AaefMappingException si la factura no cumple reglas de negocio
     */
    public CreateSalesInvoiceRequest toSalesInvoiceRequest(AaefInvoiceDTO invoice) {
        validateCommon(invoice);

        AaefInvoiceDTO.Header header = invoice.getHeader();
        AaefInvoiceDTO.ThirdParty tp = invoice.getThirdParty();
        AaefInvoiceDTO.Totals totals = invoice.getTotals();

        // Resolver o auto-crear tercero
        ThirdParty resolved = thirdPartyResolver.findOrCreate(
                tp.getNit(), tp.getDv(), tp.getName());

        // Validar cuadre de totales: TotalPayment = Subtotal + TotalVAT - Retenciones - Descuentos
        BigDecimal subtotal = safe(totals.getSubtotal());
        BigDecimal vat = safe(totals.getTotalVAT());
        BigDecimal withholdings = safe(totals.getTotalWithholdings());
        BigDecimal discounts = safe(totals.getTotalDiscounts());
        BigDecimal expected = subtotal.add(vat).subtract(withholdings).subtract(discounts);
        BigDecimal actual = safe(totals.getTotalPayment());

        // Spec RF-INT-12: tolerancia ±$0.01 por redondeos (la spec lo permite
        // explicitamente; antes haciamos compareTo() == 0 strict y rechazabamos
        // diferencias de centavos legitimas).
        if (expected.subtract(actual).abs().compareTo(new BigDecimal("0.01")) > 0) {
            // QA Bloque AT (HU-INT-RF-04, 2026-05-13): mensaje explicito con
            // diferencia exacta y recomendacion de accion para AgroFusion.
            BigDecimal diff = actual.subtract(expected);
            String docId = header != null && header.getDocumentId() != null
                    ? header.getDocumentId() : "(sin DocumentId)";
            String accion = diff.signum() > 0
                    ? "TotalPayment es MAYOR que la suma de las lineas. Verifique si falta agregar lineas o si TotalPayment esta mal calculado."
                    : "TotalPayment es MENOR que la suma de las lineas. Revise descuentos o retenciones no declarados.";
            throw new AaefMappingException(
                    AaefMappingException.AMOUNT_MISMATCH,
                    "La factura " + docId + " tiene descuadre contable. "
                            + "TotalPayment=" + actual + " no coincide con "
                            + "Subtotal(" + subtotal + ") + TotalVAT(" + vat + ") "
                            + "- TotalWithholdings(" + withholdings + ") - TotalDiscounts(" + discounts + ") = " + expected + ". "
                            + "Diferencia: " + diff.abs() + " COP (tolerancia maxima: $0.01). "
                            + accion);
        }

        // Mapear lineas
        List<CreateSalesInvoiceLineRequest> lines = new ArrayList<>();
        if (invoice.getLines() != null) {
            for (AaefInvoiceDTO.Line l : invoice.getLines()) {
                if (l.getLineType() == null || l.getLineType().trim().isEmpty()) {
                    throw new AaefMappingException(
                            AaefMappingException.MISSING_LINE_TYPE,
                            "Linea '" + (l.getDescription() != null ? l.getDescription() : l.getCode())
                                    + "' no tiene LineType");
                }
                // AAEF v1.1: resolver override de accounting_account si viene en la linea
                Long[] accOverride = resolveAccountOverride(l);
                // AAEF QA Bloque BJ (HU-INT-RF-04 E1, 2026-05-18): calcular IVA y
                // retencion desde Tax[] de la linea. Esto pasa por override al
                // SalesInvoiceService para que NO invoque SalesTaxEngine (que
                // requiere taxRuleIds locales del tenant). Antes la factura
                // AAEF con TotalVAT $19k quedaba con total_tax=0.
                BigDecimal[] taxSplit = splitLineTaxes(l);
                lines.add(CreateSalesInvoiceLineRequest.builder()
                        .description(
                                l.getDescription() != null
                                        ? l.getDescription()
                                        : (l.getName() != null ? l.getName() : l.getCode()))
                        .quantity(safe(l.getQuantity()))
                        .unitPrice(safe(l.getUnitPrice()))
                        .discount(BigDecimal.ZERO)
                        .accountDebitOverride(accOverride[0])
                        .accountCreditOverride(accOverride[1])
                        .taxAmountOverride(taxSplit[0])
                        .withholdingAmountOverride(taxSplit[1])
                        .build());
            }
        }

        // Fallback (HU-INT-RF-04 E1): si las lineas no traen Tax[] pero
        // Totals.TotalVAT > 0, distribuir el IVA total entre las lineas
        // proporcionalmente al subtotal de cada una. Igual para retenciones.
        // Esto cubre el caso donde AgroFusion solo manda los totales agregados
        // (sin Tax[] por linea) y el subtotal cuadra con la suma de lineas.
        applyAggregateTotalsFallback(lines, totals);

        if (lines.isEmpty()) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura debe tener al menos una linea");
        }

        // Construir request
        return CreateSalesInvoiceRequest.builder()
                .thirdPartyId(resolved.getId())
                .invoiceDate(header.getIssueDate())
                .dueDate(header.getDueDate() != null ? header.getDueDate() : header.getIssueDate())
                .resolutionNumber(resolveDocumentReference(header))
                .notes("Importado desde AAEF - DocumentId: " + header.getDocumentId())
                .lines(lines)
                .build();
    }

    /**
     * Arma el identificador externo de la factura para persistir en los campos
     * {@code supplier_invoice_number}/{@code resolution_invoice}. AgroFusion puede
     * enviar los datos de tres formas:
     * <ul>
     *   <li>{@code Prefix + Serial} completos (ej. {@code FCE + 1234} -> "FCE-1234")</li>
     *   <li>Solo uno de los dos (se usa el disponible)</li>
     *   <li>Ninguno (common - usar {@code DocumentId} como identificador principal)</li>
     * </ul>
     * Asi evitamos persistir literal {@code "null-null"} cuando el Header no trae
     * Prefix/Serial, como ocurria antes.
     */
    private String resolveDocumentReference(AaefInvoiceDTO.Header header) {
        String prefix = header.getPrefix();
        String serial = header.getSerial();
        boolean hasPrefix = prefix != null && !prefix.trim().isEmpty();
        boolean hasSerial = serial != null && !serial.trim().isEmpty();
        if (hasPrefix && hasSerial) return prefix + "-" + serial;
        if (hasPrefix)              return prefix;
        if (hasSerial)              return serial;
        return header.getDocumentId();
    }

    /**
     * HU-AP-01 E5 / HU-INT-RF-04 E2: Valida y mapea una factura AAEF de compra
     * (Type=02) a {@link InvoiceFCRequestDTO} usado por {@code InvoiceService}.
     *
     * <p>Particularidades respecto a ventas (Type=01):
     * <ul>
     *   <li>Cada linea usa como cuenta contable default {@code AP_COMPRAS_DEFAULT}
     *       (PUC 5135 Servicios). El contador puede reclasificar manualmente
     *       despues si corresponde.</li>
     *   <li>No se aplican reglas tributarias AAEF (taxRulesIds queda vacio):
     *       los totales IVA/retenciones ya vienen calculados desde AgroFusion
     *       y se materializan en el JE via {@code generateJournalEntry} del
     *       {@code InvoiceService}.</li>
     *   <li>La forma de pago se resuelve: 'CASH' si Status=PAID, credito en caso
     *       contrario (se toma la primera forma de pago a credito disponible).</li>
     * </ul>
     *
     * @return {@link InvoiceFCRequestDTO} listo para pasar a {@code InvoiceService.createInvoice}
     * @throws AaefMappingException si la factura no cumple reglas de negocio
     */
    public InvoiceFCRequestDTO toPurchaseInvoiceRequest(AaefInvoiceDTO invoice) {
        validateCommon(invoice);

        AaefInvoiceDTO.Header header = invoice.getHeader();
        AaefInvoiceDTO.ThirdParty tp = invoice.getThirdParty();
        AaefInvoiceDTO.Totals totals = invoice.getTotals();

        // Resolver o auto-crear tercero (HU-INT-RF-04 E6)
        ThirdParty resolved = thirdPartyResolver.findOrCreate(
                tp.getNit(), tp.getDv(), tp.getName());

        // Validar cuadre de totales: TotalPayment = Subtotal + TotalVAT - Retenciones - Descuentos
        BigDecimal subtotal = safe(totals.getSubtotal());
        BigDecimal vat = safe(totals.getTotalVAT());
        BigDecimal withholdings = safe(totals.getTotalWithholdings());
        BigDecimal discounts = safe(totals.getTotalDiscounts());
        BigDecimal expected = subtotal.add(vat).subtract(withholdings).subtract(discounts);
        BigDecimal actual = safe(totals.getTotalPayment());

        // Spec RF-INT-12: tolerancia ±$0.01 por redondeos (la spec lo permite
        // explicitamente; antes haciamos compareTo() == 0 strict y rechazabamos
        // diferencias de centavos legitimas).
        if (expected.subtract(actual).abs().compareTo(new BigDecimal("0.01")) > 0) {
            // QA Bloque AT (HU-INT-RF-04, 2026-05-13): mensaje explicito con
            // contexto suficiente para que AgroFusion lo muestre directo al usuario.
            BigDecimal diff = actual.subtract(expected);
            String docId = header != null && header.getDocumentId() != null
                    ? header.getDocumentId() : "(sin DocumentId)";
            String accion = diff.signum() > 0
                    ? "TotalPayment es MAYOR que la suma de las lineas. Verifique si falta agregar lineas o si TotalPayment esta mal calculado."
                    : "TotalPayment es MENOR que la suma de las lineas. Revise descuentos o retenciones no declarados.";
            throw new AaefMappingException(
                    AaefMappingException.AMOUNT_MISMATCH,
                    "La factura de compra " + docId + " tiene descuadre contable. "
                            + "TotalPayment=" + actual + " no coincide con "
                            + "Subtotal(" + subtotal + ") + TotalVAT(" + vat + ") "
                            + "- TotalWithholdings(" + withholdings + ") - TotalDiscounts(" + discounts + ") = " + expected + ". "
                            + "Diferencia: " + diff.abs() + " COP (tolerancia maxima: $0.01). "
                            + accion);
        }

        // Resolver cuenta contable default (AP_COMPRAS_DEFAULT -> PUC 5135 Servicios)
        Long defaultDebitAccountId =
                accountMappingService.resolveOrThrow(AccountingConcept.AP_COMPRAS_DEFAULT);

        // Mapear lineas con cuenta contable default
        List<LineInvoiceRequestDTO> lines = new ArrayList<>();
        if (invoice.getLines() != null) {
            for (AaefInvoiceDTO.Line l : invoice.getLines()) {
                if (l.getLineType() == null || l.getLineType().trim().isEmpty()) {
                    throw new AaefMappingException(
                            AaefMappingException.MISSING_LINE_TYPE,
                            "Linea '" + (l.getDescription() != null ? l.getDescription() : l.getCode())
                                    + "' no tiene LineType");
                }
                BigDecimal qty = safe(l.getQuantity());
                BigDecimal unit = safe(l.getUnitPrice());
                // AAEF v1.1 (2026-04-28): override accounting_account[0] = cuenta debito.
                // Si la linea trae accounting_account, sobreescribe el default
                // AP_COMPRAS_DEFAULT (PUC 5135). El indice [1] (credito) NO se aplica
                // aqui porque el motor AP usa AP_PROVEEDORES (PUC 2205) por convencion
                // contable. Para casos especiales sera futuro work item.
                Long[] accOverride = resolveAccountOverride(l);
                Long debitAccount = (accOverride[0] != null) ? accOverride[0] : defaultDebitAccountId;
                lines.add(LineInvoiceRequestDTO.builder()
                        .accountingAccountId(debitAccount)
                        .description(
                                l.getDescription() != null
                                        ? l.getDescription()
                                        : (l.getName() != null ? l.getName() : l.getCode()))
                        .quantity(qty.doubleValue())
                        .price(unit.doubleValue())
                        .taxRulesIds(Collections.emptyList())
                        .build());
            }
        }

        if (lines.isEmpty()) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura debe tener al menos una linea");
        }

        // Forma de pago: si esta PAID -> CASH (isContado=true), sino primer credito disponible
        boolean isPaid = "PAID".equalsIgnoreCase(header.getStatus());
        Long paymentFormId = resolvePaymentFormId(isPaid);

        LocalDate issue = header.getIssueDate();
        LocalDate due = header.getDueDate() != null ? header.getDueDate() : issue;
        int dueDays = (int) Math.max(0, ChronoUnit.DAYS.between(issue, due));

        String docRef = resolveDocumentReference(header);
        return InvoiceFCRequestDTO.builder()
                .thirdPartyId(resolved.getId())
                .paymentFormId(paymentFormId)
                .invoiceDate(issue)
                .invoiceDueDay(dueDays)
                .resolutionInvoice(docRef)
                .supplierInvoiceNumber(docRef)
                .notes("Importado desde AAEF - DocumentId: " + header.getDocumentId())
                .lineInvoices(lines)
                .build();
    }

    /**
     * Resuelve la primera forma de pago cuyo flag {@code isContado} coincide con
     * el parametro. Si no encuentra, devuelve la primera disponible (fallback).
     */
    private Long resolvePaymentFormId(boolean wantCash) {
        return paymentFormRepository.findAll().stream()
                .filter(pf -> pf.getDeletedAt() == null)
                .filter(pf -> wantCash
                        ? Boolean.TRUE.equals(pf.getIsContado())
                        : !Boolean.TRUE.equals(pf.getIsContado()))
                .map(PaymentForms::getId)
                .findFirst()
                .orElseGet(() -> paymentFormRepository.findAll().stream()
                        .filter(pf -> pf.getDeletedAt() == null)
                        .map(PaymentForms::getId)
                        .findFirst()
                        .orElseThrow(() -> new AaefMappingException(
                                AaefMappingException.MAPPING_ERROR,
                                "No hay formas de pago activas en el sistema")));
    }

    /**
     * Determina si una factura AAEF es de venta (Type=01).
     */
    public boolean isSalesInvoice(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && SALES_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /**
     * Determina si una factura AAEF es de compra (Type=02).
     */
    public boolean isPurchaseInvoice(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && PURCHASE_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /**
     * AAEF v1.1 (2026-04-28): determina si una factura es de honorarios (Type=03).
     * Se procesa en AP con tratamiento tributario especial (retencion en la fuente
     * Art. 383/384 ET via motor existente).
     */
    public boolean isFeesInvoice(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && FEES_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /** AAEF v1.1: Type=04 Nota credito. */
    public boolean isCreditNote(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && CREDIT_NOTE_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /** AAEF v1.1: Type=05 Nota debito. */
    public boolean isDebitNote(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && DEBIT_NOTE_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /**
     * AAEF v1.1 (2026-04-28): valida que Type.Code este en la lista de valores
     * validos. Lanza {@link AaefMappingException#INVALID_TYPE_CODE} si no.
     *
     * @param invoice factura AAEF a validar
     */
    public void validateTypeCode(AaefInvoiceDTO invoice) {
        if (invoice == null || invoice.getHeader() == null
                || invoice.getHeader().getType() == null) {
            throw new AaefMappingException(AaefMappingException.INVALID_TYPE_CODE,
                    "Header.Type es obligatorio");
        }
        // QA Bloque AX (HU-INT-13 tolerancia Type.Code): normalizar alias
        // ANTES de validar. Si el code crudo es un alias conocido, lo
        // mapeamos al code canonico y persistimos el cambio en el DTO para
        // que las llamadas downstream (isPurchaseInvoice, etc.) operen sobre
        // el code normalizado.
        String code = normalizeTypeCode(invoice);
        if (code == null || !VALID_TYPE_CODES.contains(code)) {
            throw new AaefMappingException(AaefMappingException.INVALID_TYPE_CODE,
                    "Header.Type.Code='" + code + "' no es valido. Valores admitidos: "
                    + VALID_TYPE_CODES + " (01=Venta, 02=Compra, 03=Honorarios, 04=NC, 05=ND)");
        }
    }

    /**
     * QA Bloque AX (HU-INT-13 tolerancia Type.Code, 2026-05-16): convierte
     * alias literales a code canonico 01..05 y lo persiste en el DTO.
     *
     * @return code normalizado (puede ser igual al original si ya era valido)
     */
    private String normalizeTypeCode(AaefInvoiceDTO invoice) {
        AaefInvoiceDTO.Type type = invoice.getHeader().getType();
        String raw = type.getCode();
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        // Ya es un code canonico, no normalizar
        if (VALID_TYPE_CODES.contains(trimmed)) {
            return trimmed;
        }
        String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
        // Buscar alias directo
        String mapped = TYPE_CODE_ALIASES.get(upper);
        if (mapped != null) {
            log.info("AAEF Type.Code alias '{}' -> '{}' (Name='{}', DocumentId='{}')",
                    raw, mapped, type.getName(), invoice.getHeader().getDocumentId());
            type.setCode(mapped);
            return mapped;
        }
        // Heuristica: si Name viene, inspeccionar
        String name = type.getName();
        if (name != null) {
            String upperName = name.toUpperCase(java.util.Locale.ROOT);
            String fromName = null;
            if (upperName.contains("VENTA"))             fromName = SALES_TYPE_CODE;
            else if (upperName.contains("COMPRA"))       fromName = PURCHASE_TYPE_CODE;
            else if (upperName.contains("HONORARIO"))    fromName = FEES_TYPE_CODE;
            else if (upperName.contains("CREDITO"))      fromName = CREDIT_NOTE_TYPE_CODE;
            else if (upperName.contains("CRÉDITO"))      fromName = CREDIT_NOTE_TYPE_CODE;
            else if (upperName.contains("DEBITO"))       fromName = DEBIT_NOTE_TYPE_CODE;
            else if (upperName.contains("DÉBITO"))       fromName = DEBIT_NOTE_TYPE_CODE;
            if (fromName != null) {
                log.info("AAEF Type.Code='{}' Name='{}' normalizado a '{}'",
                        raw, name, fromName);
                type.setCode(fromName);
                return fromName;
            }
        }
        return trimmed;
    }

    /**
     * AAEF v1.1 (2026-04-28): resuelve el override de cuentas PUC de una linea.
     *
     * <p>Si {@code accounting_account} viene presente:
     * <ul>
     *   <li>Valida que tenga maximo 2 elementos (lanza {@code INVALID_ACCOUNTING_ACCOUNT}).</li>
     *   <li>Valida que cada codigo PUC exista y este activo en el tenant
     *       (lanza {@code ACCOUNT_NOT_FOUND}).</li>
     * </ul>
     *
     * @param line linea AAEF (puede traer accounting_account null/vacio)
     * @return arreglo {@code [debitAccountId, creditAccountId]}; cualquiera puede
     *         ser {@code null} si no se override
     */
    public Long[] resolveAccountOverride(AaefInvoiceDTO.Line line) {
        Long[] result = new Long[]{null, null};
        if (line == null || line.getAccountingAccount() == null
                || line.getAccountingAccount().isEmpty()) {
            return result;
        }
        java.util.List<String> codes = line.getAccountingAccount();
        if (codes.size() > 2) {
            throw new AaefMappingException(
                    AaefMappingException.INVALID_ACCOUNTING_ACCOUNT,
                    "accounting_account no puede tener mas de 2 elementos. Recibido: "
                    + codes.size() + " (line code=" + line.getCode() + ")");
        }
        for (int i = 0; i < codes.size(); i++) {
            String pucCode = codes.get(i);
            if (pucCode == null || pucCode.isBlank()) {
                throw new AaefMappingException(
                        AaefMappingException.INVALID_ACCOUNTING_ACCOUNT,
                        "accounting_account[" + i + "] esta vacio (line code=" + line.getCode() + ")");
            }
            if (accountingAccountRepository == null) {
                // Si el repo no esta disponible (test unitario), retornamos nulls.
                continue;
            }
            Long tenantId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
            if (tenantId == null) {
                throw new AaefMappingException(
                        AaefMappingException.ACCOUNT_NOT_FOUND,
                        "TenantContext sin companyId al resolver accounting_account");
            }
            Long accId = accountingAccountRepository
                    .findActiveByPucCodeAndCompany(pucCode.trim(), tenantId)
                    .map(a -> a.getId())
                    .orElseThrow(() -> new AaefMappingException(
                            AaefMappingException.ACCOUNT_NOT_FOUND,
                            "Cuenta contable PUC '" + pucCode + "' no existe o esta "
                            + "inactiva en el sistema (line code=" + line.getCode() + ")"));
            result[i] = accId;
        }
        return result;
    }

    // ---------- Validaciones comunes ----------

    private void validateCommon(AaefInvoiceDTO invoice) {
        if (invoice == null || invoice.getHeader() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura AAEF no tiene header");
        }
        AaefInvoiceDTO.Header h = invoice.getHeader();

        // AAEF v1.1 (2026-04-28): validar Type.Code antes que el resto.
        validateTypeCode(invoice);

        if (h.getStatus() == null || !VALID_STATUSES.contains(h.getStatus().toUpperCase())) {
            throw new AaefMappingException(
                    AaefMappingException.INVALID_STATUS,
                    "Status invalido: '" + h.getStatus() + "'. Valores permitidos: "
                            + VALID_STATUSES);
        }

        if (h.getIssueDate() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "IssueDate es obligatoria");
        }

        if (invoice.getThirdParty() == null || invoice.getThirdParty().getNit() == null
                || invoice.getThirdParty().getNit().trim().isEmpty()) {
            // QA Bloque AT (HU-INT-RF-04, 2026-05-13): mensaje explicito con
            // contexto suficiente para que AgroFusion lo muestre directo al
            // usuario sin necesidad de interpretacion adicional.
            String docId = invoice.getHeader() != null && invoice.getHeader().getDocumentId() != null
                    ? invoice.getHeader().getDocumentId() : "(sin DocumentId)";
            String tpName = invoice.getThirdParty() != null && invoice.getThirdParty().getName() != null
                    ? invoice.getThirdParty().getName() : "(sin nombre)";
            throw new AaefMappingException(
                    AaefMappingException.UNKNOWN_THIRD_PARTY,
                    "La factura " + docId + " no se procesa porque el campo ThirdParty.NIT viene vacio. "
                            + "El tercero '" + tpName + "' debe identificarse con su NIT (empresa) o "
                            + "cedula (persona natural). Complete el NIT en el sistema fuente y reenvie el lote.");
        }

        if (invoice.getTotals() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura debe tener Totals");
        }
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * AAEF QA Bloque BJ (HU-INT-RF-04 E1, 2026-05-18): separa los Tax[] de una
     * linea en [taxAmount, withholdingAmount] segun el TaxType.
     *
     * <p>Tipos catalogados:
     * <ul>
     *   <li>IVA, VAT, ICA -> tax (impuesto generado, suma al subtotal)</li>
     *   <li>RTE_FTE, RTE_IVA, RTE_ICA -> withholding (retencion practicada, resta)</li>
     * </ul>
     *
     * @return arreglo {@code [tax, withholding]}, ambos {@code BigDecimal.ZERO} si la
     *         linea no trae {@code Taxes[]} o todos son null
     */
    private BigDecimal[] splitLineTaxes(AaefInvoiceDTO.Line line) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal withholding = BigDecimal.ZERO;
        // Sin Tax[] = sin overrides; permitir que fallback agregue desde Totals.
        if (line == null || line.getTaxes() == null || line.getTaxes().isEmpty()) {
            return new BigDecimal[]{null, null};
        }
        for (AaefInvoiceDTO.Tax t : line.getTaxes()) {
            if (t == null || t.getAmount() == null) continue;
            String type = t.getTaxType() == null ? "" : t.getTaxType().trim().toUpperCase(java.util.Locale.ROOT);
            BigDecimal amt = t.getAmount();
            if (type.equals("IVA") || type.equals("VAT") || type.equals("ICA")) {
                tax = tax.add(amt);
            } else if (type.startsWith("RTE_") || type.equals("RETE_FUENTE")
                    || type.equals("RETE_IVA") || type.equals("RETE_ICA")) {
                withholding = withholding.add(amt);
            } else {
                // Heuristica: si rate baja (<10%) probable retencion
                if (t.getRate() != null && t.getRate().compareTo(new BigDecimal("10")) < 0) {
                    withholding = withholding.add(amt);
                } else {
                    tax = tax.add(amt);
                }
            }
        }
        // Si no se calculo nada via Tax[] pero el flag amount es 0, devolver null para que el
        // motor SalesTaxEngine resuelva via taxRuleIds (comportamiento original).
        if (tax.compareTo(BigDecimal.ZERO) == 0 && withholding.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal[]{null, null};
        }
        return new BigDecimal[]{tax, withholding};
    }

    /**
     * AAEF QA Bloque BJ (HU-INT-RF-04 E1, 2026-05-18): fallback cuando AgroFusion
     * solo manda totales agregados (Totals.TotalVAT > 0) pero las lineas no
     * traen Taxes[] por linea. Distribuye el IVA y la retencion entre las
     * lineas proporcionalmente al subtotal de cada una.
     *
     * <p>Solo se aplica si las lineas TODAVIA no tienen overrides populados
     * (es decir, taxAmountOverride == null Y withholdingAmountOverride == null
     * en TODAS las lineas). Esto evita doble conteo cuando AgroFusion ya envio
     * Tax[] por linea.
     *
     * <p>Si las lineas vienen con Tax[] populados (caso preferido), no se
     * toca nada y el motor respeta los valores por linea.
     */
    private void applyAggregateTotalsFallback(List<CreateSalesInvoiceLineRequest> lines,
                                              AaefInvoiceDTO.Totals totals) {
        if (totals == null || lines == null || lines.isEmpty()) return;
        BigDecimal vat = safe(totals.getTotalVAT());
        BigDecimal wh = safe(totals.getTotalWithholdings());
        if (vat.compareTo(BigDecimal.ZERO) == 0 && wh.compareTo(BigDecimal.ZERO) == 0) return;

        // Si CUALQUIER linea ya tiene override populado, asumimos que Tax[] vino por linea
        boolean anyOverride = lines.stream().anyMatch(l ->
                l.getTaxAmountOverride() != null || l.getWithholdingAmountOverride() != null);
        if (anyOverride) return;

        // Distribuir proporcionalmente al subtotal de cada linea
        BigDecimal sumSubtotal = BigDecimal.ZERO;
        for (CreateSalesInvoiceLineRequest l : lines) {
            BigDecimal q = safe(l.getQuantity());
            BigDecimal u = safe(l.getUnitPrice());
            BigDecimal d = safe(l.getDiscount());
            sumSubtotal = sumSubtotal.add(q.multiply(u).subtract(d));
        }
        if (sumSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            // No se puede distribuir proporcional; asignar todo a la primera linea
            lines.get(0).setTaxAmountOverride(vat);
            lines.get(0).setWithholdingAmountOverride(wh);
            return;
        }
        BigDecimal totalDistVat = BigDecimal.ZERO;
        BigDecimal totalDistWh = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            CreateSalesInvoiceLineRequest l = lines.get(i);
            BigDecimal q = safe(l.getQuantity());
            BigDecimal u = safe(l.getUnitPrice());
            BigDecimal d = safe(l.getDiscount());
            BigDecimal lineSub = q.multiply(u).subtract(d);
            if (i == lines.size() - 1) {
                // Ultima linea: redondear ajustando para que suma exacta
                l.setTaxAmountOverride(vat.subtract(totalDistVat));
                l.setWithholdingAmountOverride(wh.subtract(totalDistWh));
            } else {
                BigDecimal proportion = lineSub.divide(sumSubtotal, 8, java.math.RoundingMode.HALF_UP);
                BigDecimal lineVat = vat.multiply(proportion).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal lineWh = wh.multiply(proportion).setScale(2, java.math.RoundingMode.HALF_UP);
                l.setTaxAmountOverride(lineVat);
                l.setWithholdingAmountOverride(lineWh);
                totalDistVat = totalDistVat.add(lineVat);
                totalDistWh = totalDistWh.add(lineWh);
            }
        }
    }
}
