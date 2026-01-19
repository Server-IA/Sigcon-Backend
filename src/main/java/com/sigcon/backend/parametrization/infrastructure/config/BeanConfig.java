package com.sigcon.backend.parametrization.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menu.service.MenuService;
import com.sigcon.backend.parametrization.infrastructure.adapter.out.persistence.MenuRepositoryAdapter;
import com.sigcon.backend.parametrization.infrastructure.adapter.out.persistence.SpringDataMenuRepository;

@Configuration
public class BeanConfig {
    @Bean
    public MenuRepositoryPort menuRepositoryPort(SpringDataMenuRepository repo) {
        return new MenuRepositoryAdapter(repo);
    }

    @Bean
    public MenuService menuService(MenuRepositoryPort port) {
        return new MenuService(port);
    }
}
