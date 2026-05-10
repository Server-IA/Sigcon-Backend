package com.sigcon.backend.parametrization.notifications.domain.repository;

import com.sigcon.backend.parametrization.notifications.domain.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Notification> {

    /** HU-PA-21: bandeja del usuario (no expiradas) ordenada por created_at DESC. */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.expiresAt > :now "
         + "ORDER BY n.createdAt DESC")
    Page<Notification> findActiveByUser(@Param("userId") Long userId,
                                        @Param("now") LocalDateTime now,
                                        Pageable pageable);

    /** Filtro por modulo. */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.expiresAt > :now AND n.module = :module "
         + "ORDER BY n.createdAt DESC")
    Page<Notification> findActiveByUserAndModule(@Param("userId") Long userId,
                                                 @Param("now") LocalDateTime now,
                                                 @Param("module") String module,
                                                 Pageable pageable);

    /** Filtro: solo unread. */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.expiresAt > :now AND n.readAt IS NULL "
         + "ORDER BY n.createdAt DESC")
    Page<Notification> findActiveUnreadByUser(@Param("userId") Long userId,
                                              @Param("now") LocalDateTime now,
                                              Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId "
         + "AND n.expiresAt > :now AND n.readAt IS NULL")
    long countActiveUnread(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * HU-PA-25: dedup. Busca notificaciones para el mismo usuario+evento+source dentro de la ventana temporal.
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.eventKey = :eventKey "
         + "AND ((:sourceId IS NULL AND n.sourceId IS NULL) OR n.sourceId = :sourceId) "
         + "AND n.createdAt >= :since")
    List<Notification> findRecentDuplicates(@Param("userId") Long userId,
                                            @Param("eventKey") String eventKey,
                                            @Param("sourceId") Long sourceId,
                                            @Param("since") LocalDateTime since);

    /** HU-PA-22: marcar todas como leidas. */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL "
         + "AND n.expiresAt > :now")
    int markAllReadByUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** HU-PA-24: purga (hard delete) de notificaciones cuyo expires_at ya paso. */
    @Modifying
    @Query(value = "DELETE FROM notifications WHERE expires_at <= :now", nativeQuery = true)
    int hardDeleteExpired(@Param("now") LocalDateTime now);

    /**
     * QA Bloque PA Bug 54 (HU-PA-24 E2, 2026-05-09): backfill para registros
     * legacy con expires_at IS NULL. Si una notificacion antigua nunca tuvo
     * fecha de vencimiento (creada antes de la columna), purgar las que
     * tienen mas de N dias de antiguedad sin lectura.
     */
    @Modifying
    @Query(value = "DELETE FROM notifications WHERE expires_at IS NULL AND created_at < :cutoff", nativeQuery = true)
    int hardDeleteLegacyOlderThan(@Param("cutoff") LocalDateTime cutoff);

    /**
     * QA Bloque PA Bug 55 (HU-PA-24 E3, 2026-05-09): purga absoluta a los N dias
     * desde created_at, independiente de expires_at. La HU pide retencion 90d
     * como hard cap. Usar en addition al hardDeleteExpired.
     */
    @Modifying
    @Query(value = "DELETE FROM notifications WHERE created_at < :cutoff", nativeQuery = true)
    int hardDeleteOlderThanAbsolute(@Param("cutoff") LocalDateTime cutoff);
}
