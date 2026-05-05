package com.sigcon.backend.parametrization.temporary_permissions.domain.repository;

import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission;
import com.sigcon.backend.parametrization.temporary_permissions.domain.model.TemporaryPermission.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TemporaryPermissionRepository
        extends JpaRepository<TemporaryPermission, Long>,
                JpaSpecificationExecutor<TemporaryPermission> {

    /** Cuenta cuantos permisos temporales activos tiene un usuario (HU-PA-13 E3). */
    long countByUserIdAndStatusAndDeletedAtIsNull(Long userId, Status status);

    /** Lista permisos temporales del usuario en un estado dado. */
    List<TemporaryPermission> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, Status status);

    /** Todos los activos del usuario, ordenados por end_date. */
    List<TemporaryPermission> findByUserIdAndStatusOrderByEndDateAsc(Long userId, Status status);

    /**
     * HU-PA-15 E1: lista permisos ACTIVE cuya end_date ya paso. El job los marca EXPIRED.
     */
    @Query("SELECT t FROM TemporaryPermission t WHERE t.deletedAt IS NULL "
         + "AND t.status = 'ACTIVE' AND t.endDate <= :now")
    List<TemporaryPermission> findExpired(@Param("now") LocalDateTime now);

    /**
     * HU-PA-15 E4: lista permisos ACTIVE que vencen en las proximas 24 horas
     * y aun no han sido notificados.
     */
    @Query("SELECT t FROM TemporaryPermission t WHERE t.deletedAt IS NULL "
         + "AND t.status = 'ACTIVE' AND t.expiredNotified24h = false "
         + "AND t.endDate > :now AND t.endDate <= :soon")
    List<TemporaryPermission> findUpcomingExpirations(@Param("now") LocalDateTime now,
                                                       @Param("soon") LocalDateTime soon);

    /**
     * HU-PA-13 E1 + HU-PA-17 E1: permisos efectivos del usuario AHORA (start_date <= now <= end_date).
     */
    @Query("SELECT t FROM TemporaryPermission t WHERE t.deletedAt IS NULL "
         + "AND t.userId = :userId AND t.status = 'ACTIVE' "
         + "AND t.startDate <= :now AND t.endDate > :now")
    List<TemporaryPermission> findActiveAtMoment(@Param("userId") Long userId,
                                                   @Param("now") LocalDateTime now);
}
