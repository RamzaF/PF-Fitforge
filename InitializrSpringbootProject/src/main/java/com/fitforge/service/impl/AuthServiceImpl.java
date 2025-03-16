package com.fitforge.service.impl;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.fitforge.model.User;
import com.fitforge.repository.UserRepository;
import com.fitforge.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void register(String email, String password) {
        if (userRepository.findByCorreo(email) != null) {
            throw new RuntimeException("El correo ya está registrado.");
        }
        User user = new User();
        user.setCorreo(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setActivo(true);
        userRepository.save(user);
    }

    @Override
    public void register(User user) {
        if (userRepository.findByCorreo(user.getCorreo()) != null) {
            throw new RuntimeException("El correo ya está registrado.");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActivo(true);
        userRepository.save(user);
    }

    @Override
    public void sendResetEmail(String email) {
        User user = userRepository.findByCorreo(email);
        if (user == null) {
            throw new RuntimeException("Usuario no encontrado.");
        }
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        userRepository.save(user);

        String resetLink = "http://localhost:8080/reset-password?token=" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Restablecer Contraseña");
        message.setText("Haz clic aquí para restablecer tu contraseña: " + resetLink);
        mailSender.send(message);
    }

    // Comentamos este método porque no lo necesitas ahora
    /*
    @Override
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token);
        if (user == null) {
            throw new RuntimeException("Token inválido.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        userRepository.save(user);
    }
    */
}