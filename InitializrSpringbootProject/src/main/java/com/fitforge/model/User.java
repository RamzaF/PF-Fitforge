package com.fitforge.model;

import java.io.Serializable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient; // Importamos Transient
import lombok.Data;

@Data
@Entity
@Table(name = "usuario")
public class User implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idUsuario;
    
    private String username;
    private String nombre;
    private String correo;
    private String password;
    private String telefono;
    private String avatar;
    
    @Transient // Ignoramos este campo en la base de datos
    private String resetToken;
    
    private boolean activo;
}