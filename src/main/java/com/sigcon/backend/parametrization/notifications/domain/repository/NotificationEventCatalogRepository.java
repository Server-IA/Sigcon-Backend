package com.sigcon.backend.parametrization.notifications.domain.repository;

import com.sigcon.backend.parametrization.notifications.domain.model.NotificationEventCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationEventCatalogRepository extends JpaRepository<NotificationEventCatalog, Long> {
    Optional<NotificationEventCatalog> findByEventKey(String eventKey);
    List<NotificationEventCatalog> findAllByOrderByModuleAscNameAsc();
    List<NotificationEventCatalog> findByModuleOrderByNameAsc(String module);
}
