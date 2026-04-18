package com.sigcon.backend.parametrization.parameters.domain.service;

import com.sigcon.backend.parametrization.parameters.domain.model.Parameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.CategoryParameter;
import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para obtener informacion del sistema (empresa) desde la tabla
 * {@code parameters} (categoria COMPANY).
 *
 * <p>Reemplaza el patron multi-tenant {@code user.getCompany()} eliminado
 * en Fase 0 (2026-04-12). Los datos de empresa se almacenan como parametros
 * key-value para que sean editables desde la UI sin requerir migracion.
 *
 * <p><b>Renombrado de {@code CompanyInfoService} -> {@code SystemInfoService}
 * el 2026-04-16</b> para reflejar el nombre real del concepto: SIGCON es un
 * sistema mono-empresa, no un orquestador de empresas.
 *
 * <p>Las claves esperadas en {@code parameters} (categoria COMPANY):
 * <ul>
 *   <li>{@code COMPANY_NAME} - razon social</li>
 *   <li>{@code COMPANY_NIT} - NIT fiscal</li>
 *   <li>{@code COMPANY_DV} - digito de verificacion</li>
 *   <li>{@code COMPANY_TYPE_REGIMEN_ID} - FK a tipos_regimen</li>
 *   <li>{@code COMPANY_TYPE_ORGANIZATION_ID} - FK a tipos_organizacion</li>
 * </ul>
 *
 * <p>Las claves se mantienen con prefijo {@code COMPANY_} para no romper
 * datos existentes en BD, aunque el nombre del servicio cambio.
 */
@Service
@RequiredArgsConstructor
public class SystemInfoService {

    private final ParameterRepository parameterRepository;

    /** Devuelve todos los parametros de empresa como mapa clave-valor. */
    public Map<String, String> getSystemInfo() {
        List<Parameter> params = parameterRepository.findByCategoryAndDeletedAtIsNull(CategoryParameter.COMPANY);
        Map<String, String> info = new HashMap<>();
        for (Parameter p : params) {
            info.put(p.getName(), p.getValue());
        }
        return info;
    }

    /** Lee un parametro especifico por nombre. */
    public String getSystemParam(String name) {
        return parameterRepository.findByNameAndDeletedAtIsNull(name)
                .map(Parameter::getValue)
                .orElse(null);
    }

    /** Razon social de la empresa. */
    public String getCompanyName() {
        return getSystemParam("COMPANY_NAME");
    }

    /** NIT fiscal de la empresa. */
    public String getCompanyNit() {
        return getSystemParam("COMPANY_NIT");
    }

    /** Digito de verificacion del NIT. */
    public String getCompanyDv() {
        return getSystemParam("COMPANY_DV");
    }

    /** ID del tipo de regimen tributario configurado. */
    public Long getCompanyTypeRegimenId() {
        String val = getSystemParam("COMPANY_TYPE_REGIMEN_ID");
        return val != null ? Long.parseLong(val) : null;
    }

    /** ID del tipo de organizacion configurado. */
    public Long getCompanyTypeOrganizationId() {
        String val = getSystemParam("COMPANY_TYPE_ORGANIZATION_ID");
        return val != null ? Long.parseLong(val) : null;
    }
}
