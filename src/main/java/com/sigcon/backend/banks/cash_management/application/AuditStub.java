package com.sigcon.backend.banks.cash_management.application;

import org.springframework.stereotype.Component;

/**
 * Este es un Stub(implementación temporal y simplificada de una dependencia que aún 
 * no existe o no está disponible. Su único propósito es devolver respuestas predefinidas 
 * (generalmente vacías o falsas) para que el código que depende de él pueda funcionar 
 * sin errores mientras el módulo real se desarrolla.) temporal hasta que se cree y se realize la implementacion
 * con el submodulo arqueos de bancos y cajas, el cual es necesario para el desarrollo del
 * submodulo de cajas debido a que los requerimientos 11 y 12 requieren del submodulo de arqueos
 * para poder verificar si una caja tiene arqueos abiertos o pendientes antes de
 * permitir su eliminación física o cierre definitivo.
 * Para no frenar el desarrollo de este submodulo se creo este stub que retorna valores seguros (flase / 0)
 * que simula una caja sin dependencias, una vez que se halla completado el desarrollo del submodulo
 * arqueos y se implemente este stub sera eliminado. 
*/
@Component
public class AuditStub {

     /**
     * Verifica si una caja tiene arqueos abiertos o pendientes.
     * BNK-RF-11: Requerido antes de permitir eliminación física de una caja.
     * BNK-RF-12: Requerido antes de permitir el cierre definitivo de una caja.
     *
     * @param cashId ID de la caja a verificar
     * @return false siempre — stub, módulo ARQUEOS no implementado aún
     */
    public boolean hasOpenAudits(Long cashId) {
        return false;
    }

    /**
     * Retorna la cantidad de arqueos asociados a una caja.
     * BNK-RF-11: Usado para mostrar el detalle de dependencias al usuario
     * cuando se intenta eliminar una caja con historial.
     *
     * @param cashId ID de la caja a verificar
     * @return 0 siempre — stub, módulo ARQUEOS no implementado aún
     */
    public long countAudits(Long cashId) {
        return 0L;
    }

}
