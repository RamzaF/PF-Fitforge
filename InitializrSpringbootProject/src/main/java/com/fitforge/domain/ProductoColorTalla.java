package com.fitforge.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "producto_color_talla")
public class ProductoColorTalla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto_color_talla")
    private Long idProductoColorTalla;

    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "id_color")
    private Color color;

    @ManyToOne
    @JoinColumn(name = "id_talla")
    private Talla talla;

    @Column(name = "existencias")
    private Integer existencias;

    // Getters y setters
    public Long getIdProductoColorTalla() {
        return idProductoColorTalla;
    }

    public void setIdProductoColorTalla(Long idProductoColorTalla) {
        this.idProductoColorTalla = idProductoColorTalla;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Talla getTalla() {
        return talla;
    }

    public void setTalla(Talla talla) {
        this.talla = talla;
    }

    public Integer getExistencias() {
        return existencias;
    }

    public void setExistencias(Integer existencias) {
        this.existencias = existencias;
    }
}
