package com.fitforge.app.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Bean
    public UserDetailsService userDetailsService() {
        // Demo credentials live only in the isolated database, never in source.
        return username -> {
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

            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.POST, "/carrito/confirmar-pedido").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/carrito/factura/*").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/pedidos", "/pedidos/*").hasRole("USER")
                .requestMatchers("/carrito/confirmar-pedido", "/carrito/factura/**", "/pedidos/**").denyAll()
                .requestMatchers("/admin/**", "/usuario/**", "/api/imagenes/**").hasRole("ADMIN")
                .requestMatchers("/carrito/**").hasRole("USER")
                .requestMatchers("/perfil").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/", "/home", "/login", "/register", "/forgot-password",
                                "/tienda/**", "/productos/**", "/categoria/**", "/nosotros", "/contacto",
                                "/css/**", "/js/**", "/img/**", "/webjars/**", "/Uploads/**", "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/login", "/register", "/forgot-password", "/contacto/enviar").permitAll()
                .anyRequest().denyAll()
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
            .csrf(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
