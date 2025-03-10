package com.fitforge.dao;
/**
 *
 * @author bryam
 */
import com.fitforge.domain.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoDao extends JpaRepository<Producto, Long> {
    Producto findByDescripcion(String descripcion);
}
