package com.fitforge.dao;

import com.fitforge.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;


public interface CategoriaDao extends JpaRepository <Categoria,Long>{
    
}
