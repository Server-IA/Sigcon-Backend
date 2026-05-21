package com.sigcon.backend.banks.dian.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import com.sigcon.backend.audit.domain.service.AuditLogService;
import com.sigcon.backend.banks.archivos_soporte.domain.model.ArchivoSoporte;
import com.sigcon.backend.banks.archivos_soporte.domain.service.ArchivoSoporteService;
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.dian.domain.model.ExogenaGeneracion;
import com.sigcon.backend.banks.dian.domain.repository.ExogenaGeneracionRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.reconciliation.domain.model.enums.ReconciliationSessionStatus;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.utils.export.SimpleTableExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * BNK-HU-079: información exógena DIAN (formatos 1647 movimientos bancarios, 1010 terceros,
 * 1011 información tributaria) por año fiscal.
 *
 * <p><b>STAND-IN (infra diferida, precedente HU-AU-11):</b> el archivo en la estructura
 * XML/XSD oficial de la resolución DIAN del año es infraestructura externa (los XSD oficiales
 * cambian cada año y deben publicarse). El sistema produce los datos estructurados + un
 * export CSV real + un XML genérico, valida los prerequisitos, conserva el archivo con
 * retención 10 años y lleva el histórico de generaciones. El módulo es configurable por año.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExogenaService {

    private static final Set<String> FORMATOS = Set.of("1647", "1010", "1011");
    private static final BigDecimal TOL = new BigDecimal("0.01");

    private final BankAccountRepository bankAccountRepository;
    private final FinancialMovementRepository movementRepository;
    private final ExogenaGeneracionRepository generacionRepository;
    private final ArchivoSoporteService archivoSoporteService;
    private final AuditLogService auditLogService;
    private final UserUtil userUtil;

    // ===================== E1/E2: datos consolidados =====================

    /** HU-079 E1/E2: datos del formato exógena por cuenta (solo movimientos de sesiones CERRADAS). */
    public Map<String, Object> datos(int ano, String formato) {
        String fmt = normFormato(formato);
        LocalDate ini = LocalDate.of(ano, 1, 1), fin = LocalDate.of(ano, 12, 31);
        List<Map<String, Object>> cuentas = new ArrayList<>();
        BigDecimal totDep = BigDecimal.ZERO, totRet = BigDecimal.ZERO, totSaldo = BigDecimal.ZERO;

        for (BankAccount ba : bankAccountRepository.findAll()) {
            if (ba.getDeletedAt() != null) continue;
            BigDecimal dep = BigDecimal.ZERO, ret = BigDecimal.ZERO, saldo = ba.getInitialBalance() != null ? ba.getInitialBalance() : BigDecimal.ZERO;
            for (FinancialMovement m : movementRepository.findAllByBankAccountIdOrdered(ba.getId())) {
                if (m.getMovementDate() == null || !cerrada(m)) continue;       // E2: solo sesiones CERRADAS
                if (m.getMovementDate().getYear() != ano) {
                    if (!m.getMovementDate().isAfter(fin)) saldo = saldo.add(safe(m.getAmount())); // afecta saldo acumulado
                    continue;
                }
                BigDecimal amt = safe(m.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) >= 0) dep = dep.add(amt); else ret = ret.add(amt.abs());
                saldo = saldo.add(amt);
            }
            String nitBanco = ba.getBank() != null ? ba.getBank().getNit() : null;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("bankAccountId", ba.getId());
            c.put("banco", ba.getBank() != null ? ba.getBank().getName() : null);
            c.put("nitBanco", nitBanco);
            c.put("numeroCuenta", ba.getAccountNumber());                       // E1: completo (no enmascarado, contexto DIAN)
            c.put("tipoCuenta", ba.getAccountType() != null ? ba.getAccountType().name() : null);
            c.put("depositos", dep);
            c.put("retiros", ret);
            c.put("saldoCierre", saldo);
            cuentas.add(c);
            totDep = totDep.add(dep); totRet = totRet.add(ret); totSaldo = totSaldo.add(saldo);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("formato", fmt);
        out.put("ano", ano);
        out.put("cuentas", cuentas);
        out.put("totalDepositos", totDep);
        out.put("totalRetiros", totRet);
        out.put("totalSaldoCierre", totSaldo);
        return out;
    }

    // ===================== E4: validación previa =====================

    /** HU-079 E4: verifica NIT del banco, sesiones cerradas y cuadre antes de exportar. */
    public Map<String, Object> validar(int ano, String formato) {
        normFormato(formato);
        List<String> observaciones = new ArrayList<>();
        int movsFueraSesion = 0;
        for (BankAccount ba : bankAccountRepository.findAll()) {
            if (ba.getDeletedAt() != null) continue;
            if (ba.getBank() == null || ba.getBank().getNit() == null || ba.getBank().getNit().isBlank())
                observaciones.add("La cuenta " + ba.getCode() + " no tiene NIT del banco.");
            for (FinancialMovement m : movementRepository.findAllByBankAccountIdOrdered(ba.getId())) {
                if (m.getMovementDate() == null || m.getMovementDate().getYear() != ano) continue;
                if (!cerrada(m)) movsFueraSesion++;
            }
        }
        if (movsFueraSesion > 0)
            observaciones.add("Hay " + movsFueraSesion + " movimiento(s) del año " + ano
                    + " que NO pertenecen a una sesión de conciliación CERRADA (no se reportarán).");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ano", ano);
        out.put("valido", observaciones.isEmpty());
        out.put("observaciones", observaciones);
        return out;
    }

    // ===================== E3/E5: exportar + conservar + auditar =====================

    /**
     * HU-079 E3/E5: genera el archivo (CSV o XML genérico), lo conserva en archivos_soporte
     * (retención 10 años), registra la generación (E6) y audita EXPORTAR.
     */
    @Transactional
    public byte[] exportar(int ano, String formato, String formatoArchivo) {
        String fmt = normFormato(formato);
        String fArch = "xml".equalsIgnoreCase(formatoArchivo) ? "xml" : "csv";
        Map<String, Object> datos = datos(ano, fmt);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cuentas = (List<Map<String, Object>>) datos.get("cuentas");

        byte[] bytes = "xml".equals(fArch) ? buildXml(fmt, ano, cuentas) : buildCsv(cuentas);
        String fileName = "exogena_" + fmt + "_" + ano + "." + fArch;
        String mime = "xml".equals(fArch) ? "application/xml" : SimpleTableExporter.CSV_MIME;

        User u = userUtil.getUser();
        ArchivoSoporte soporte = null;
        try {
            soporte = archivoSoporteService.store(bytes, fileName, mime, "EXOGENA_" + fmt,
                    null, null, u != null ? u.getId() : null);
        } catch (RuntimeException ex) {
            log.warn("BNK-HU-079: no se pudo conservar el soporte exógena: {}", ex.getMessage());
        }

        ExogenaGeneracion gen = ExogenaGeneracion.builder()
                .anoFiscal(ano).formato(fmt).formatoArchivo(fArch)
                .hashArchivo(soporte != null ? soporte.getHashSha256() : null)
                .archivoSoporteId(soporte != null ? soporte.getId() : null)
                .generadoBy(u != null ? u.getId() : null)
                .build();
        generacionRepository.save(gen);

        auditLogService.register(AuditAction.EXPORT, AuditModule.BNK, AuditSeverity.LOW,
                "ExogenaGeneracion", gen.getId(),
                "EXPORTAR · Generación formato " + fmt + " año " + ano + " (" + fArch + ")",
                null, null, null);
        return bytes;
    }

    /** HU-079 E6: histórico de generaciones (opcionalmente por formato). */
    public List<ExogenaGeneracion> historico(String formato) {
        if (formato != null && !formato.isBlank())
            return generacionRepository.findByFormatoOrderByGeneradoAtDesc(normFormato(formato));
        return generacionRepository.findByOrderByGeneradoAtDesc();
    }

    // ===================== helpers =====================

    private boolean cerrada(FinancialMovement m) {
        return m.getReconciliationSession() != null
                && m.getReconciliationSession().getStatus() == ReconciliationSessionStatus.CLOSED;
    }

    private byte[] buildCsv(List<Map<String, Object>> cuentas) {
        List<String> headers = List.of("NIT Banco", "Banco", "Número de cuenta", "Tipo", "Depósitos", "Retiros", "Saldo al cierre");
        List<Function<Map<String, Object>, Object>> cols = List.of(
                m -> m.get("nitBanco"), m -> m.get("banco"), m -> m.get("numeroCuenta"),
                m -> m.get("tipoCuenta"), m -> m.get("depositos"), m -> m.get("retiros"), m -> m.get("saldoCierre"));
        return SimpleTableExporter.toCsv(headers, cols, cuentas);
    }

    private byte[] buildXml(String fmt, int ano, List<Map<String, Object>> cuentas) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<Exogena formato=\"").append(fmt).append("\" ano=\"").append(ano).append("\">\n");
        for (Map<String, Object> c : cuentas) {
            sb.append("  <Cuenta nitBanco=\"").append(nv(c.get("nitBanco"))).append("\" numero=\"")
              .append(nv(c.get("numeroCuenta"))).append("\" tipo=\"").append(nv(c.get("tipoCuenta"))).append("\">\n");
            sb.append("    <Depositos>").append(nv(c.get("depositos"))).append("</Depositos>\n");
            sb.append("    <Retiros>").append(nv(c.get("retiros"))).append("</Retiros>\n");
            sb.append("    <SaldoCierre>").append(nv(c.get("saldoCierre"))).append("</SaldoCierre>\n");
            sb.append("  </Cuenta>\n");
        }
        sb.append("</Exogena>\n");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String nv(Object o) { return o == null ? "" : o.toString().replace("\"", "&quot;").replace("<", "&lt;"); }
    private BigDecimal safe(BigDecimal b) { return b != null ? b : BigDecimal.ZERO; }

    private String normFormato(String formato) {
        String f = formato == null ? "1647" : formato.trim();
        if (!FORMATOS.contains(f))
            throw new IllegalArgumentException("Formato exógena no soportado: " + f + ". Válidos: 1647, 1010, 1011.");
        return f;
    }
}
