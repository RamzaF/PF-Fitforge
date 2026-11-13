package com.fitforge.service;

import java.util.List;
import java.util.Set;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.domain.Producto;
import com.fitforge.domain.Color;
import com.fitforge.domain.Talla;

/**
 *
 * @author ktrejos
 */
public interface ProductoColorTallaService {
    List<ProductoColorTalla> listarProductoColorTalla();
    List<ProductoColorTalla> getByProducto(Producto producto);
    List<ProductoColorTalla> listarPorProducto(Long idProducto);
    ProductoColorTalla getProductoColorTalla(Long id);
    void guardar(ProductoColorTalla productoColorTalla);
    ProductoColorTalla guardarVariante(Long idProducto, Long idColor, Long idTalla, int existencias);
    void cambiarEstado(Long idVariante, boolean activo);
    void eliminar(Long id);
    Set<Long> variantesVendidasDeProducto(Long idProducto);
    List<Color> listarColores();
    List<Color> listarColoresActivos();
    List<Talla> listarTallas();
    List<Talla> listarTallasActivas();
}
