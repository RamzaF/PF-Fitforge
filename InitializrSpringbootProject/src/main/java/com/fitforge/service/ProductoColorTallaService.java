package com.fitforge.service;

import java.util.List;
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
    ProductoColorTalla getProductoColorTalla(Long id);
    void guardar(ProductoColorTalla productoColorTalla);
    void eliminar(Long id);
    List<Color> listarColores();
    List<Talla> listarTallas();
}
