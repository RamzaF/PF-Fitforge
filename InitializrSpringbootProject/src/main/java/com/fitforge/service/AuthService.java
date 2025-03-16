package com.fitforge.service;

import com.fitforge.model.User;

public interface AuthService {
    void register(String email, String password);
    void register(User user);
    void sendResetEmail(String email);
    // void resetPassword(String token, String password); 
}