package com.fitforge.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "producto_color_talla")
public class ProductoColorTalla implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto_color_talla")
    private Long idProductoColorTalla;
    
    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;
    
    private String color;
    private String talla;
    private int existencias;
    
    public ProductoColorTalla() {
    }
    
    public ProductoColorTalla(Producto producto, String color, String talla, int existencias) {
        this.producto = producto;
        this.color = color;
        this.talla = talla;
        this.existencias = existencias;
    }
}
