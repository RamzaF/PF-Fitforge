/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.fitforge.domain;

/**
 *
 * @author Eduin M
 */
import lombok.Data;

@Data
public class Item {
    private ProductoColorTalla productoColorTalla;
    private int cantidad;

    public Item() {
    }

    public Item(ProductoColorTalla pct) {
        this.productoColorTalla = pct;
        this.cantidad = 1;
    }

    public double getSubtotal() {
        return this.productoColorTalla.getProducto().getPrecio() * this.cantidad;
    }
}