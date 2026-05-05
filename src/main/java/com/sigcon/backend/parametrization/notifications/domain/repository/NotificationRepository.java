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

public interface NotificationRepository extends JpaRepository<Notification, Long> {

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
}
