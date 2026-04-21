package com.sigcon.backend.general.config;

import com.sigcon.backend.parametrization.parameters.domain.model.Parameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.CategoryParameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.StatusParameter;
import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.parametrization.users.domain.model.Permission;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
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
        private final ParameterRepository parameterRepository;

        @Override
        public void run(String... args) {
                executeScripts();
                seedCompanyParameters();
                createOrUpdateRole("ADMIN", new HashSet<>(Set.of()));
                createOrUpdateRole("USER", new HashSet<>(Set.of()));
                createOrUpdateUser("SUPER", "ADMIN", "superadmin@gmail.com", "123456", "ADMIN", Set.of(), "superadmin");
        }

        /**
         * Inserta parametros por defecto de la empresa si no existen.
         * Estos parametros permiten configurar la empresa desde la pantalla de parametros.
         */
        private void seedCompanyParameters() {
                if (parameterRepository.findByNameAndDeletedAtIsNull("COMPANY_NAME").isEmpty()) {
                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_NAME")
                                        .value("Mi Empresa S.A.S.")
                                        .description("Nombre o razon social de la empresa")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());

                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_NIT")
                                        .value("0000000000")
                                        .description("NIT de la empresa")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());

                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_DV")
                                        .value("0")
                                        .description("Digito de verificacion del NIT")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());

                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_LEGAL_REPRESENTATIVE")
                                        .value("")
                                        .description("Nombre del representante legal")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());

                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_EMAIL")
                                        .value("")
                                        .description("Correo electronico de la empresa")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());

                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_PHONE")
                                        .value("")
                                        .description("Telefono de la empresa")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());

                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_ADDRESS")
                                        .value("")
                                        .description("Direccion de la empresa")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());

                        parameterRepository.save(Parameter.builder()
                                        .name("COMPANY_SIZE")
                                        .value("")
                                        .description("Tamano de la empresa")
                                        .category(CategoryParameter.COMPANY)
                                        .status(StatusParameter.ACTIVE)
                                        .build());
                }
        }

        private void createOrUpdateRole(String name, HashSet<Permission> permissions) {

                Role role = roleRepository.findByNameAndDeletedAtIsNull(name)
                                .orElseGet(() -> Role.builder().name(name).build());
                role.setPermissions(permissions);
                role.setStatus(Status.ACTIVE);
                roleRepository.save(role);
        }

        private void createOrUpdateUser(String name, String lastname, String email, String password, String role,
                        Set<Permission> permissions, String username) {

                boolean isSuperadmin = "superadmin".equals(username);
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
                                                // Multi-tenant: el superadmin es PLATFORM_ADMIN sin empresa.
                                                // Cumple ck_users_tenant_or_platform (V10-A).
                                                .platformRole(isSuperadmin ? "PLATFORM_ADMIN" : null)
                                                .companyId(isSuperadmin ? null : 1L)
                                                .build());
                userRepository.save(user);
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
                                        System.out.println("Ignorando script: " + filename + " (no tiene nombre)");
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

                                System.out.println("Script ejecutado: " + script);
                                System.out.println("--------------------------------");

                        } catch (Exception e) {

                                System.out.println("Error ejecutando script: " + script);
                                throw new RuntimeException(e);

                        }
                }

                System.out.println("Todos los scripts ejecutados correctamente");

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
