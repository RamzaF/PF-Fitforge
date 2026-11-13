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

    public boolean isDisponible() {
        return productoColorTalla != null
            && productoColorTalla.isActivo()
            && productoColorTalla.getProducto() != null
            && productoColorTalla.getProducto().isActivo()
            && (productoColorTalla.getProducto().getCategoria() == null
                || productoColorTalla.getProducto().getCategoria().isActivo());
    }

    public double getSubtotal() {
        if (!isDisponible()) {
            return 0;
        }
        return productoColorTalla.getProducto().getPrecio() * cantidad;
    }

    @Override
    public String toString() {
        return "Item{productoColorTallaId=" +
               (productoColorTalla != null ? productoColorTalla.getIdProductoColorTalla() : "null") +
               ", cantidad=" + cantidad + '}';
    }
}