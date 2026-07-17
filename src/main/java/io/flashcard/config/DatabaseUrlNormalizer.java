package io.flashcard.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts DATABASE_URL from postgresql:// format to jdbc:postgresql:// format
 * so both HikariCP and Liquibase work correctly.
 */
@Slf4j
public class DatabaseUrlNormalizer implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbUrl = environment.getProperty("DATABASE_URL");
        if (dbUrl == null || dbUrl.startsWith("jdbc:")) return;

        try {
            // postgresql://user:pass@host:5432/dbname → jdbc:postgresql://host:5432/dbname
            URI uri = new URI(dbUrl);
            String userInfo = uri.getUserInfo(); // user:pass
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath(); // /dbname

            String jdbcUrl = "jdbc:postgresql://" + host + (port > 0 ? ":" + port : "") + path;

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            // Liquibase needs its own URL explicitly
            props.put("spring.liquibase.url", jdbcUrl);

            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                props.put("spring.datasource.username", parts[0]);
                props.put("spring.liquibase.user", parts[0]);
                if (parts.length > 1) {
                    props.put("spring.datasource.password", parts[1]);
                    props.put("spring.liquibase.password", parts[1]);
                }
            }

            log.info("[db-url-normalizer] Converted DATABASE_URL to JDBC format: {}", jdbcUrl);

            environment.getPropertySources().addFirst(
                new MapPropertySource("databaseUrlNormalized", props)
            );
        } catch (Exception e) {
            log.error("[db-url-normalizer] Failed to parse DATABASE_URL: {}", e.getMessage());
        }
    }
}
