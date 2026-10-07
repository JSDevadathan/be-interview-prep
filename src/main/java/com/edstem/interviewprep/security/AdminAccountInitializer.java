package com.edstem.interviewprep.security;

import com.edstem.interviewprep.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Registration only ever creates USER accounts, so the first ADMIN is created at startup from configuration.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AdminAccountProperties adminAccountProperties;
    private final UserService userService;

    public AdminAccountInitializer(AdminAccountProperties adminAccountProperties, UserService userService) {
        this.adminAccountProperties = adminAccountProperties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!adminAccountProperties.isConfigured()) {
            log.info("No admin account configured; set ADMIN_USERNAME and ADMIN_PASSWORD to create one");
            return;
        }
        boolean isCreated = userService.createAdminIfAbsent(
                adminAccountProperties.username(), adminAccountProperties.password());
        if (isCreated) {
            log.info("Created admin account {}", adminAccountProperties.username());
        }
    }
}
