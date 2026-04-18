package com.sigcon.backend.nomina.domain.repository;

import com.sigcon.backend.nomina.domain.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de empleados de nomina (HU-NOM-01).
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Busqueda por documento unico (tipo + numero). */
    Optional<Employee> findByDocumentTypeAndDocumentNumberAndDeletedAtIsNull(
            String documentType, String documentNumber);

    /** true si ya existe otro empleado con el mismo documento. */
    boolean existsByDocumentTypeAndDocumentNumberAndDeletedAtIsNull(
            String documentType, String documentNumber);

    /**
     * TER-10 x NOM: true si hay empleados activos vinculados al tercero.
     * Usado por {@code ThirdPartyService.delete} para bloquear la eliminacion
     * de un tercero que sigue siendo empleado de nomina activo.
     */
    boolean existsByThirdPartyIdAndDeletedAtIsNull(Long thirdPartyId);

    /** Empleados activos por centro de costo (para liquidacion por grupo). */
    List<Employee> findByCostCenterIdAndStatusAndDeletedAtIsNull(Long costCenterId, String status);

    /** Todos los empleados activos (para liquidacion masiva). */
    List<Employee> findByStatusAndDeletedAtIsNull(String status);
}
