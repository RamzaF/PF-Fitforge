package com.fitforge.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitforge.dao.CategoriaDao;
import com.fitforge.domain.Categoria;
import com.fitforge.service.CatalogoValidacion;
import com.fitforge.service.CategoriaService;
/**
  *
  * @author bryam
  */
@Service
public class CategoriaServiceImpl implements CategoriaService {

    @Autowired
    private CategoriaDao categoriaDao;

    @Override
    @Transactional(readOnly = true)
    public List<Categoria> getCategoria(boolean activos) {
        if (activos) {
            return categoriaDao.findByActivoTrueOrderByDescripcionAsc();
        }
        return categoriaDao.findAllByOrderByIdCategoriaAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Categoria getCategoria(Categoria categoria) {
        if (categoria == null || categoria.getIdCategoria() == null) {
            return null;
        }
        return categoriaDao.findById(categoria.getIdCategoria()).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Categoria obtenerPorId(Long id) {
        if (id == null) {
            return null;
        }
        return categoriaDao.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Categoria> buscarActivaPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        String normalized = nombre.trim().replaceAll("\\s+", " ");
        return categoriaDao.findByDescripcionIgnoreCase(normalized)
                .filter(Categoria::isActivo);
    }

    @Override
    @Transactional
    public Categoria guardar(Long id, String descripcion, boolean activo) {
        String normalized = CatalogoValidacion.normalizarNombre(descripcion, "nombre de la categoría", 2, 50);
        String key = CatalogoValidacion.claveComparacion(normalized);

        boolean duplicate = categoriaDao.findAllByOrderByIdCategoriaAsc().stream()
                .anyMatch(c -> (id == null || !c.getIdCategoria().equals(id))
                        && CatalogoValidacion.claveComparacion(c.getDescripcion().trim().replaceAll("\\s+", " "))
                                .equals(key));
        if (duplicate) {
            throw new IllegalArgumentException("Ya existe una categoría con el nombre '" + normalized + "'.");
        }

        Categoria categoria;
        if (id != null && id > 0) {
            categoria = categoriaDao.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría solicitada no existe."));
        } else {
            categoria = new Categoria();
        }
        categoria.setDescripcion(normalized);
        categoria.setActivo(activo);
        try {
            return categoriaDao.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre.");
        }
    }

    @Override
    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        if (id == null) {
            throw new IllegalArgumentException("La categoría solicitada no existe.");
        }
        Categoria categoria = categoriaDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La categoría solicitada no existe."));
        categoria.setActivo(activo);
        categoriaDao.saveAndFlush(categoria);
    }

    @Override
    @Transactional
    public void save(Categoria categoria) {
        if (categoria == null) {
            throw new IllegalArgumentException("Categoría inválida.");
        }
        Categoria saved = guardar(categoria.getIdCategoria(), categoria.getDescripcion(), categoria.isActivo());
        categoria.setIdCategoria(saved.getIdCategoria());
        categoria.setDescripcion(saved.getDescripcion());
    }

    @Override
    @Transactional
    public void delete(Categoria categoria) {
        if (categoria != null && categoria.getIdCategoria() != null) {
            cambiarEstado(categoria.getIdCategoria(), false);
        }
    }
}
