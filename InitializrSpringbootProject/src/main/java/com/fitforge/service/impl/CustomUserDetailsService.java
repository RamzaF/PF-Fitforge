package com.fitforge.service.impl;

/*
import com.fitforge.model.User;
import com.fitforge.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authorities.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Usuario admin quemado
        if ("admin@fitforge.com".equals(username)) {
            return new org.springframework.security.core.userdetails.User(
                "admin@fitforge.com",
                "$2a$10$XURP2fT/SKPu4./jMSne.uQUSJpo1CVeFmjhS4f6sLdhzXENnYqIW", // "admin123" encriptado
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        }

        // Usuarios de la base de datos
        User user = userRepository.findByCorreo(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new org.springframework.security.core.userdetails.User(
            user.getCorreo(),
            user.getPassword(),
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
*/