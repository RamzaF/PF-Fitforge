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
@Table(name = "color")
public class Color implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_color")
    private Long idColor;

    @Column(name = "nombre_color", nullable = false, length = 50)
    private String nombreColor;

    @Column(nullable = false)
    private boolean activo = true;

    public Color() {
    }
}
