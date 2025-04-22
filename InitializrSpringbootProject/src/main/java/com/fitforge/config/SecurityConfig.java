package com.fitforge.app.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Bean
    public UserDetailsService userDetailsService() {
        // Usuarios en memoria
        UserDetails admin = User.builder()
                .username("admin@fitforge.com")
                .password(passwordEncoder().encode("admin123"))
                .roles("ADMIN")
                .build();

        UserDetails user = User.builder()
                .username("user@fitforge.com")
                .password(passwordEncoder().encode("user123"))
                .roles("USER")
                .build();

        InMemoryUserDetailsManager inMemoryManager = new InMemoryUserDetailsManager(admin, user);

        // Servicio personalizado que combina memoria y base de datos
        return username -> {
            // Primero intenta autenticar con usuarios en memoria
            try {
                UserDetails inMemoryUser = inMemoryManager.loadUserByUsername(username);
                if (inMemoryUser != null) {
                    return inMemoryUser;
                }
            } catch (UsernameNotFoundException e) {
                // Si no está en memoria, busca en la base de datos
                String sql = "SELECT u.correo, u.password, u.activo, r.nombre " +
                             "FROM usuario u LEFT JOIN rol r ON u.id_usuario = r.id_usuario " +
                             "WHERE u.correo = ?";
                List<UserDetails> dbUsers = jdbcTemplate.query(sql, new Object[]{username}, (rs, rowNum) -> {
                    String correo = rs.getString("correo");
                    String password = rs.getString("password");
                    boolean activo = rs.getBoolean("activo");
                    String role = rs.getString("nombre");

                    if (role == null) {
                        throw new UsernameNotFoundException("Usuario sin roles: " + correo);
                    }

                    return new User(
                            correo,
                            password,
                            activo,
                            true, true, true,
                            Arrays.asList(new SimpleGrantedAuthority(role))
                    );
                });

                if (!dbUsers.isEmpty()) {
                    return dbUsers.get(0);
                }
            }

            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/admin/**", "/usuario/**").hasRole("ADMIN")
                .requestMatchers("/carrito/**", "/pedidos/**", "/perfil").hasRole("USER")
                .requestMatchers("/", "/login", "/register", "/forgot-password", "/tienda/**", "/categoria/**", 
                                "/nosotros", "/contacto", "/css/**", "/js/**", "/img/**", "/webjars/**", 
                                "/Uploads/**").permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    boolean isAdmin = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    if (isAdmin) {
                        response.sendRedirect("/admin/productos/listado");
                    } else {
                        response.sendRedirect("/");
                    }
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf().disable();

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/Uploads/**");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}