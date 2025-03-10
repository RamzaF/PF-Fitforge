package com.fitforge.dao;

import com.fitforge.domain.Contacto;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author bryam
 */

public interface ContactoDao extends JpaRepository <Contacto,Long>{
    
}
