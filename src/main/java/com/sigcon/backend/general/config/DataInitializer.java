package com.sigcon.backend.general.config;

import com.sigcon.backend.parametrization.modules.domain.model.ModuleEntity;
import com.sigcon.backend.parametrization.modules.domain.model.enums.ModelStatus;
import com.sigcon.backend.parametrization.modules.domain.repository.ModuleRepository;
import com.sigcon.backend.parametrization.resources.domain.model.Municipality;
import com.sigcon.backend.parametrization.resources.domain.model.TypeOrganization;
import com.sigcon.backend.parametrization.resources.domain.model.TypeRegimen;
import com.sigcon.backend.parametrization.resources.domain.repository.MunicipalityRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeOrganizationRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.TypeRegimenRepository;
import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.model.enums.TypePermits;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sigcon.backend.parametrization.companies.domain.model.Company;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyLocation;
import com.sigcon.backend.parametrization.companies.domain.model.CompanyStatus;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyLocationRepository;
import com.sigcon.backend.parametrization.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.MenuEntity;
import com.sigcon.backend.parametrization.menu.port.out.MenuRepositoryPort;
import com.sigcon.backend.parametrization.menuPermissions.domain.model.MenuPermissionsEntity;
import com.sigcon.backend.parametrization.menuPermissions.domain.repository.MenuPermissionsRepository;
import com.sigcon.backend.parametrization.menu.infrastructure.adapter.out.persistence.enums.MenuStatus;

import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;

import jakarta.transaction.Transactional;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
// @Profile("DEVELOPMENT")
@Transactional
public class DataInitializer implements CommandLineRunner {

        private final DataSource dataSource;

        private final RoleRepository roleRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;

        private final CompanyRepository companyRepository;
        private final TypeRegimenRepository typeRegimenRepository;
        private final TypeOrganizationRepository typeOrganizationRepository;
        private final CompanyLocationRepository companyLocationRepository;
        private final MunicipalityRepository municipalityRepository;

        @Override
        public void run(String... args) {

                // ✅ Ejecutar SQL desde archivo
                executeScripts();

                // Crear roles y asignar permisos
                createOrUpdateRole("SUPERADMIN", new HashSet<>(Set.of()));
                createOrUpdateRole("USER", new HashSet<>(Set.of()));

                Long companyId = createCompany("Sigcon S.A.S.", "9001234567", "1", "Juan Vidarte", "juan.vidarte@gmail.com", "100", 
                "1234567890", 2L, 1L, 1L, "Calle 123", "Sede Principal");

                // Crear usuarios
                createOrUpdateUser("SUPER", "ADMIN", "superadmin@gmail.com", "123456", "SUPERADMIN",
                                Set.of()
                , "superadmin", companyId);
        }



        private void createOrUpdateRole(String name, HashSet<Permission> permissions) {

                Role role = roleRepository.findByNameAndDeletedAtIsNull(name)
                                .orElseGet(() -> Role.builder().name(name).build());
                role.setPermissions(permissions);
                role.setStatus(Status.ACTIVE);
                roleRepository.save(role);
        }

        private void createOrUpdateUser(String name, String lastname, String email, String password, String role,
                        Set<Permission> permissions, String username, Long companyId) {

                Company company = companyRepository.findById(companyId)
                        .orElseThrow(() -> new RuntimeException("Company not found"));

                User user = userRepository.findByEmail(email)
                                .orElseGet(() -> User.builder()
                                                .name(name)
                                                .lastname(lastname)
                                                .email(email)
                                                .password(passwordEncoder.encode(password))
                                                .roles(Set.of(roleRepository.findByNameAndDeletedAtIsNull(role)
                                                                .orElseThrow(() -> new RuntimeException(
                                                                                "Role not found"))))
                                                .status(Status.ACTIVE)
                                                .username(username)
                                                .company(company)
                                                .build());
                userRepository.save(user);
        }

