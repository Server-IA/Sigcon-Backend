package com.sigcon.backend.audit.domain.service;

import java.util.Map;

/**
 * QA Bloque AU (2026-05-25): etiquetas en espanol de los enums de auditoria para
 * los REPORTES exportados (CSV / Excel / PDF). Antes los archivos descargados
 * mostraban los codigos crudos en ingles (VIEW, LOW, CREATE, AuditLog...), lo
 * que era inconsistente con la pantalla, que ya los muestra en espanol.
 *
 * <p>Se usan los MISMOS textos que el helper del frontend
 * (utils/auditLabels.jsx) para que la pantalla y el archivo exportado coincidan.
 * Sin tildes a proposito, igual que el resto de literales del backend, para no
 * depender de la codificacion de fuente.</p>
 */
public final class AuditLabels {

    private AuditLabels() { }

    private static final Map<String, String> ACTIONS = Map.of(
            "CREATE", "Creacion",
            "UPDATE", "Actualizacion",
            "DELETE", "Eliminacion",
            "LOGIN", "Inicio de sesion",
            "LOGOUT", "Cierre de sesion",
            "EXPORT", "Exportacion",
            "VIEW", "Vista"
    );

    private static final Map<String, String> SEVERITIES = Map.of(
            "LOW", "Baja",
            "MEDIUM", "Media",
            "HIGH", "Alta",
            "CRITICAL", "Critica"
    );

    private static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("PA", "Parametrizacion"),
            Map.entry("TER", "Terceros"),
            Map.entry("CFG", "Listas Contables"),
            Map.entry("ACT", "Activos Fijos"),
            Map.entry("AP", "Cuentas por Pagar"),
            Map.entry("AR", "Cuentas por Cobrar"),
            Map.entry("BNK", "Bancos y Cajas"),
            Map.entry("CG", "Contabilidad General"),
            Map.entry("NOM", "Nomina"),
            Map.entry("INT", "Integracion AAEF"),
            Map.entry("AU", "Auditoria")
    );

    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("User", "Usuario"), Map.entry("Role", "Rol"), Map.entry("Module", "Modulo"),
            Map.entry("Menu", "Menu"), Map.entry("Parameter", "Parametro"),
            Map.entry("ReportTemplate", "Plantilla de reporte"), Map.entry("ReportType", "Tipo de reporte"),
            Map.entry("SystemWithholdingAssignment", "Retencion del sistema"),
            Map.entry("ThirdParty", "Tercero"), Map.entry("CommercialData", "Datos comerciales"),
            Map.entry("EclSegmentation", "Segmentacion ECL"), Map.entry("ThirdPartyBankAccount", "Cuenta bancaria de tercero"),
            Map.entry("AccountingAccount", "Cuenta contable"), Map.entry("ChartOfAccount", "Cuenta PUC"),
            Map.entry("CostCenter", "Centro de costo"), Map.entry("ExchangeRate", "Tasa de cambio"),
            Map.entry("RuleTax", "Regla tributaria"), Map.entry("DepretationRule", "Regla de depreciacion"),
            Map.entry("CurrencyType", "Tipo de moneda"),
            Map.entry("Asset", "Activo fijo"), Map.entry("AssetDisposal", "Baja de activo"),
            Map.entry("NiifVerification", "Verificacion NIIF"),
            Map.entry("Invoice", "Factura de compra"), Map.entry("ApPayment", "Pago a proveedor"),
            Map.entry("ApAdvance", "Anticipo a proveedor"), Map.entry("ApNote", "Nota credito/debito (compra)"),
            Map.entry("PurchaseOrder", "Orden de compra"), Map.entry("GoodsReceipt", "Recepcion"),
            Map.entry("InvoiceAttachment", "Soporte de factura"),
            Map.entry("SalesInvoice", "Factura de venta"), Map.entry("ArPayment", "Cobro"),
            Map.entry("ArAdvance", "Anticipo de cliente"), Map.entry("ArNote", "Nota credito/debito (venta)"),
            Map.entry("DianResolution", "Resolucion DIAN"), Map.entry("SalesInvoiceAttachment", "Soporte de factura de venta"),
            Map.entry("Bank", "Banco"), Map.entry("BankAccount", "Cuenta bancaria"),
            Map.entry("BankBranch", "Sucursal bancaria"), Map.entry("Checkbook", "Chequera"),
            Map.entry("Check", "Cheque"), Map.entry("Cash", "Caja"), Map.entry("CashAudit", "Arqueo de caja"),
            Map.entry("FinancialMovement", "Movimiento financiero"),
            Map.entry("BankReconciliationSession", "Conciliacion bancaria"),
            Map.entry("CashFlowProjection", "Proyeccion de flujo"),
            Map.entry("JournalEntry", "Comprobante contable"), Map.entry("AccountingPeriod", "Periodo contable"),
            Map.entry("JournalEntrySupport", "Soporte de comprobante"), Map.entry("VoucherSeriesConfig", "Serie de consecutivos"),
            Map.entry("ClosingEntry", "Asiento de cierre"),
            Map.entry("Employee", "Empleado"), Map.entry("PayrollConcept", "Concepto de nomina"),
            Map.entry("PayrollReceipt", "Recibo de nomina"), Map.entry("PayrollLine", "Linea de nomina"),
            Map.entry("BenefitLiquidation", "Liquidacion de prestaciones"),
            Map.entry("IntegrationBatch", "Lote de integracion"), Map.entry("IntegrationTransfer", "Transferencia de integracion"),
            Map.entry("AuditLog", "Log de auditoria"), Map.entry("AuditRiskRule", "Regla de riesgo"),
            Map.entry("AuditRetentionPolicy", "Politica de retencion"), Map.entry("AuditPurgeRecord", "Registro de purga"),
            Map.entry("AuditFinding", "Hallazgo de auditoria"),
            Map.entry("AccessDenied", "Acceso denegado"), Map.entry("FinancialStatement", "Estado financiero")
    );

    public static String action(String code)   { return code == null ? "" : ACTIONS.getOrDefault(code, code); }
    public static String severity(String code)  { return code == null ? "" : SEVERITIES.getOrDefault(code, code); }
    public static String module(String code)    { return code == null ? "" : MODULES.getOrDefault(code, code); }
    public static String entity(String name)    { return name == null ? "" : ENTITIES.getOrDefault(name, name); }
}
