package com.eugene.sharednotes.user;

import com.eugene.sharednotes.common.Role;
import com.eugene.sharednotes.user.dto.CreateUserRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Создаёт первого администратора при старте приложения, если ни одного нет в системе.
 * <p>
 * Эндпоинты {@code /admin/**} требуют {@code ROLE_ADMIN}, поэтому создать первого администратора
 * через API невозможно.
 */
@Component
@Slf4j
public class AdminBootstrap implements CommandLineRunner {
    private final UserService userService;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    /**
     * У {@code admin.email} и {@code admin.password} намеренно нет значений по умолчанию:
     * иначе приложение создало бы администратора с заранее известными учётными данными,
     * если переменные окружения не заданы.
     */
    public AdminBootstrap(
            UserService userService,
            @Value("${admin.username:admin}") String adminUsername,
            @Value("${admin.email:}") String adminEmail,
            @Value("${admin.password:}") String adminPassword) {
        this.userService = userService;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    /**
     * Делегирует {@link UserService#createUser(CreateUserRequest)}
     * хеширование пароля перед сохранением в БД.
     */
    @Override
    public void run(String... args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("Admin credentials not configured, skipping admin bootstrap");
            return;
        }

        if (!userService.adminExists()) {
            userService.createUser(new CreateUserRequest(
                    adminUsername,
                    adminPassword,
                    adminEmail,
                    Role.ADMIN
            ));
            log.info("Admin user created: {}", adminEmail);
        }
    }
}
