package com.fitforge.dao;

import com.fitforge.domain.Color;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author bryam
 */
public interface ColorDao extends JpaRepository<Color, Long> {
    Optional<Color> findByNombreColorIgnoreCase(String nombreColor);
    List<Color> findByActivoTrueOrderByNombreColorAsc();
    List<Color> findAllByOrderByIdColorAsc();
}
