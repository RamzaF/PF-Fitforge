package com.fitforge.service;

import java.util.List;

import com.fitforge.domain.Categoria;
/**
 *
 * @author bryam
 */
public interface CategoriaService {

    List<Categoria> getCategoria(boolean activos); 
    
    public Categoria getCategoria(Categoria categoria);

    public void save(Categoria categoria);

    public void delete(Categoria categoria);
    
}