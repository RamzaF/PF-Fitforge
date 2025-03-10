package com.fitforge.dao;

import com.fitforge.domain.Color;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author bryam
 */

public interface ColorDao extends JpaRepository<Color, Long> {
}
