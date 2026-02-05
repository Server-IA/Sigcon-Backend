package com.sigcon.backend.parametrization.menu.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menu.service.MenuService;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuRepositoryAdapter;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.SpringDataMenuRepository;

import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;

@Configuration
public class BeanConfig {
    @Bean
    public MenuRepositoryPort menuRepositoryPort(SpringDataMenuRepository repo) {
        return new MenuRepositoryAdapter(repo);
    }

    @Bean
    public MenuService menuService(MenuRepositoryPort port, ModuleRepository moduleRepository) {
        return new MenuService(port, moduleRepository);
    }
}
