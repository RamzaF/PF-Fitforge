package com.fitforge.dao;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author bryam
 */
import com.fitforge.domain.Producto;

public interface ProductoDao extends JpaRepository<Producto, Long> {
    Producto findByDescripcion(String descripcion);

    //buscar productos por cat
   List<Producto> findByCategoria_Descripcion(String nombre);
}
