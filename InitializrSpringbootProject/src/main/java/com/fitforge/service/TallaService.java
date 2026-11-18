package com.fitforge.service;

import com.fitforge.domain.Talla;
import java.util.List;

public interface TallaService {
    List<Talla> listarTodos();
    List<Talla> listarActivos();
    Talla obtenerPorId(Long id);
    Talla guardar(Long id, String nombreTalla, boolean activo);
    void cambiarEstado(Long id, boolean activo);
}
