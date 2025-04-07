package com.fitforge.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitforge.dao.ProductoColorTallaDao;
import com.fitforge.dao.ColorDao;
import com.fitforge.dao.TallaDao;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.domain.Producto;
import com.fitforge.domain.Color;
import com.fitforge.domain.Talla;
import com.fitforge.service.ProductoColorTallaService;

/**
 *
 * @author ktrejos
 */
@Service
public class ProductoColorTallaServiceImpl implements ProductoColorTallaService {

    @Autowired
    private ProductoColorTallaDao productoColorTallaDao;
    
    @Autowired
    private ColorDao colorDao;
    
    @Autowired
    private TallaDao tallaDao;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoColorTalla> listarProductoColorTalla() {
        return productoColorTallaDao.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoColorTalla> getByProducto(Producto producto) {
        return productoColorTallaDao.findAll().stream()
                .filter(pct -> pct.getProducto().getIdProducto().equals(producto.getIdProducto()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoColorTalla getProductoColorTalla(Long id) {
        return productoColorTallaDao.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void guardar(ProductoColorTalla productoColorTalla) {
        productoColorTallaDao.save(productoColorTalla);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        productoColorTallaDao.deleteById(id);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Color> listarColores() {
        return colorDao.findAll();
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<Talla> listarTallas() {
        return tallaDao.findAll();
    }
}

