package io.flashcard.controller;

import io.flashcard.config.AdminProperties;
import io.flashcard.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminProperties adminProperties;
    private final JwtService jwtService;

    public AdminAuthController(AdminProperties adminProperties, JwtService jwtService) {
        this.adminProperties = adminProperties;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));
        }

        if (!adminProperties.getUsername().equals(username) || !adminProperties.getPassword().equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        String token = jwtService.generateToken(username);
        return ResponseEntity.ok(Map.of("token", token));
    }
}
