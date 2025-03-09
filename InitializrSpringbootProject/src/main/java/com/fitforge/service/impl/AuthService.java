package com.fitforge.service;

import com.fitforge.model.User;
import com.fitforge.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User register(String email, String password) {
        if (userRepository.findByEmail(email) != null) {
            throw new RuntimeException("El email ya está registrado.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }

    public void sendResetEmail(String email) {
        User user = userRepository.findByEmail(email);
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

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token);
        if (user == null) {
            throw new RuntimeException("Token inválido.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        userRepository.save(user);
    }
}