package io.flashcard.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Converts Node.js-style DATABASE_URL (postgresql://user:pass@host:5432/db)
 * to a JDBC DataSource. Falls back to standard Spring datasource config
 * if DATABASE_URL is not set.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "DATABASE_URL")
    public DataSource databaseUrlDataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null) {
            databaseUrl = System.getProperty("DATABASE_URL");
        }

        URI uri = URI.create(databaseUrl);
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
            + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
            + uri.getPath();

        String userInfo = uri.getUserInfo();
        String username = null;
        String password = null;
        if (userInfo != null && userInfo.contains(":")) {
            String[] parts = userInfo.split(":", 2);
            username = parts[0];
            password = parts[1];
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        if (username != null) ds.setUsername(username);
        if (password != null) ds.setPassword(password);
        ds.setMaximumPoolSize(20);
        ds.setIdleTimeout(30_000);
        ds.setConnectionTimeout(10_000);
        return ds;
    }
}
