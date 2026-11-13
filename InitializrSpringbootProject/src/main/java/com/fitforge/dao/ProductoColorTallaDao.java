package com.fitforge.dao;

import com.fitforge.domain.ProductoColorTalla;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author bryam
 */
public interface ProductoColorTallaDao extends JpaRepository<ProductoColorTalla, Long> {
    Optional<ProductoColorTalla> findByProducto_IdProductoAndColor_IdColorAndTalla_IdTalla(
            Long idProducto, Long idColor, Long idTalla);
    List<ProductoColorTalla> findByProducto_IdProductoOrderByIdProductoColorTallaAsc(Long idProducto);
}
