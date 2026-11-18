package com.fitforge.dao;

import com.fitforge.domain.Producto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * @author bryam
 */
public interface ProductoDao extends JpaRepository<Producto, Long> {
    Producto findByDescripcion(String descripcion);

    List<Producto> findByCategoria_Descripcion(String nombre);

    List<Producto> findByCategoria_DescripcionIgnoreCase(String nombre);

    @Query("SELECT MIN(p.precio) FROM Producto p WHERE p.activo = true AND p.categoria.activo = true")
    Double findMinPrecio();

    @Query("SELECT MAX(p.precio) FROM Producto p WHERE p.activo = true AND p.categoria.activo = true")
    Double findMaxPrecio();

    @Query("SELECT p FROM Producto p WHERE " +
           "LOWER(p.descripcion) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(p.detalle) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(p.categoria.descripcion) LIKE LOWER(CONCAT('%', :termino, '%'))")
    List<Producto> buscarPorTermino(@Param("termino") String termino);
}
