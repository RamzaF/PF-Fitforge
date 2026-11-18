package com.fitforge.service;

import java.util.List;
import java.util.Optional;

import com.fitforge.domain.Categoria;
/**
 *
 * @author bryam
 */
public interface CategoriaService {

    List<Categoria> getCategoria(boolean activos);

    Categoria getCategoria(Categoria categoria);

    Categoria obtenerPorId(Long id);

    Optional<Categoria> buscarActivaPorNombre(String nombre);

    Categoria guardar(Long id, String descripcion, boolean activo);

    void cambiarEstado(Long id, boolean activo);

    void save(Categoria categoria);

    void delete(Categoria categoria);
}
