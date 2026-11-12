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

    @Autowired private ProductoColorTallaDao productoColorTallaDao;
    @Autowired private com.fitforge.dao.PedidoInventarioDao inventario;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;
    
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
        var input=productoColorTalla;
        if(input.getProducto()==null || input.getProducto().getIdProducto()==null || input.getExistencias()==null || input.getExistencias()<0)
            throw new IllegalArgumentException("Inventario inválido");
        Long pid=input.getProducto().getIdProducto();
        if(inventario.bloquearProducto(pid)==null) throw new IllegalArgumentException("Producto no disponible");
        Long color=input.getColor()==null?null:input.getColor().getIdColor();
        Long talla=input.getTalla()==null?null:input.getTalla().getIdTalla();
        if(input.getIdProductoColorTalla()!=null) {
            var current=inventario.bloquearVariante(input.getIdProductoColorTalla());
            if(current==null || current.producto()!=pid) throw new IllegalArgumentException("Variante no disponible para ese producto");
            jdbc.update("UPDATE producto_color_talla SET id_color=?,id_talla=?,existencias=? WHERE id_producto_color_talla=?",color,talla,input.getExistencias(),input.getIdProductoColorTalla());
        } else {
            var fresh=new ProductoColorTalla(); fresh.setProducto(em.getReference(Producto.class,pid));
            fresh.setColor(color==null?null:em.getReference(Color.class,color));fresh.setTalla(talla==null?null:em.getReference(Talla.class,talla));
            fresh.setExistencias(input.getExistencias());em.persist(fresh);em.flush();input.setIdProductoColorTalla(fresh.getIdProductoColorTalla());
        }
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Long pid=inventario.productoDe(id);
        if(pid==null) return;
        inventario.bloquearProducto(pid); var current=inventario.bloquearVariante(id);
        if(current==null) return;
        if(current.producto()!=pid) throw new IllegalArgumentException("La variante cambió; reintentá.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM venta WHERE id_producto_color_talla=?",Integer.class,id)>0)
            throw new IllegalArgumentException("La variante tiene pedidos registrados y no se puede eliminar.");
        jdbc.update("DELETE FROM producto_color_talla WHERE id_producto_color_talla=?",id);
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

