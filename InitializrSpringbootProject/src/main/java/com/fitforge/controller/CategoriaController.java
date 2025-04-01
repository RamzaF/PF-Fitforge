package com.fitforge.controller;

/**
  *
  * @author bryam
  */
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fitforge.domain.Producto;
import com.fitforge.service.ProductoService;

@Controller
@RequestMapping("/categoria")
public class CategoriaController {

    @Autowired
    private ProductoService productoService;

    @GetMapping("/{nombre}")
    public String verCategoria(@PathVariable String nombre, Model model) {
        // Obtener productos de la categoría
        List<Producto> productos = productoService.obtenerPorCategoria(nombre);
        
        // Agregar datos a la vista
        model.addAttribute("productos", productos);
        model.addAttribute("categoria", nombre);
        
        return "categoria/listado"; // Cargar la vista categoria/listado.html
    }
}