        private Long createCompany(
                String name,
                String nit,
                String dv,
                String legalRepresentative,
                String email,
                String size,
                String phone,
                Long typeRegimeId,
                Long typeOrganizationId,
                Long municipalityId,
                String address,
                String sede
        ) {
                Company company = companyRepository.findByNameAndDeletedAtIsNull(name)
                        .orElseGet(() -> Company.builder().name(name).build());

                TypeRegimen typeRegimen = typeRegimenRepository.findById(typeRegimeId)
                        .orElseThrow(() -> new RuntimeException("Type regimen not found"));
                TypeOrganization typeOrganization = typeOrganizationRepository.findById(typeOrganizationId)
                        .orElseThrow(() -> new RuntimeException("Type organization not found"));

                Municipality municipality = municipalityRepository.findById(municipalityId)
                        .orElseThrow(() -> new RuntimeException("Municipality not found"));

                company.setNit(nit);
                company.setDv(dv);
                company.setLegalRepresentative(legalRepresentative);
                company.setEmail(email);
                company.setSize(size);
                company.setPhone(phone);
                company.setTypeRegimen(typeRegimen);
                company.setTypeOrganization(typeOrganization);
                company.setStatus(CompanyStatus.ACTIVE);
                Company savedCompany = companyRepository.save(company);

                CompanyLocation companyLocation = CompanyLocation.builder()
                        .name(sede)
                        .address(address)
                        .status(CompanyStatus.ACTIVE)
                        .company(savedCompany)
                        .municipality(municipality)
                        .isMain(true)
                        .build();
                companyLocationRepository.save(companyLocation);

                return savedCompany.getId();
        }

        private void executeScripts() {

                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

                List<String> scripts = new ArrayList<>();

                try {

                        Resource[] resources =
                                new PathMatchingResourcePatternResolver()
                                .getResources("classpath:db/migration/*.sql");

                        for (Resource resource : resources) {
                                String filename = resource.getFilename();
                                if (filename == null) {
                                        System.out.println("⚠️ Ignorando script: " + filename + " (no tiene nombre)");
                                        continue;
                                }

                                scripts.add(filename);
                        }

                } catch (Exception e) {
                        throw new RuntimeException("Error al obtener scripts SQL", e);
                }

                // ordenar scripts
                Collections.sort(scripts);

                for (String script : scripts) {

                        try {

                                Resource resource = new ClassPathResource("db/migration/" + script);
                                String sql = new String(resource.getInputStream().readAllBytes());

                                System.out.println("Procesando script: " + script);

                                List<String> statements = splitSqlStatements(sql);

                                for (String statement : statements) {

                                        if (!statement.trim().isEmpty()) {
                                                jdbcTemplate.execute(statement);
                                        }

                                }

                                System.out.println("✔ Script ejecutado: " + script);
                                System.out.println("--------------------------------");

                        } catch (Exception e) {

                                System.out.println("❌ Error ejecutando script: " + script);
                                throw new RuntimeException(e);

                        }
                }

                System.out.println("✔ Todos los scripts ejecutados correctamente");
                
        }

        private List<String> splitSqlStatements(String sql) {

                List<String> statements = new ArrayList<>();
            
                StringBuilder current = new StringBuilder();
            
                boolean insideDollarBlock = false;
            
                String[] lines = sql.split("\n");
            
                for (String line : lines) {
            
                    if (line.contains("$$")) {
                        insideDollarBlock = !insideDollarBlock;
                    }
            
                    current.append(line).append("\n");
            
                    if (!insideDollarBlock && line.trim().endsWith(";")) {
            
                        statements.add(current.toString());
                        current.setLength(0);
            
                    }
                }
            
                if (current.length() > 0) {
                    statements.add(current.toString());
                }
            
                return statements;
        }

        private int extractVersion(String filename) {
                try {
                    String version = filename.split("__")[0]  // V10
                                            .replace("V", ""); // 10
                    return Integer.parseInt(version);
                } catch (Exception e) {
                    return Integer.MAX_VALUE; // manda al final si falla
                }
            }

        private List<Integer> extractVersionParts(String filename) {
                try {
                        String version = filename.split("__")[0]  // V1-10
                                                .replace("V", ""); // 1-10
                
                        String[] parts = version.split("-");
                
                        List<Integer> numbers = new ArrayList<>();
                        for (String part : parts) {
                                numbers.add(Integer.parseInt(part));
                        }
                
                        return numbers;
                } catch (Exception e) {
                        return List.of(Integer.MAX_VALUE);
                }
        }
}
