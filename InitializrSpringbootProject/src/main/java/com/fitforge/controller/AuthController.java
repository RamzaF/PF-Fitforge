package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fitforge.model.User; // Importa tu modelo User
import com.fitforge.service.impl.AuthService;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder; // Inyecta el PasswordEncoder de SecurityConfig

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User()); // Añade el objeto User al modelo para el formulario
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, 
                         @RequestParam String email, 
                         @RequestParam String password, 
                         @RequestParam String confirmPassword, 
                         Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Las contraseñas no coinciden");
            return "register";
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password)); // Codifica la contraseña
        user.setActive(true); // Activa el usuario por defecto
        authService.register(user.getEmail(), user.getPassword()); // Usa el método existente del servicio (ajústalo si necesitas pasar el User completo)
        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        authService.sendResetEmail(email);
        model.addAttribute("message", "Se ha enviado un enlace para restablecer tu contraseña.");
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token, @RequestParam String password) {
        authService.resetPassword(token, password);
        return "redirect:/login";
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}