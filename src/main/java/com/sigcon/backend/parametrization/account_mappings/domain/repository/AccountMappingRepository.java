package com.sigcon.backend.parametrization.account_mappings.domain.repository;

import com.sigcon.backend.parametrization.account_mappings.domain.model.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para {@link AccountMapping}.
 *
 * <p>El soft delete es manejado por {@code @SQLDelete} en la entidad, por lo que
 * cualquier {@code find} automaticamente filtra {@code deleted_at IS NULL}.
 */
@Repository
public interface AccountMappingRepository extends JpaRepository<AccountMapping, Long> {

    /**
     * Busca un mapeo por codigo de concepto.
     *
     * @param conceptCode codigo logico del concepto (ej. AR_CLIENTES)
     * @return mapeo si existe y no esta eliminado
     */
    Optional<AccountMapping> findByConceptCode(String conceptCode);

    /**
     * Verifica existencia de un concepto (para validacion fail-fast al iniciar la app).
     *
     * @param conceptCode codigo logico del concepto
     * @return true si el mapeo existe y esta activo
     */
    boolean existsByConceptCode(String conceptCode);

    /**
     * Lista todos los mapeos ordenados por codigo de concepto (para vista admin).
     */
    List<AccountMapping> findAllByOrderByConceptCodeAsc();
}
