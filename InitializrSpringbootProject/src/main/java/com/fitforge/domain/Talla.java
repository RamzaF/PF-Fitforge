package com.fitforge.domain;

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
    private long idTalla;
    
    @Column(name = "nombre_talla")
    private String nombreTalla;

    public Talla() {
    }

    public Long getIdTalla() {
        return idTalla;
    }

    public void setIdTalla(Long idTalla) {
        this.idTalla = idTalla;
    }

    public String getNombreTalla() {
        return nombreTalla;
    }

    public void setNombreTalla(String nombreTalla) {
        this.nombreTalla = nombreTalla;
    }
}
