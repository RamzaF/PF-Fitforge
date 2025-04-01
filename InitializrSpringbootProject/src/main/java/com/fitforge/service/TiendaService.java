package com.fitforge.service;

import com.fitforge.domain.Producto;
import com.fitforge.domain.Color;
import com.fitforge.domain.Talla;
import java.util.List;
/**
  *
  * @author bryam
  */
public interface TiendaService {
    List<Producto> getProducto(boolean activos);
    Producto getProducto(Producto producto);
    void save(Producto producto);
    //void deleteById (Producto producto);
    void delete(Producto producto);
    List<Color> getColores();
    List<Talla> getTallas();
    Double getPrecioMinimo();
    Double getPrecioMaximo();
    List<Producto> getProductosFiltrados(List<Long> categorias, List<Long> colores, List<Long> tallas, Double precioMin, Double precioMax);
    List<Producto> buscarProductos(String termino);
}