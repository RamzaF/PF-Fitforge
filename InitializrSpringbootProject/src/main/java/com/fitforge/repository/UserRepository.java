package com.fitforge.repository;

import com.fitforge.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    boolean existsByCorreo(String correo);
    User findByCorreo(String correo);
}