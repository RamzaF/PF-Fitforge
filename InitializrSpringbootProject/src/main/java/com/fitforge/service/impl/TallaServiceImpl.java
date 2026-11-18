package com.fitforge.service.impl;

import com.fitforge.dao.TallaDao;
import com.fitforge.domain.Talla;
import com.fitforge.service.CatalogoValidacion;
import com.fitforge.service.TallaService;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TallaServiceImpl implements TallaService {

    private final TallaDao tallaDao;

    public TallaServiceImpl(TallaDao tallaDao) {
        this.tallaDao = tallaDao;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Talla> listarTodos() {
        return tallaDao.findAllByOrderByIdTallaAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Talla> listarActivos() {
        return tallaDao.findByActivoTrueOrderByNombreTallaAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Talla obtenerPorId(Long id) {
        if (id == null) {
            return null;
        }
        return tallaDao.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public Talla guardar(Long id, String nombreTalla, boolean activo) {
        String normalized = CatalogoValidacion.normalizarNombre(nombreTalla, "nombre de la talla", 1, 50);
        String key = CatalogoValidacion.claveComparacion(normalized);

        boolean duplicate = tallaDao.findAllByOrderByIdTallaAsc().stream()
                .anyMatch(t -> (id == null || !t.getIdTalla().equals(id))
                        && CatalogoValidacion.claveComparacion(t.getNombreTalla().trim().replaceAll("\\s+", " "))
                                .equals(key));
        if (duplicate) {
            throw new IllegalArgumentException("Ya existe una talla con el nombre '" + normalized + "'.");
        }

        Talla talla;
        if (id != null && id > 0) {
            talla = tallaDao.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("La talla solicitada no existe."));
        } else {
            talla = new Talla();
        }
        talla.setNombreTalla(normalized);
        talla.setActivo(activo);
        try {
            return tallaDao.saveAndFlush(talla);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Ya existe una talla con ese nombre.");
        }
    }

    @Override
    @Transactional
    public void cambiarEstado(Long id, boolean activo) {
        if (id == null) {
            throw new IllegalArgumentException("La talla solicitada no existe.");
        }
        Talla talla = tallaDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La talla solicitada no existe."));
        talla.setActivo(activo);
        tallaDao.saveAndFlush(talla);
    }
}
