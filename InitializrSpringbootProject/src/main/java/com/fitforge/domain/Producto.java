/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.fitforge.domain;

/**
 *
 * @author ktrejos
 */

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
    @Column(name = "id_producto")
    private Long idProducto;

    @Column(name = "id_categoria")
    private Long idCategoria;

    private String descripcion;
    private String detalle;
    private Double precio;
    private Integer existencias;

    @Column(name = "ruta_imagen")
    private String rutaImagen;

    private Boolean activo;

    public Producto() {
    }

    public Producto(Long idCategoria, String descripcion, String detalle, Double precio, Integer existencias, String rutaImagen, Boolean activo) {
        this.idCategoria = idCategoria;
        this.descripcion = descripcion;
        this.detalle = detalle;
        this.precio = precio;
        this.existencias = existencias;
        this.rutaImagen = rutaImagen;
        this.activo = activo;
    }
}
