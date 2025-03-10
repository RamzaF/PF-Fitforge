package com.fitforge.service;

import com.fitforge.domain.Producto;
import java.util.List;

public interface ProductoService {
    List<Producto> listarProductos();
    List<Producto> getProductos(boolean activos); 
    Producto getProducto(Producto producto);
    Producto obtenerProductoPorId(Long id);
    void guardarProducto(Producto producto);
    void eliminarProducto(Long id);
    Producto getProductoById(Long id); 
}
