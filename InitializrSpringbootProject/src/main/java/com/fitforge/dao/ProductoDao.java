package com.fitforge.dao;

import com.fitforge.domain.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param; 

import java.util.List;

/**
 * @author bryam
 */
public interface ProductoDao extends JpaRepository<Producto, Long> {
    Producto findByDescripcion(String descripcion);

    // Buscar productos por categoría
    List<Producto> findByCategoria_Descripcion(String nombre);

    // Consultas para obtener el precio mínimo y máximo
    @Query("SELECT MIN(p.precio) FROM Producto p")
    Double findMinPrecio();

    @Query("SELECT MAX(p.precio) FROM Producto p")
    Double findMaxPrecio();
        @Query("SELECT p FROM Producto p WHERE " +
       "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
       "LOWER(p.detalle) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
       "LOWER(p.categoria.descripcion) LIKE LOWER(CONCAT('%', :termino, '%'))")
            
    List<Producto> buscarPorTermino(@Param("termino") String termino);

}