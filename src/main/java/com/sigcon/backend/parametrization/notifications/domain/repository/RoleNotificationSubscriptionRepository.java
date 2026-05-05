package com.sigcon.backend.parametrization.notifications.domain.repository;

import com.sigcon.backend.parametrization.notifications.domain.model.RoleNotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleNotificationSubscriptionRepository extends JpaRepository<RoleNotificationSubscription, Long> {

    Optional<RoleNotificationSubscription> findByRoleIdAndEventKey(Long roleId, String eventKey);

    List<RoleNotificationSubscription> findByRoleId(Long roleId);

    @Query("SELECT s FROM RoleNotificationSubscription s WHERE s.eventKey = :eventKey AND s.enabled = true "
         + "AND s.roleId IN :roleIds")
    List<RoleNotificationSubscription> findActiveByEventKeyAndRoleIds(@Param("eventKey") String eventKey,
                                                                     @Param("roleIds") List<Long> roleIds);

    /** Devuelve user_ids distintos de la empresa que tienen un rol suscrito al evento. */
    @Query(value = "SELECT DISTINCT ur.user_id FROM users_roles ur "
                 + "JOIN role_notification_subscriptions s ON s.role_id = ur.role_id "
                 + "JOIN users u ON u.id = ur.user_id AND u.deleted_at IS NULL "
                 + "WHERE s.deleted_at IS NULL AND s.enabled = TRUE "
                 + "  AND s.event_key = :eventKey "
                 + "  AND u.company_id = :companyId",
           nativeQuery = true)
    List<Long> findUserIdsSubscribedToEvent(@Param("eventKey") String eventKey,
                                            @Param("companyId") Long companyId);
}
