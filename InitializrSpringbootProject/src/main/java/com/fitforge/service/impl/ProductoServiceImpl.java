package com.fitforge.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitforge.dao.ProductoDao;
import com.fitforge.domain.Producto;
import com.fitforge.service.ProductoService;
/**
  *
  * @author bryam
  */
@Service
public class ProductoServiceImpl implements ProductoService {

    @Autowired private ProductoDao productoDao;
    @Autowired private com.fitforge.dao.PedidoInventarioDao inventario;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public List<Producto> listarProductos() {
        return productoDao.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> getProductos(boolean activos) {
        var lista = productoDao.findAll();
        if (activos) {
            lista = lista.stream()
                    .filter(Producto::isActivo)
                    .collect(Collectors.toList());
        }
        return lista;
    }

    @Override
    @Transactional(readOnly = true)
    public Producto getProducto(Producto producto) {
        return productoDao.findById(producto.getIdProducto()).orElse(null);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Producto obtenerProductoPorId(Long id) {
        return productoDao.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void guardarProducto(Producto producto) {
        if(producto.getDescripcion()==null || producto.getDescripcion().isBlank())
            throw new IllegalArgumentException("La descripción del producto no puede estar vacía");
        com.fitforge.service.Dinero.desdeDouble(producto.getPrecio());
        boolean nuevo=producto.getIdProducto()==null || producto.getIdProducto()==0;
        if(nuevo) {
            // Only catalog scalar fields; never merge a browser-supplied inventory graph.
            var fresh=new Producto(producto.getDescripcion(),producto.getDetalle(),producto.getRutaImagen(),producto.getPrecio(),producto.isActivo());
            fresh.setCategoria(producto.getCategoria());
            productoDao.saveAndFlush(fresh); producto.setIdProducto(fresh.getIdProducto());
        } else {
            if(inventario.bloquearProducto(producto.getIdProducto())==null) throw new IllegalArgumentException("Producto no disponible");
            // Scalar SQL deliberately avoids cascade merge/dirty checking of stale EAGER stock.
            jdbc.update("UPDATE producto SET descripcion=?, detalle=?, imagen=?, precio=?, activo=?, id_categoria=? WHERE id_producto=?",
                producto.getDescripcion(),producto.getDetalle(),producto.getRutaImagen(),producto.getPrecio(),producto.isActivo(),
                producto.getCategoria()==null?null:producto.getCategoria().getIdCategoria(),producto.getIdProducto());
        }
    }

    @Override
    @Transactional
    public void eliminarProducto(Long id) {
        if(inventario.bloquearProducto(id)!=null) jdbc.update("UPDATE producto SET activo=false WHERE id_producto=?",id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Producto getProductoById(Long id) {
        return productoDao.findById(id)
                .map(producto -> {
                    // Forzar la carga de la categoría
                    Hibernate.initialize(producto.getCategoria());
                    return producto;
                })
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> obtenerPorCategoria(String nombreCategoria) {
        // Obtener productos por categoría y luego filtrar solo los activos
        List<Producto> productos = productoDao.findByCategoria_Descripcion(nombreCategoria);
        
        // No necesitamos filtrar aquí ya que ahora filtramos en el controlador
        return productos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> buscarProductos(String termino) {
    if (termino == null || termino.trim().isEmpty()) {
        return productoDao.findAll();
    }
     return productoDao.buscarPorTermino(termino);
    }
 
}