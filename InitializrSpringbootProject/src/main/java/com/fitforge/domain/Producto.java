package com.fitforge.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
@Entity
@Table(name = "producto")
public class Producto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long idProducto;
    private String rutaImagen;
   /* 
    @Column(name = "id_categoria")
    private Long idCategoria;
*/
    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "detalle")
    private String detalle;

    @Column(name = "precio")
    private Double precio; 

    @Column(name = "imagen")
    private String imagen;

    @Column(name = "activo")
    private Boolean activo; 

    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductoColorTalla> productoColorTallas; 

    // Constructor vacío
    public Producto() {
    }
    public Producto(String rutaImagen) {
        this.rutaImagen = rutaImagen;
    }

    public Producto(String descripcion, String detalle, Double precio, String imagen, Boolean activo) {
        this.descripcion = descripcion;
        this.detalle = detalle;
        this.precio = precio;
        this.imagen = imagen;
        this.activo = activo;
    }
    

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
    public void setImagen(String imagen) {
    this.imagen = imagen;
}
    public void setRutaImagen(String rutaImagen) {
         this.rutaImagen = rutaImagen;
     }
    
    public Long getIdProducto() {
    return idProducto;
}
}
