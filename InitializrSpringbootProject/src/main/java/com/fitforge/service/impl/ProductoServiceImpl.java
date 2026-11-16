package com.fitforge.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitforge.dao.CategoriaDao;
import com.fitforge.dao.PedidoInventarioDao;
import com.fitforge.dao.ProductoDao;
import com.fitforge.domain.Categoria;
import com.fitforge.domain.Producto;
import com.fitforge.service.CatalogoValidacion;
import com.fitforge.service.Dinero;
import com.fitforge.service.ProductoColorTallaService;
import com.fitforge.service.ProductoService;

/**
  *
  * @author bryam
  */
@Service
public class ProductoServiceImpl implements ProductoService {

    @Autowired private ProductoDao productoDao;
    @Autowired private CategoriaDao categoriaDao;
    @Autowired private PedidoInventarioDao inventario;
    @Autowired private ProductoColorTallaService productoColorTallaService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;

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
                    .filter(p -> p.isActivo() && p.getCategoria() != null && p.getCategoria().isActivo())
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
        if (producto == null) {
            throw new IllegalArgumentException("Datos de producto inválidos.");
        }
        String descripcion = CatalogoValidacion.normalizarNombre(
                producto.getDescripcion(), "nombre del producto", 3, 100);
        String detalle = producto.getDetalle() == null ? "" : producto.getDetalle().trim();
        if (detalle.isEmpty() || detalle.length() > 1000) {
            throw new IllegalArgumentException("El detalle del producto debe tener entre 1 y 1000 caracteres.");
        }
        Dinero.desdeDouble(producto.getPrecio());

        if (producto.getCategoria() == null || producto.getCategoria().getIdCategoria() == null) {
            throw new IllegalArgumentException("Debe seleccionar una categoría válida.");
        }
        Categoria categoria = categoriaDao.findById(producto.getCategoria().getIdCategoria())
                .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no existe."));

        boolean nuevo = producto.getIdProducto() == null || producto.getIdProducto() == 0;
        if (nuevo) {
            if (!categoria.isActivo()) {
                throw new IllegalArgumentException("La categoría seleccionada está inactiva.");
            }
            var fresh = new Producto(descripcion, detalle, producto.getRutaImagen(), producto.getPrecio(), producto.isActivo());
            fresh.setCategoria(categoria);
            productoDao.saveAndFlush(fresh);
            producto.setIdProducto(fresh.getIdProducto());
            producto.setDescripcion(descripcion);
            producto.setDetalle(detalle);
        } else {
            if (inventario.bloquearProducto(producto.getIdProducto()) == null) {
                throw new IllegalArgumentException("Producto no disponible");
            }
            Long currentCategoryId = jdbc.queryForObject(
                    "SELECT id_categoria FROM producto WHERE id_producto=?", Long.class, producto.getIdProducto());
            if (!categoria.isActivo() && (currentCategoryId == null || !currentCategoryId.equals(categoria.getIdCategoria()))) {
                throw new IllegalArgumentException("No podés mover un producto a una categoría inactiva.");
            }
            // Scalar SQL deliberately avoids cascade merge/dirty checking of stale EAGER stock.
            em.flush();
            jdbc.update(
                    "UPDATE producto SET descripcion=?, detalle=?, imagen=?, precio=?, activo=?, id_categoria=? WHERE id_producto=?",
                    descripcion, detalle, producto.getRutaImagen(), producto.getPrecio(), producto.isActivo(),
                    categoria.getIdCategoria(), producto.getIdProducto());
            Producto managed = em.find(Producto.class, producto.getIdProducto());
            if (managed != null) {
                em.refresh(managed);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Producto guardarProductoConVarianteInicial(Producto producto, Long idColor, Long idTalla, Integer existencias) {
        if (existencias != null && existencias < 0) {
            throw new IllegalArgumentException("Las existencias iniciales no pueden ser negativas.");
        }
        boolean hasColor = idColor != null && idColor > 0;
        boolean hasTalla = idTalla != null && idTalla > 0;
        if (hasColor != hasTalla) {
            throw new IllegalArgumentException("Para crear la variante inicial debés seleccionar tanto color como talla.");
        }
        guardarProducto(producto);
        if (hasColor) {
            int stock = existencias == null ? 0 : existencias;
            productoColorTallaService.guardarVariante(producto.getIdProducto(), idColor, idTalla, stock);
        }
        return producto;
    }

    @Override
    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        if (id == null || inventario.bloquearProducto(id) == null) {
            throw new IllegalArgumentException("Producto no encontrado.");
        }
        em.flush();
        jdbc.update("UPDATE producto SET activo=? WHERE id_producto=?", activo, id);
        Producto managed = em.find(Producto.class, id);
        if (managed != null) {
            em.refresh(managed);
        }
    }

    @Override
    @Transactional
    public void eliminarProducto(Long id) {
        if (inventario.bloquearProducto(id) != null) {
            em.flush();
            jdbc.update("UPDATE producto SET activo=false WHERE id_producto=?", id);
            Producto managed = em.find(Producto.class, id);
            if (managed != null) {
                em.refresh(managed);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Producto getProductoById(Long id) {
        return productoDao.findById(id)
                .map(producto -> {
                    Hibernate.initialize(producto.getCategoria());
                    return producto;
                })
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> obtenerPorCategoria(String nombreCategoria) {
        if (nombreCategoria == null || nombreCategoria.isBlank()) {
            return List.of();
        }
        return productoDao.findByCategoria_DescripcionIgnoreCase(nombreCategoria.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Producto> buscarProductos(String termino) {
        if (termino == null || termino.trim().isEmpty()) {
            return productoDao.findAll();
        }
        return productoDao.buscarPorTermino(termino.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public int contarProductosPorImagen(String rutaImagen) {
        if (rutaImagen == null || rutaImagen.isBlank()) {
            return 0;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto WHERE imagen = ?", Integer.class, rutaImagen);
        return count == null ? 0 : count;
    }
}
