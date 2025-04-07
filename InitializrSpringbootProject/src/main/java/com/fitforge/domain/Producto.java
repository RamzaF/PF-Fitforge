package com.fitforge.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "producto")
public class Producto implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long idProducto;
    
    private String descripcion;
    private String detalle;
    
    @Column(name = "imagen")
    private String rutaImagen;
    
    private double precio;
    private boolean activo;
    
    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;
    
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ProductoColorTalla> productoColorTallas = new ArrayList<>();
    
    public Producto() {
    }

    public Producto(String descripcion, String detalle, String rutaImagen, double precio, boolean activo) {
        this.descripcion = descripcion;
        this.detalle = detalle;
        this.rutaImagen = rutaImagen;
        this.precio = precio;
        this.activo = activo;
    }
    
    // Métodos para compatibilidad con código existente
    
    /**
     * Método setter alternativo para mantener compatibilidad con código antiguo
     * @param imagen ruta de la imagen del producto
     */
    public void setImagen(String imagen) {
        this.rutaImagen = imagen;
    }
    
    /**
     * Método getter alternativo para mantener compatibilidad con código antiguo
     * @return ruta de la imagen del producto
     */
    public String getImagen() {
        return this.rutaImagen;
    }
    
    // Calculate total existencias from productoColorTallas
    public int getExistencias() {
        return productoColorTallas.stream()
                .mapToInt(pct -> pct.getExistencias())
                .sum();
    }
    
    // No-op setter for compatibility
    public void setExistencias(int existencias) {
        // No-op as existencias is calculated from productoColorTallas
    }

    
}
