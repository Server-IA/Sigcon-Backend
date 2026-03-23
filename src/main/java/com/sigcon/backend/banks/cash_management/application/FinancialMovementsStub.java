package com.sigcon.backend.banks.cash_management.application;

import org.springframework.stereotype.Component;

/**
 * Este es un Stub(implementación temporal y simplificada de una dependencia que aún 
 * no existe o no está disponible. Su único propósito es devolver respuestas predefinidas 
 * (generalmente vacías o falsas) para que el código que depende de él pueda funcionar 
 * sin errores mientras el módulo real se desarrolla.) temporal hasta que se cree y se realize la implementacion
 * con el submodulo movimientos financieros de bancos y cajas, el cual es necesario para el desarrollo del
 * submodulo de cajas debido a que los requerimientos 11 y 12 requieren del submodulo de movimientos financieros
 * para poder verificar si una caja tiene movimientos registrados antes de
 * permitir su eliminación física o cierre definitivo.
 * Para no frenar el desarrollo de este submodulo se creo este stub que retorna valores seguros (flase / 0)
 * que simula una caja sin dependencias, una vez que se halla completado el desarrollo del submodulo
 * moviemientos financieros y se implemente este stub sera eliminado. 
*/
@Component
public class FinancialMovementsStub {

    /**
     * Verifica si una caja tiene movimientos financieros registrados.
     * BNK-RF-11: Requerido antes de permitir eliminación física de una caja.
     *
     * @param cashId ID de la caja a verificar
     * @return false siempre 
     */
    public boolean hasMovements(Long cashId) {
        return false;
    }

    /**
     * Retorna la cantidad de movimientos financieros registrados para una caja.
     * BNK-RF-11: Usado para mostrar el detalle de dependencias al usuario
     * cuando se intenta eliminar una caja con historial.
     *
     * @param cashId ID de la caja a verificar
     * @return 0 siempre 
     */
    public long countMovements(Long cashId) {
        return 0L;
    }

}
