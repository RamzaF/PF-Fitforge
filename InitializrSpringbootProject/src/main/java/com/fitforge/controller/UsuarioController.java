package com.fitforge.app.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.dao.EmptyResultDataAccessException;

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
        Integer usernameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE username = ?", Integer.class, username);
        if (usernameCount != null && usernameCount > 0) {
            model.addAttribute("errorUsername", "El nombre de usuario ya está en uso.");
            return "usuario-nuevo";
        }

        Integer correoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE correo = ?", Integer.class, correo);
        if (correoCount != null && correoCount > 0) {
            model.addAttribute("errorCorreo", "El correo ya está registrado.");
            return "usuario-nuevo";
        }

        String encodedPassword = passwordEncoder.encode(password);
        jdbcTemplate.update(
                "INSERT INTO usuario (username, password, nombre, correo, activo) VALUES (?, ?, ?, ?, ?)",
                username, encodedPassword, nombre, correo, true);

        Integer userId = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuario WHERE username = ?", Integer.class, username);

        jdbcTemplate.update(
                "INSERT INTO rol (nombre, id_usuario) VALUES (?, ?)",
                "ROLE_ADMIN", userId);

        return "redirect:/admin";
    }

    @GetMapping("/perfil")
    public String mostrarPerfil(Model model) {
        Map<String, String> usuario = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            usuario.put("username", "Desconocido");
            usuario.put("nombre", "Usuario");
            usuario.put("correo", "No autenticado");
        } else {
            String correo = auth.getName();
            try {
                Map<String, Object> result = jdbcTemplate.queryForMap(
                        "SELECT username, nombre, correo FROM usuario WHERE correo = ?",
                        correo
                );
                usuario.put("username", (String) result.get("username"));
                usuario.put("nombre", result.get("nombre") != null ? (String) result.get("nombre") : "Usuario");
                usuario.put("correo", (String) result.get("correo"));
            } catch (EmptyResultDataAccessException e) {
                usuario.put("username", correo);
                usuario.put("nombre", "Usuario");
                usuario.put("correo", correo);
            }
        }

        model.addAttribute("usuario", usuario);
        return "perfil";
    }

    @PostMapping("/perfil")
    public String actualizarPerfil(
            @RequestParam String username,
            @RequestParam String nombre,
            @RequestParam String correo,
            @RequestParam(required = false) String password,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            model.addAttribute("error", "Debes estar autenticado para editar tu perfil.");
            return mostrarPerfil(model);
        }

        String correoActual = auth.getName();

        // Verificar si el username ya existe (excluyendo el usuario actual)
        Integer usernameCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE username = ? AND correo != ?",
                Integer.class, username, correoActual);
        if (usernameCount != null && usernameCount > 0) {
            model.addAttribute("error", "El nombre de usuario ya está en uso.");
            return mostrarPerfil(model);
        }

        // Verificar si el correo ya existe (excluyendo el usuario actual)
        Integer correoCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE correo = ? AND correo != ?",
                Integer.class, correo, correoActual);
        if (correoCount != null && correoCount > 0) {
            model.addAttribute("error", "El correo ya está registrado.");
            return mostrarPerfil(model);
        }

        // Preparar actualización
        String sql = "UPDATE usuario SET username = ?, nombre = ?, correo = ?";
        Object[] params = new Object[]{username, nombre, correo};

        if (password != null && !password.isEmpty()) {
            String encodedPassword = passwordEncoder.encode(password);
            sql += ", password = ?";
            params = new Object[]{username, nombre, correo, encodedPassword};
        }

        sql += " WHERE correo = ?";
        params = java.util.Arrays.copyOf(params, params.length + 1);
        params[params.length - 1] = correoActual;

        jdbcTemplate.update(sql, params);

        // The principal uses the login email. Reauthenticate after identity or
        // password changes instead of retaining a stale authenticated session.
        if (!correo.equals(correoActual) || (password != null && !password.isEmpty())) {
            new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
                    .logout(request, response, auth);
            return "redirect:/login";
        }

        // Actualizar el modelo para reflejar los cambios
        Map<String, String> usuario = new HashMap<>();
        usuario.put("username", username);
        usuario.put("nombre", nombre);
        usuario.put("correo", correo);
        model.addAttribute("usuario", usuario);

        return "perfil";
    }
}
