package com.fitforge.dao;

import com.fitforge.domain.Talla;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author bryam
 */
public interface TallaDao extends JpaRepository<Talla, Long> {
    Optional<Talla> findByNombreTallaIgnoreCase(String nombreTalla);
    List<Talla> findByActivoTrueOrderByNombreTallaAsc();
    List<Talla> findAllByOrderByIdTallaAsc();
}
