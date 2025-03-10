package com.fitforge.service;

import com.fitforge.domain.Producto;
import java.util.List;
/**
 *
 * @author bryam
 */
public interface ProductoService {
    List<Producto> getProductos(boolean activos); 
    Producto getProducto(Producto producto);
    void save(Producto producto);
    void delete(Producto producto);
    Producto getProductoById(Long id); 
}


