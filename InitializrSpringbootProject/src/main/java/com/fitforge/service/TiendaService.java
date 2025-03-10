package com.fitforge.service;

import com.fitforge.domain.Producto;
import java.util.List;
/**
 *
 * @author bryam
 */
public interface TiendaService{

    public List<Producto> getProducto(boolean activos);
    public Producto getProducto(Producto producto);
    public void save(Producto producto);
    public void delete(Producto producto);
}


