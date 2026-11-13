package com.fitforge.dao;

import com.fitforge.domain.Categoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
  *
  * @author bryam
  */
public interface CategoriaDao extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByDescripcionIgnoreCase(String descripcion);
    List<Categoria> findByActivoTrueOrderByDescripcionAsc();
    List<Categoria> findAllByOrderByIdCategoriaAsc();
}
