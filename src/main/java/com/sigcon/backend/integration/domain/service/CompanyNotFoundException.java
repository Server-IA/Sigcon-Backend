package com.sigcon.backend.integration.domain.service;

/**
 * Excepcion que indica que el NIT recibido en el payload AAEF no corresponde
 * a ninguna empresa registrada en SIGCON.
 *
 * <p>Se traduce a HTTP 400 con errorCode {@code COMPANY_NOT_FOUND} en el
 * ACK devuelto a AgroFusion. El flujo:
 *
 * <ol>
 *   <li>AgroFusion envia lote AAEF con {@code metadata.SourceSystem.SystemNIT}.</li>
 *   <li>{@link AaefReceiverService} resuelve el company_id por ese NIT.</li>
 *   <li>Si no existe empresa activa con ese NIT, se lanza esta excepcion.</li>
 *   <li>El ACK incluye {@code errorCode=COMPANY_NOT_FOUND} y el mensaje.</li>
 * </ol>
 *
 * <p>AgroFusion debe registrar la empresa destino en SIGCON antes de volver
 * a enviar lotes con ese NIT, o corregir el NIT del origen.
 */
public class CompanyNotFoundException extends RuntimeException {

    public static final String ERROR_CODE = "COMPANY_NOT_FOUND";

    private final String nit;

    public CompanyNotFoundException(String nit) {
        super("El NIT '" + nit + "' no corresponde a ninguna empresa registrada en SIGCON. "
            + "Registre la empresa antes de enviar lotes AAEF con ese NIT.");
        this.nit = nit;
    }

    public String getNit() { return nit; }
    public String getErrorCode() { return ERROR_CODE; }
}
