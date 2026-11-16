package com.fitforge.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitforge.dao.ColorDao;
import com.fitforge.dao.PedidoInventarioDao;
import com.fitforge.dao.ProductoColorTallaDao;
import com.fitforge.dao.TallaDao;
import com.fitforge.domain.Color;
import com.fitforge.domain.Producto;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.domain.Talla;
import com.fitforge.service.InventarioService;
import com.fitforge.service.ProductoColorTallaService;

/**
 *
 * @author ktrejos
 */
@Service
public class ProductoColorTallaServiceImpl implements ProductoColorTallaService {

    @Autowired private ProductoColorTallaDao productoColorTallaDao;
    @Autowired private PedidoInventarioDao inventario;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;
    @Autowired private ColorDao colorDao;
    @Autowired private TallaDao tallaDao;
    @Autowired private InventarioService inventarioService;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoColorTalla> listarProductoColorTalla() {
        return productoColorTallaDao.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoColorTalla> getByProducto(Producto producto) {
        if (producto == null || producto.getIdProducto() == null) {
            return List.of();
        }
        return listarPorProducto(producto.getIdProducto());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoColorTalla> listarPorProducto(Long idProducto) {
        if (idProducto == null) {
            return List.of();
        }
        return productoColorTallaDao.findByProducto_IdProductoOrderByIdProductoColorTallaAsc(idProducto);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductoColorTalla getProductoColorTalla(Long id) {
        if (id == null) {
            return null;
        }
        return productoColorTallaDao.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void guardar(ProductoColorTalla productoColorTalla) {
        var input = productoColorTalla;
        if (input == null || input.getProducto() == null || input.getProducto().getIdProducto() == null
                || input.getExistencias() == null || input.getExistencias() < 0) {
            throw new IllegalArgumentException("Inventario inválido");
        }
        Long pid = input.getProducto().getIdProducto();
        if (inventario.bloquearProducto(pid) == null) {
            throw new IllegalArgumentException("Producto no disponible");
        }
        Long colorId = input.getColor() == null ? null : input.getColor().getIdColor();
        Long tallaId = input.getTalla() == null ? null : input.getTalla().getIdTalla();
        if (input.getIdProductoColorTalla() != null) {
            var current = inventario.bloquearVariante(input.getIdProductoColorTalla());
            if (current == null || current.producto() != pid) {
                throw new IllegalArgumentException("Variante no disponible para ese producto");
            }
            em.flush();
            jdbc.update("UPDATE producto_color_talla SET id_color=?,id_talla=?,existencias=? WHERE id_producto_color_talla=?",
                    colorId, tallaId, input.getExistencias(), input.getIdProductoColorTalla());
            ProductoColorTalla managed = em.find(ProductoColorTalla.class, input.getIdProductoColorTalla());
            if (managed != null) {
                em.refresh(managed);
            }
        } else {
            if (colorId == null || tallaId == null) {
                throw new IllegalArgumentException("Debe seleccionar un color y una talla.");
            }
            guardarVariante(pid, colorId, tallaId, input.getExistencias());
        }
    }

    @Override
    @Transactional
    public ProductoColorTalla guardarVariante(Long idProducto, Long idColor, Long idTalla, int existencias) {
        if (idProducto == null || idColor == null || idTalla == null) {
            throw new IllegalArgumentException("Producto, color y talla son obligatorios.");
        }
        if (existencias < 0) {
            throw new IllegalArgumentException("Las existencias no pueden ser negativas.");
        }
        if (inventario.bloquearProducto(idProducto) == null) {
            throw new IllegalArgumentException("El producto no existe.");
        }
        Color color = colorDao.findById(idColor)
                .orElseThrow(() -> new IllegalArgumentException("El color seleccionado no existe."));
        if (!color.isActivo()) {
            throw new IllegalArgumentException("El color seleccionado está inactivo.");
        }
        Talla talla = tallaDao.findById(idTalla)
                .orElseThrow(() -> new IllegalArgumentException("La talla seleccionada no existe."));
        if (!talla.isActivo()) {
            throw new IllegalArgumentException("La talla seleccionada está inactiva.");
        }

        if (productoColorTallaDao
                .findByProducto_IdProductoAndColor_IdColorAndTalla_IdTalla(idProducto, idColor, idTalla)
                .isPresent()) {
            throw new IllegalArgumentException("Ya existe una variante con esa combinación de color y talla.");
        }

        var fresh = new ProductoColorTalla();
        fresh.setProducto(em.getReference(Producto.class, idProducto));
        fresh.setColor(color);
        fresh.setTalla(talla);
        fresh.setExistencias(existencias);
        fresh.setActivo(true);
        try {
            em.persist(fresh);
            em.flush();
            inventarioService.registrarAltaInicialSiAplica(fresh.getIdProductoColorTalla(), existencias);
            Producto parent = em.find(Producto.class, idProducto);
            if (parent != null) {
                em.refresh(parent);
            }
            return fresh;
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Ya existe una variante con esa combinación de color y talla.");
        }
    }

    @Override
    @Transactional
    public void cambiarEstado(Long idVariante, boolean activo) {
        if (idVariante == null) {
            throw new IllegalArgumentException("Variante no encontrada.");
        }
        Long pid = inventario.productoDe(idVariante);
        if (pid == null) {
            throw new IllegalArgumentException("Variante no encontrada.");
        }
        inventario.bloquearProducto(pid);
        var current = inventario.bloquearVariante(idVariante);
        if (current == null || current.producto() != pid) {
            throw new IllegalArgumentException("Variante no encontrada.");
        }
        em.flush();
        jdbc.update("UPDATE producto_color_talla SET activo=? WHERE id_producto_color_talla=?", activo, idVariante);
        ProductoColorTalla managed = em.find(ProductoColorTalla.class, idVariante);
        if (managed != null) {
            em.refresh(managed);
        }
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        Long pid = inventario.productoDe(id);
        if (pid == null) {
            return;
        }
        inventario.bloquearProducto(pid);
        var current = inventario.bloquearVariante(id);
        if (current == null) {
            return;
        }
        if (current.producto() != pid) {
            throw new IllegalArgumentException("La variante cambió; reintentá.");
        }
        Integer salesCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM venta WHERE id_producto_color_talla=?", Integer.class, id);
        if (salesCount != null && salesCount > 0) {
            throw new IllegalArgumentException("La variante tiene pedidos registrados y no se puede eliminar.");
        }
        Integer movCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM movimiento_inventario WHERE id_producto_color_talla=?", Integer.class, id);
        if (movCount != null && movCount > 0) {
            throw new IllegalArgumentException(
                    "La variante tiene historial de inventario registrado y no se puede eliminar; usá Desactivar.");
        }
        em.flush();
        em.clear();
        jdbc.update("DELETE FROM producto_color_talla WHERE id_producto_color_talla=?", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> variantesVendidasDeProducto(Long idProducto) {
        if (idProducto == null) {
            return Set.of();
        }
        return new HashSet<>(jdbc.query(
                "SELECT DISTINCT v.id_producto_color_talla FROM venta v " +
                "JOIN producto_color_talla pct ON pct.id_producto_color_talla = v.id_producto_color_talla " +
                "WHERE pct.id_producto = ?",
                (rs, rowNum) -> rs.getLong(1), idProducto));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> variantesConHistorialDeProducto(Long idProducto) {
        if (idProducto == null) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>(variantesVendidasDeProducto(idProducto));
        ids.addAll(jdbc.query(
                "SELECT DISTINCT m.id_producto_color_talla FROM movimiento_inventario m " +
                "JOIN producto_color_talla pct ON pct.id_producto_color_talla = m.id_producto_color_talla " +
                "WHERE pct.id_producto = ?",
                (rs, rowNum) -> rs.getLong(1), idProducto));
        return ids;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Color> listarColores() {
        return colorDao.findAllByOrderByIdColorAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Color> listarColoresActivos() {
        return colorDao.findByActivoTrueOrderByNombreColorAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Talla> listarTallas() {
        return tallaDao.findAllByOrderByIdTallaAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Talla> listarTallasActivas() {
        return tallaDao.findByActivoTrueOrderByNombreTallaAsc();
    }
}
