package com.fitforge.service.impl;

import com.fitforge.dao.ColorDao;
import com.fitforge.domain.Color;
import com.fitforge.service.CatalogoValidacion;
import com.fitforge.service.ColorService;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ColorServiceImpl implements ColorService {

    private final ColorDao colorDao;

    public ColorServiceImpl(ColorDao colorDao) {
        this.colorDao = colorDao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Color> listarTodos() {
        return colorDao.findAllByOrderByIdColorAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Color> listarActivos() {
        return colorDao.findByActivoTrueOrderByNombreColorAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Color obtenerPorId(Long id) {
        if (id == null) {
            return null;
        }
        return colorDao.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public Color guardar(Long id, String nombreColor, boolean activo) {
        String normalized = CatalogoValidacion.normalizarNombre(nombreColor, "nombre del color", 1, 50);
        String key = CatalogoValidacion.claveComparacion(normalized);

        boolean duplicate = colorDao.findAllByOrderByIdColorAsc().stream()
                .anyMatch(c -> (id == null || !c.getIdColor().equals(id))
                        && CatalogoValidacion.claveComparacion(c.getNombreColor().trim().replaceAll("\\s+", " "))
                                .equals(key));
        if (duplicate) {
            throw new IllegalArgumentException("Ya existe un color con el nombre '" + normalized + "'.");
        }

        Color color;
        if (id != null && id > 0) {
            color = colorDao.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("El color solicitado no existe."));
        } else {
            color = new Color();
        }
        color.setNombreColor(normalized);
        color.setActivo(activo);
        try {
            return colorDao.saveAndFlush(color);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Ya existe un color con ese nombre.");
        }
    }

    @Override
    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        if (id == null) {
            throw new IllegalArgumentException("El color solicitado no existe.");
        }
        Color color = colorDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El color solicitado no existe."));
        color.setActivo(activo);
        colorDao.saveAndFlush(color);
    }
}
