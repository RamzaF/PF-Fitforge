package com.fitforge.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Data
@Entity
@Table(name = "producto")
public class Producto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_Producto")
    private long idProducto;

    private String descripcion;
    private String detalle;
    private int precio;
    private int existencias;

    @Column(name = "imagen")
    private String imagen;

    private boolean activo;

    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    public Producto() {
    }

    public Producto(String descripcion, String detalle, int precio, int existencias, String imagen, boolean activo) {
        this.descripcion = descripcion;
        this.detalle = detalle;
        this.precio = precio;
        this.existencias = existencias;
        this.imagen = imagen;
        this.activo = activo;
    }

    public long getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(long idProducto) {
        this.idProducto = idProducto;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
