package com.fitforge.domain;
/**
 *
 * @author bryam
 */
import lombok.Data;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;

@Data
@Entity
@Table(name = "contacto")
public class Contacto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contacto")
    private Long idContacto;

    @NotEmpty(message = "El nombre es obligatorio")
    private String nombre;

    @NotEmpty(message = "El apellido es obligatorio")
    private String apellido;

    @NotEmpty(message = "El correo es obligatorio")
    private String correo;

    @NotEmpty(message = "El mensaje es obligatorio")
    private String mensaje;

    private boolean activo;

    public Contacto() {
    }
}
