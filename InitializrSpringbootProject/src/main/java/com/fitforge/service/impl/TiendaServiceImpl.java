package com.fitforge.service.impl;
/**
 *
 * @author bryam
 */
import com.fitforge.dao.ProductoDao;
import com.fitforge.domain.Producto;
import com.fitforge.service.TiendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TiendaServiceImpl implements TiendaService {

    @Autowired
    private ProductoDao productoDao;

    @Override
    @Transactional(readOnly = true)
    public List<Producto> getProducto(boolean activos) {
        var lista = productoDao.findAll();
        if (activos) {
            lista = lista.stream()
                    .filter(Producto::isActivo) // Cambiado a isActivo
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
    @Transactional
    public void save(Producto producto) {
        productoDao.save(producto);
    }

    @Override
    @Transactional
    public void delete(Producto producto) {
        productoDao.delete(producto);
    }
}