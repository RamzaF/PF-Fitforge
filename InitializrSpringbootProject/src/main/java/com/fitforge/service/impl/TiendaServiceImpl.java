package com.fitforge.service.impl;

import com.fitforge.dao.ProductoDao;
import com.fitforge.dao.ColorDao;
import com.fitforge.dao.TallaDao;
import com.fitforge.domain.Producto;
import com.fitforge.domain.Color;
import com.fitforge.domain.Talla;
import com.fitforge.service.TiendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
/**
  *
  * @author bryam
  */
@Service
public class TiendaServiceImpl implements TiendaService {

    @Autowired
    private ProductoDao productoDao;

    @Autowired
    private ColorDao colorDao;

    @Autowired
    private TallaDao tallaDao;

    @Override
    @Transactional(readOnly = true)
    public List<Producto> getProducto(boolean activos) {
        var lista = productoDao.findAll();
        if (activos) {
            lista = lista.stream().filter(Producto::isActivo).collect(Collectors.toList());
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

    /*@Override
    @Transactional
    public void delete(Producto producto) {
    productoDao.deleteById(producto.getIdProducto());
}*/

    @Override
    @Transactional(readOnly = true)
    public List<Color> getColores() {
        return colorDao.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Talla> getTallas() {
        return tallaDao.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Double getPrecioMinimo() {
        Double min = productoDao.findMinPrecio();
        return min != null ? min : 0.0;
    }

    @Override
    @Transactional(readOnly = true)
    public Double getPrecioMaximo() {
        Double max = productoDao.findMaxPrecio();
        return max != null ? max : 0.0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> getProductosFiltrados(List<Long> categorias, List<Long> colores, List<Long> tallas, Double precioMin, Double precioMax) {
        List<Producto> lista = productoDao.findAll();
        if (categorias != null && !categorias.isEmpty()) {
            lista = lista.stream()
                    .filter(p -> categorias.contains(p.getCategoria().getIdCategoria()))
                    .collect(Collectors.toList());
        }
        if (colores != null && !colores.isEmpty()) {
            lista = lista.stream()
                    .filter(p -> p.getProductoColorTallas().stream()
                            .anyMatch(pct -> pct.getColor() != null && colores.contains(pct.getColor().getIdColor())))
                    .collect(Collectors.toList());
        }
        if (tallas != null && !tallas.isEmpty()) {
            lista = lista.stream()
                    .filter(p -> p.getProductoColorTallas().stream()
                            .anyMatch(pct -> pct.getTalla() != null && tallas.contains(pct.getTalla().getIdTalla())))
                    .collect(Collectors.toList());
        }
        if (precioMin != null) {
            lista = lista.stream().filter(p -> p.getPrecio() >= precioMin).collect(Collectors.toList());
        }
        if (precioMax != null) {
            lista = lista.stream().filter(p -> p.getPrecio() <= precioMax).collect(Collectors.toList());
        }
        return lista;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> buscarProductos(String termino) {
        if (termino == null || termino.trim().isEmpty()) {
        return productoDao.findAll();
    }
        return productoDao.buscarPorTermino(termino.toLowerCase());
    }

}