package com.fitforge.service;

import com.fitforge.domain.Color;
import java.util.List;

public interface ColorService {
    List<Color> listarTodos();
    List<Color> listarActivos();
    Color obtenerPorId(Long id);
    Color guardar(Long id, String nombreColor, boolean activo);
    void cambiarEstado(Long id, boolean activo);
}
