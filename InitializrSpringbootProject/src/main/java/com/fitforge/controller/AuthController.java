package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fitforge.model.User;
import com.fitforge.service.AuthService;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username, 
                          @RequestParam String nombre, 
                          @RequestParam String correo, 
                          @RequestParam String password, 
                          @RequestParam String confirmPassword, 
                          Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Las contraseñas no coinciden");
            return "register";
        }
        User user = new User();
        user.setUsername(username);
        user.setNombre(nombre);
        user.setCorreo(correo);
        user.setPassword(password);
        try {
            authService.register(user);
            return "redirect:/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, Model model) {
        //authService.sendResetEmail(email);
        model.addAttribute("message", "Se ha enviado un enlace para restablecer tu contraseña.");
        return "forgot-password";
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }
}