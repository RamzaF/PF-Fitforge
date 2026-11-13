package com.fitforge.domain;
/**
 *
 * @author bryam
 */
import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Data
@Entity
@Table(name = "talla")
public class Talla implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_talla")
    private Long idTalla;

    @Column(name = "nombre_talla", nullable = false, length = 50)
    private String nombreTalla;

    @Column(nullable = false)
    private boolean activo = true;

    public Talla() {
    }
}
