package com.fitforge.domain;

import lombok.Data;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author bryam
 */
@Data
@Entity
@Table(name = "contacto")
public class Contacto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contacto")
    private Long idContacto;

    private String nombre;
    private String apellido;
    private String correo;
    private String mensaje;
    private boolean activo;

    @OneToMany
    @JoinColumn(name = "id_contacto")
    private List<Contacto> contactos;

    public Contacto() {
    }

    public Contacto(Long idContacto, String nombre, String apellido, String correo, String mensaje, boolean activo) {
        this.idContacto = idContacto;
        this.nombre = nombre;
        this.apellido = apellido;
        this.correo = correo;
        this.mensaje = mensaje;
        this.activo = activo;
    }

    public Long getIdContacto() {
        return idContacto;
    }

    public void setIdContacto(Long idContacto) {
        this.idContacto = idContacto;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
