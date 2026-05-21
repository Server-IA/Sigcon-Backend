package com.sigcon.backend.banks.matching.domain.service;

import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.banks.matching.application.ParametrosMatchingRequest;
import com.sigcon.backend.banks.matching.domain.model.ParametrosMatching;
import com.sigcon.backend.banks.matching.domain.repository.ParametrosMatchingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * BNK-HU-072: parámetros del motor de matching. Resuelve los efectivos (override
 * por cuenta o globales), valida rangos lógicos y audita cambios.
 */
@Service
@RequiredArgsConstructor
public class ParametrosMatchingService {

    private final ParametrosMatchingRepository repository;
    private final AuditPublisher auditPublisher;

    /** Globales de la empresa (crea defaults si no existen). */
    @Transactional
    public ParametrosMatching getGlobal() {
        return repository.findByCuentaBancariaIdIsNull()
                .orElseGet(() -> repository.save(ParametrosMatching.builder().cuentaBancariaId(null).build()));
    }

    /**
     * BNK-HU-072: parámetros EFECTIVOS para una cuenta = override de la cuenta si
     * existe, de lo contrario los globales.
     */
    public ParametrosMatching getEffective(Long bankAccountId) {
        if (bankAccountId != null) {
            var byAccount = repository.findByCuentaBancariaId(bankAccountId);
            if (byAccount.isPresent()) return byAccount.get();
        }
        return repository.findByCuentaBancariaIdIsNull()
                .orElseGet(this::getGlobal);
    }

    /** Crea o actualiza parámetros (global si cuentaBancariaId == null, override si no). */
    @Transactional
    public ParametrosMatching upsert(ParametrosMatchingRequest req) {
        ParametrosMatching p = (req.getCuentaBancariaId() == null)
                ? repository.findByCuentaBancariaIdIsNull().orElse(ParametrosMatching.builder().build())
                : repository.findByCuentaBancariaId(req.getCuentaBancariaId())
                        .orElse(ParametrosMatching.builder().cuentaBancariaId(req.getCuentaBancariaId()).build());

        if (req.getToleranciaMontoAbs() != null) p.setToleranciaMontoAbs(req.getToleranciaMontoAbs());
        if (req.getToleranciaMontoPct() != null) p.setToleranciaMontoPct(req.getToleranciaMontoPct());
        if (req.getToleranciaFechaDias() != null) p.setToleranciaFechaDias(req.getToleranciaFechaDias());
        if (req.getUmbralScoreAutoAprobar() != null) p.setUmbralScoreAutoAprobar(req.getUmbralScoreAutoAprobar());
        if (req.getUmbralScoreSugerir() != null) p.setUmbralScoreSugerir(req.getUmbralScoreSugerir());
        if (req.getPermitirNaM() != null) p.setPermitirNaM(req.getPermitirNaM());
        if (req.getPesoMonto() != null) p.setPesoMonto(req.getPesoMonto());
        if (req.getPesoFecha() != null) p.setPesoFecha(req.getPesoFecha());
        if (req.getPesoTexto() != null) p.setPesoTexto(req.getPesoTexto());
        if (req.getPesoReferencia() != null) p.setPesoReferencia(req.getPesoReferencia());
        if (req.getCuentaBancariaId() != null) p.setCuentaBancariaId(req.getCuentaBancariaId());

        validate(p);
        p = repository.save(p);
        auditPublisher.publishUpdate(AuditModule.BNK, "ParametrosMatching", p.getId(),
                "Parámetros matching " + (p.getCuentaBancariaId() == null ? "globales" : "cuenta " + p.getCuentaBancariaId())
                        + " actualizados (autoAprobar=" + p.getUmbralScoreAutoAprobar() + ", sugerir=" + p.getUmbralScoreSugerir() + ")");
        return p;
    }

    /** BNK-HU-072 E4: vista comparativa global vs cuenta + efectivo. */
    public Map<String, Object> comparative(Long bankAccountId) {
        ParametrosMatching global = getGlobal();
        ParametrosMatching cuenta = bankAccountId != null
                ? repository.findByCuentaBancariaId(bankAccountId).orElse(null) : null;
        ParametrosMatching efectivo = getEffective(bankAccountId);
        Map<String, Object> r = new HashMap<>();
        r.put("global", global);
        r.put("cuenta", cuenta);
        r.put("efectivo", efectivo);
        r.put("usaOverride", cuenta != null);
        return r;
    }

    /** BNK-HU-072 E3: validación de rangos lógicos. */
    private void validate(ParametrosMatching p) {
        if (p.getUmbralScoreAutoAprobar() < p.getUmbralScoreSugerir()) {
            throw new IllegalArgumentException("umbral_score_auto_aprobar debe ser >= umbral_score_sugerir");
        }
        int sumaPesos = p.getPesoMonto() + p.getPesoFecha() + p.getPesoTexto() + p.getPesoReferencia();
        if (sumaPesos != 100) {
            throw new IllegalArgumentException("Los pesos (monto+fecha+texto+referencia) deben sumar 100. Suma actual: " + sumaPesos);
        }
        if (p.getToleranciaMontoAbs() == null || p.getToleranciaMontoAbs().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("tolerancia_monto_abs debe ser >= 0");
        }
        if (p.getToleranciaFechaDias() < 0 || p.getToleranciaFechaDias() > 30) {
            throw new IllegalArgumentException("tolerancia_fecha_dias debe estar entre 0 y 30");
        }
        if (p.getUmbralScoreAutoAprobar() < 0 || p.getUmbralScoreAutoAprobar() > 100
                || p.getUmbralScoreSugerir() < 0 || p.getUmbralScoreSugerir() > 100) {
            throw new IllegalArgumentException("Los umbrales de score deben estar entre 0 y 100");
        }
    }
}
