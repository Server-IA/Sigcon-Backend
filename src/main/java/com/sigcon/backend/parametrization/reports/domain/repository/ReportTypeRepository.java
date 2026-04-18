package com.sigcon.backend.parametrization.reports.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sigcon.backend.parametrization.reports.domain.model.ReportType;

/**
 * Repositorio JPA para la entidad ReportType.
 * Soporta paginacion, filtros dinamicos y validacion de unicidad por nombre.
 */
@Repository
public interface ReportTypeRepository extends JpaRepository<ReportType, Long>, JpaSpecificationExecutor<ReportType> {

    /**
     * Verifica si existe un tipo de reporte activo (no eliminado) con el nombre dado.
     *
     * @param name nombre del tipo de reporte
     * @return true si ya existe un registro con ese nombre
     */
    boolean existsByNameAndDeletedAtIsNull(String name);

    /**
     * Verifica si existe un tipo de reporte activo con el nombre dado, excluyendo un ID especifico.
     * Util para validar unicidad al actualizar.
     *
     * @param name nombre del tipo de reporte
     * @param id   ID a excluir de la busqueda
     * @return true si existe otro registro con ese nombre
     */
    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, Long id);
}
