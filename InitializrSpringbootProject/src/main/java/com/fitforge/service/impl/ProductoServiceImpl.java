package com.fitforge.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitforge.dao.ProductoDao;
import com.fitforge.domain.Producto;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.service.ProductoService;
/**
  *
  * @author bryam
  */
@Service
public class ProductoServiceImpl implements ProductoService {

    @Autowired
    private ProductoDao productoDao;

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
        boolean esNuevo = (producto.getIdProducto() == null || producto.getIdProducto() == 0);
        
        // Validar datos básicos
        if (producto.getDescripcion() == null || producto.getDescripcion().trim().isEmpty()) {
            throw new IllegalArgumentException("La descripción del producto no puede estar vacía");
        }
        
        // Asegurar que el precio no sea negativo
        if (producto.getPrecio() < 0) {
            producto.setPrecio(0.0);
        }
        
        // Asegurar que la categoría esté correctamente configurada
        if (producto.getCategoria() != null && producto.getCategoria().getIdCategoria() == 0) {
            producto.setCategoria(null); // Evitar guardar categorías inválidas
        }
        
        // Inicializar colecciones si es necesario
        if (producto.getProductoColorTallas() == null) {
            producto.setProductoColorTallas(new ArrayList<>());
        }
        
        // Validar y mantener relaciones bidireccionales para ProductoColorTalla
        for (ProductoColorTalla pct : producto.getProductoColorTallas()) {
            if (pct.getProducto() == null) {
                pct.setProducto(producto);
            }
        }
        
        // La existencia se calcula automáticamente en getExistencias()
        // No es necesario el setter, pero mantener la validación por compatibilidad
        if (producto.getExistencias() < 0) {
            producto.setExistencias(0);
        }
        
        // El AdminProductoController se encarga de inicializar un ProductoColorTalla para 
        // productos nuevos después de la primera persistencia, por lo que aquí solo 
        // nos aseguramos de guardar el producto correctamente
        
        // Guardar el producto con todas sus relaciones
        productoDao.save(producto);
    }

    @Override
    @Transactional
    public void eliminarProducto(Long id) {
        // Implementar soft delete en lugar de borrado físico
        var producto = productoDao.findById(id);
        if (producto.isPresent()) {
            Producto productoEntity = producto.get();
            productoEntity.setActivo(false);
            productoDao.save(productoEntity);
        }
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
        return productoDao.findByCategoria_Descripcion(nombreCategoria);
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