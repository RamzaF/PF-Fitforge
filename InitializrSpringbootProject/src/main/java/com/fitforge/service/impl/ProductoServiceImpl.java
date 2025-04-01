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
        if (producto.getExistencias() < 0) {
            producto.setExistencias(0);  
        }
        productoDao.save(producto);
    }

    @Override
    @Transactional
    public void eliminarProducto(Long id) {
        productoDao.deleteById(id);
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