package com.fitforge.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UsuarioController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/usuario/nuevo")
    public String mostrarFormulario() {
        return "usuario-nuevo";
    }

    @PostMapping("/usuario/guardar")
    public String guardarAdmin(
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam("nombre") String nombre,
            @RequestParam("correo") String correo,
            Model model) {
        // Verificar si el username ya existe
        Integer usernameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE username = ?", Integer.class, username);
        if (usernameCount != null && usernameCount > 0) {
            model.addAttribute("errorUsername", "El nombre de usuario ya está en uso.");
            return "usuario-nuevo";
        }

        // Verificar si el correo ya existe
        Integer correoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE correo = ?", Integer.class, correo);
        if (correoCount != null && correoCount > 0) {
            model.addAttribute("errorCorreo", "El correo ya está registrado.");
            return "usuario-nuevo";
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

        // Insertar el rol ROLE_ADMIN
        jdbcTemplate.update(
                "INSERT INTO rol (nombre, id_usuario) VALUES (?, ?)",
                "ROLE_ADMIN", userId);

        return "redirect:/admin";
    }
}
