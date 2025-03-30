package com.fitforge.service;

import java.util.List;

import com.fitforge.domain.Producto;

public interface ProductoService {
    List<Producto> listarProductos();
    List<Producto> getProductos(boolean activos); 
    List<Producto> obtenerPorCategoria(String nombreCategoria);
    List<Producto> buscarProductos(String termino);
    Producto getProducto(Producto producto);
    Producto obtenerProductoPorId(Long id);
    void guardarProducto(Producto producto);
    void eliminarProducto(Long id);
    Producto getProductoById(Long id); 
}


