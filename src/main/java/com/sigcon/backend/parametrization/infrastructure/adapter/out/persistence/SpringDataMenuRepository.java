package com.sigcon.backend.parametrization.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SpringDataMenuRepository extends JpaRepository<MenuEntity, Long> {
    List<MenuEntity> findByActiveTrue();
    List<MenuEntity> findByActiveTrueOrderByMenuOrderAsc();
}
    