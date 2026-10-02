package io.flashcard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    private String username = "admin";
    private String password = "changeme";
    private String jwtSecret = "default-secret-change-in-production";

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }

    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String v) { this.jwtSecret = v; }
}
