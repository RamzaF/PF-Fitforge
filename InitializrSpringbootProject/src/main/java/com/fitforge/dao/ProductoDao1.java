package com.fitforge.dao;

import com.fitforge.domain.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author bryam
 */

public interface ProductoDao extends JpaRepository <Producto,Long>{
    
}
