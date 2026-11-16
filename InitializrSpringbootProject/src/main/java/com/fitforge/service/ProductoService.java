package com.fitforge.service;

import java.util.List;

import com.fitforge.domain.Producto;
/**
  *
  * @author bryam
  */
public interface ProductoService {
    List<Producto> listarProductos();
    List<Producto> getProductos(boolean activos);
    List<Producto> obtenerPorCategoria(String nombreCategoria);
    List<Producto> buscarProductos(String termino);
    Producto getProducto(Producto producto);
    Producto obtenerProductoPorId(Long id);
    void guardarProducto(Producto producto);
    Producto guardarProductoConVarianteInicial(Producto producto, Long idColor, Long idTalla, Integer existencias);
    void cambiarEstado(Long id, boolean activo);
    void eliminarProducto(Long id);
    Producto getProductoById(Long id);
    int contarProductosPorImagen(String rutaImagen);
}
