package com.huy.enterprise.demo;

import com.huy.enterprise.monitoring.RiskMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataRunner implements ApplicationRunner {
    private final DemoDataset dataset;
    private final RiskMonitoringService monitoring;
    @Value("${app.demo.password:}") private String password;

    @Override
    public void run(ApplicationArguments args) {
        boolean created = dataset.initialize(password);
        // Commit the complete fixture transaction before asking JPA to run real rules.
        // A restart keeps user edits and does not recreate rows or reset passwords.
        if (created) monitoring.runMonitoring();
        log.info("DEMO DATASET READY: {}. Accounts: demo_admin, demo_manager, demo_analyst, demo_staff. Passwords are never logged.",
                created ? "created isolated fixture snapshot" : "existing snapshot retained");
    }
}
