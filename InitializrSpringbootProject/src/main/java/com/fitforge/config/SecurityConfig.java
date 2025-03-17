package com.fitforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/register", "/forgot-password").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**").permitAll()
                        .anyRequest().permitAll())
                // Login        
                // .formLogin(form -> form
                //         .loginPage("/login")
                //         // .defaultSuccessUrl("/", true)  Redirige a / (index)
                //         .defaultSuccessUrl("/?loginSuccess", true) // Agrega ?loginSuccess
                //         .permitAll())

                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            response.sendRedirect("/?loginSuccess=true");
                        })
                        .permitAll()
)



                // Logout        
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        // .logoutSuccessUrl("/")
                        .logoutSuccessUrl("/login?logout") // Redirige al login
                        .permitAll())
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}