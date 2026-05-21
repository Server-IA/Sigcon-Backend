package com.sigcon.backend.banks.archivos_soporte.domain.repository;

import com.sigcon.backend.banks.archivos_soporte.domain.model.ArchivoSoporte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * BNK-HU-062/063: acceso a soportes conservados.
 */
public interface ArchivoSoporteRepository extends JpaRepository<ArchivoSoporte, Long> {

    List<ArchivoSoporte> findByBankAccountIdAndDeletedAtIsNullOrderByUploadedAtDesc(Long bankAccountId);

    Optional<ArchivoSoporte> findByIdAndDeletedAtIsNull(Long id);

    long countByDeletedAtIsNull();

    /** BNK-HU-063 E6: soportes próximos a vencer retención (entre hoy y +6 meses). */
    @Query("SELECT COUNT(a) FROM ArchivoSoporte a WHERE a.deletedAt IS NULL "
            + "AND a.retenerHasta IS NOT NULL AND a.retenerHasta <= :limite")
    long countProximosAVencer(@Param("limite") LocalDateTime limite);

    @Query("SELECT COALESCE(SUM(a.fileSize),0) FROM ArchivoSoporte a WHERE a.deletedAt IS NULL")
    long totalBytesAlmacenados();
}
