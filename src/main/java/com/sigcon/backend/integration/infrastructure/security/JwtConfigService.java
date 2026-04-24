package com.sigcon.backend.integration.infrastructure.security;

import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * HU-INT-RF-11: lee la configuracion JWT desde la tabla {@code parameters}
 * (categoria INTEGRATION_AGROFUSION). Cachea los valores pero permite refresh
 * llamando a {@link #reload}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtConfigService {

    public static final String PARAM_ENABLED = "AGROFUSION_JWT_ENABLED";
    public static final String PARAM_ISSUER = "AGROFUSION_JWT_ISSUER";
    public static final String PARAM_JWKS_URL = "AGROFUSION_JWKS_URL";
    public static final String PARAM_SCOPE = "AGROFUSION_JWT_SCOPE_REQUIRED";

    private final ParameterRepository parameterRepository;

    private volatile Boolean enabled;
    private volatile String issuer;
    private volatile String jwksUrl;
    private volatile String scopeRequired;

    public boolean isEnabled() {
        if (enabled == null) enabled = "true".equalsIgnoreCase(readParam(PARAM_ENABLED).orElse("false"));
        return enabled;
    }

    public String getIssuer() {
        if (issuer == null) issuer = readParam(PARAM_ISSUER).orElse(null);
        return issuer;
    }

    public String getJwksUrl() {
        if (jwksUrl == null) jwksUrl = readParam(PARAM_JWKS_URL).orElse(null);
        return jwksUrl;
    }

    public String getScopeRequired() {
        if (scopeRequired == null) scopeRequired = readParam(PARAM_SCOPE).orElse("aaef:lote:enviar");
        return scopeRequired;
    }

    /** Invalida el cache para forzar re-lectura de parameters. */
    public void reload() {
        enabled = null; issuer = null; jwksUrl = null; scopeRequired = null;
    }

    private Optional<String> readParam(String name) {
        // Multi-tenant: usar query nativa que bypasea @Filter("tenantFilter").
        // findByNameAndDeletedAtIsNull devolveria multiples filas (una por empresa)
        // y el Optional fallaria con NonUniqueResultException en contexto sin tenant.
        return parameterRepository.findGlobalValueByName(name);
    }
}
