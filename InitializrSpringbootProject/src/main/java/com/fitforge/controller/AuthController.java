package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String nombre,
            @RequestParam String correo,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Las contraseñas no coinciden");
            return "register";
        }

        // Verificar si el username ya existe
        Integer usernameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE username = ?", Integer.class, username);
        if (usernameCount != null && usernameCount > 0) {
            model.addAttribute("error", "El nombre de usuario ya está en uso.");
            return "register";
        }

        // Verificar si el correo ya existe
        Integer correoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE correo = ?", Integer.class, correo);
        if (correoCount != null && correoCount > 0) {
            model.addAttribute("error", "El correo ya está registrado.");
            return "register";
        }

        // Encriptar la contraseña
        String encodedPassword = passwordEncoder.encode(password);

        // Insertar el usuario
        jdbcTemplate.update(
                "INSERT INTO usuario (username, password, nombre, correo, activo) VALUES (?, ?, ?, ?, ?)",
                username, encodedPassword, nombre, correo, true);

        // Obtener el ID del usuario recién creado
        Integer userId = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuario WHERE username = ?", Integer.class, username);

        // Insertar el rol ROLE_USER
        jdbcTemplate.update(
                "INSERT INTO rol (nombre, id_usuario) VALUES (?, ?)",
                "ROLE_USER", userId);

        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Recuperación de contraseña no disponible en esta demo.");
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }

    @GetMapping("/admin")
    public String adminPanel(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return "admin";
        }
        return "redirect:/";
    }
}