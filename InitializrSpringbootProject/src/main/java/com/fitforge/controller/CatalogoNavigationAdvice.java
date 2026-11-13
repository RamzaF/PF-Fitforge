package com.fitforge.controller;

import com.fitforge.domain.Categoria;
import com.fitforge.service.CategoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CatalogoNavigationAdvice {

    private final CategoriaService categoriaService;

    public CatalogoNavigationAdvice(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @ModelAttribute("navCategorias")
    public List<Categoria> navCategorias() {
        return categoriaService.getCategoria(true);
    }
}
