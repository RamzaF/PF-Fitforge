package com.fitforge.service;

import com.fitforge.domain.Categoria;
import java.util.List;
/**
 *
 * @author bryam
 */
public interface CategoriaService {

    public List<Categoria> getCategoria(boolean activos);
    
    public Categoria getCategoria(Categoria categoria);

    public void save(Categoria categoria);

    public void delete(Categoria categoria);
    
}