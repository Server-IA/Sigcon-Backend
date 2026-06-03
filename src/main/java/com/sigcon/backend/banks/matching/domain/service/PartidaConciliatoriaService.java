package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.banks.matching.domain.model.PartidaConciliatoria;
import com.sigcon.backend.banks.matching.domain.repository.PartidaConciliatoriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * BNK-HU-061 / BNK-HU-073: gestión de partidas conciliatorias — movimientos del
 * extracto que el banco cargó/abonó y NO están en libros, requiriendo asiento
 * de ajuste.
 *
 * - {@link #ensureCandidatesForAccount(Long)}: se llama tras el pre-procesamiento
 *   (HU-061 E1) para marcar como PENDIENTE los movimientos clasificados como GMF /
 *   COMISION / INTERES_* / NOTA_*. Aplica la exención GMF (HU-061 E5).
 * - El mapeo de cuentas (HU-073 E1) está en {@link #mapFor} y lo reutiliza
 *   AdjustmentEntryService para construir el comprobante.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartidaConciliatoriaService {

    public static final String C_NO = "NO_CONCILIADO";
    public static final String E_PENDIENTE = "PENDIENTE";
    public static final String E_RESUELTA = "RESUELTA_AJUSTE";
    public static final String E_DESCARTADA = "DESCARTADA";

    private final PartidaConciliatoriaRepository partidaRepository;
    private final FinancialMovementRepository movementRepository;
    private final BankAccountRepository bankAccountRepository;

    /** Resultado del mapeo tipo_movimiento -> cuentas de ajuste (HU-073 E1). */
    public static class AdjMap {
        public boolean isAdjustment;   // ¿genera partida/ajuste?
        public String tipoPartida;     // GMF_NO_REGISTRADO, COMISION_NO_REGISTRADA, ...
        public String cuentaDebito;    // código PUC débito
        public String cuentaCredito;   // código PUC crédito
        public boolean gmfExento;      // GMF detectado en cuenta exenta (HU-061 E5)
    }

    /**
     * HU-073 E1: tabla de mapeo tipo_movimiento -> (tipo partida, cuenta DB, cuenta CR).
     * "bankPuc" es el código PUC de la cuenta bancaria (lado banco). "sugerida" es la
     * cuenta sugerida en el pre-procesamiento (usada para notas débito/crédito).
     */
    public AdjMap mapFor(String tipoMovimiento, String bankPuc, String sugerida, boolean aplicaGmf) {
        AdjMap r = new AdjMap();
        if (tipoMovimiento == null) return r;
        switch (tipoMovimiento) {
            case "GMF":
                if (!aplicaGmf) { r.gmfExento = true; return r; } // HU-061 E5: no genera ajuste
                r.isAdjustment = true; r.tipoPartida = "GMF_NO_REGISTRADO";
                r.cuentaDebito = "530525"; r.cuentaCredito = bankPuc; break;
            case "COMISION":
                r.isAdjustment = true; r.tipoPartida = "COMISION_NO_REGISTRADA";
                r.cuentaDebito = "530505"; r.cuentaCredito = bankPuc; break;
            case "INTERES_GANADO":
                r.isAdjustment = true; r.tipoPartida = "INTERES_GANADO_NO_REGISTRADO";
                r.cuentaDebito = bankPuc; r.cuentaCredito = "421005"; break;
            case "INTERES_PAGADO":
                r.isAdjustment = true; r.tipoPartida = "INTERES_PAGADO_NO_REGISTRADO";
                r.cuentaDebito = "530520"; r.cuentaCredito = bankPuc; break;
            case "NOTA_DEBITO":
                r.isAdjustment = true; r.tipoPartida = "NOTA_DEBITO_NO_REGISTRADA";
                r.cuentaDebito = sugerida; r.cuentaCredito = bankPuc; break;
            case "NOTA_CREDITO":
                r.isAdjustment = true; r.tipoPartida = "NOTA_CREDITO_NO_REGISTRADA";
                r.cuentaDebito = bankPuc; r.cuentaCredito = sugerida; break;
            default:
                r.isAdjustment = false; // CHEQUE/PSE/TRANSFERENCIA/etc no son partidas de ajuste
        }
        return r;
    }

    /**
     * BNK-HU-061 E1: tras el pre-procesamiento, marca como PENDIENTE las partidas
     * conciliatorias detectadas. Devuelve cuántas se crearon y las alertas de GMF
     * en cuentas exentas (HU-061 E5). Idempotente (find-or-skip por movimiento).
     */
    @Transactional
    public Map<String, Object> ensureCandidatesForAccount(Long bankAccountId) {
        BankAccount ba = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta bancaria no encontrada"));
        String bankPuc = (ba.getAccountingAccount() != null && ba.getAccountingAccount().getPucAccount() != null)
                ? ba.getAccountingAccount().getPucAccount().getCode() : null;
        boolean aplicaGmf = Boolean.TRUE.equals(ba.getAplicaGmf());

        int creadas = 0;
        List<String> alertas = new ArrayList<>();
        for (FinancialMovement m : movementRepository.findAllByBankAccountIdOrdered(bankAccountId)) {
            // Solo movimientos del extracto sin conciliar (estado de TRABAJO de la sesión — Bug 1).
            if (!C_NO.equals(m.getEstadoConciliacionSesion())) continue;
            AdjMap map = mapFor(m.getTipoMovimiento(), bankPuc, m.getCuentaPucSugerida(), aplicaGmf);
            if (map.gmfExento) {
                alertas.add("Movimiento #" + m.getId() + " (" + m.getMovementDate()
                        + "): Posible error del banco — cobro de GMF en cuenta exenta (art. 879 ET).");
                continue;
            }
            if (!map.isAdjustment) continue;
            // find-or-skip: si ya existe partida activa para el movimiento, no duplicar.
            if (partidaRepository.findByFinancialMovementIdAndDeletedAtIsNull(m.getId()).isPresent()) continue;
            partidaRepository.save(PartidaConciliatoria.builder()
                    .companyId(ba.getCompanyId())
                    .bankAccountId(bankAccountId)
                    .financialMovementId(m.getId())
                    .tipo(map.tipoPartida)
                    .estado(E_PENDIENTE)
                    .monto(m.getAmount() != null ? m.getAmount().abs() : java.math.BigDecimal.ZERO)
                    .cuentaDebitoSugerida(map.cuentaDebito)
                    .cuentaCreditoSugerida(map.cuentaCredito)
                    .descripcion(m.getDescripcionNormalizada() != null ? m.getDescripcionNormalizada() : m.getDescription())
                    .fechaOrigen(m.getMovementDate()) // HU-074 E1: origen de la antigüedad
                    .build());
            creadas++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("partidasCreadas", creadas);
        r.put("alertasGmfExento", alertas);
        return r;
    }

    /** Lista las partidas de la cuenta (opcionalmente filtradas por estado) para la UI. */
    public List<Map<String, Object>> listForAccount(Long bankAccountId, String estado) {
        List<PartidaConciliatoria> list = (estado == null || estado.isBlank())
                ? partidaRepository.findByBankAccountIdAndDeletedAtIsNullOrderByIdDesc(bankAccountId)
                : partidaRepository.findByBankAccountIdAndEstadoAndDeletedAtIsNullOrderByIdDesc(bankAccountId, estado);
        List<Map<String, Object>> out = new ArrayList<>();
        for (PartidaConciliatoria p : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("financialMovementId", p.getFinancialMovementId());
            row.put("tipo", p.getTipo());
            row.put("estado", p.getEstado());
            row.put("monto", p.getMonto());
            row.put("cuentaDebitoSugerida", p.getCuentaDebitoSugerida());
            row.put("cuentaCreditoSugerida", p.getCuentaCreditoSugerida());
            row.put("comprobanteAjusteId", p.getComprobanteAjusteId());
            row.put("descripcion", p.getDescripcion());
            movementRepository.findById(p.getFinancialMovementId()).ifPresent(m -> {
                row.put("fecha", m.getMovementDate());
                row.put("tipoMovimiento", m.getTipoMovimiento());
            });
            out.add(row);
        }
        return out;
    }

    /**
     * BNK-HU-073 E8: marca la partida del movimiento como RESUELTA_AJUSTE y la
     * vincula al comprobante. Si no existía partida (movimiento clasificado
     * manualmente), la crea ya resuelta.
     */
    @Transactional
    public void resolveByAdjustment(Long financialMovementId, Long bankAccountId, Long companyId,
                                    String tipoPartida, java.math.BigDecimal monto, Long comprobanteId,
                                    String cuentaDb, String cuentaCr, String descripcion) {
        Optional<PartidaConciliatoria> existing =
                partidaRepository.findByFinancialMovementIdAndDeletedAtIsNull(financialMovementId);
        if (existing.isPresent()) {
            PartidaConciliatoria p = existing.get();
            p.setEstado(E_RESUELTA);
            p.setComprobanteAjusteId(comprobanteId);
            partidaRepository.save(p);
        } else {
            partidaRepository.save(PartidaConciliatoria.builder()
                    .companyId(companyId)
                    .bankAccountId(bankAccountId)
                    .financialMovementId(financialMovementId)
                    .tipo(tipoPartida != null ? tipoPartida : "OTRO")
                    .estado(E_RESUELTA)
                    .monto(monto != null ? monto.abs() : java.math.BigDecimal.ZERO)
                    .cuentaDebitoSugerida(cuentaDb)
                    .cuentaCreditoSugerida(cuentaCr)
                    .comprobanteAjusteId(comprobanteId)
                    .descripcion(descripcion)
                    .build());
        }
    }
}
