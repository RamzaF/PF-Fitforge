package com.fitforge.controller;

/**
  *
  * @author bryam
  */
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fitforge.domain.Categoria;
import com.fitforge.domain.Producto;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.ProductoService;

@Controller
@RequestMapping("/categoria")
public class CategoriaController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private CategoriaService categoriaService;

    @GetMapping("/{nombre}")
    public String verCategoria(@PathVariable String nombre, Model model) {
        Categoria activa = categoriaService.buscarActivaPorNombre(nombre).orElse(null);
        if (activa == null) {
            model.addAttribute("productos", List.of());
            model.addAttribute("categoria", nombre);
            return "categoria/listado";
        }
        List<Producto> productos = productoService.obtenerPorCategoria(activa.getDescripcion()).stream()
                .filter(p -> p.isActivo() && p.getCategoria() != null && p.getCategoria().isActivo())
                .collect(Collectors.toList());

        model.addAttribute("productos", productos);
        model.addAttribute("categoria", activa.getDescripcion());
        return "categoria/listado";
    }
}