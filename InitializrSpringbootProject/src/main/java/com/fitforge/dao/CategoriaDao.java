package com.fitforge.dao;

import com.fitforge.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

/**
  *
  * @author bryam
  */
public interface CategoriaDao extends JpaRepository <Categoria,Long>{
    
}
