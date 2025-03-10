package com.fitforge.controller;

import com.fitforge.domain.Producto;
import com.fitforge.domain.Categoria;
import com.fitforge.service.TiendaService;
import com.fitforge.service.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.List;

/**
 * @author bryam
 */
@Controller
@RequestMapping("/tienda")
public class TiendaController {

    @Autowired
    private TiendaService tiendaService;

    private final CategoriaService categoriaService;

    @Autowired
    public TiendaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping("/productos")
    public String listado(Model model) {
        List<Producto> lista = tiendaService.getProducto(false);
        List<Categoria> categoria = categoriaService.getCategoria(false);
        model.addAttribute("productos", lista != null ? lista : Collections.emptyList());
        model.addAttribute("categoria", categoria != null ? categoria : Collections.emptyList());
        model.addAttribute("totalProductos", lista != null ? lista.size() : 0);
        return "tienda/tienda";
    }

    @GetMapping("/tienda")
    public String mostrarTienda(Model model) {
        List<Producto> lista = tiendaService.getProducto(false);
        List<Categoria> categoria = categoriaService.getCategoria(false);
        model.addAttribute("productos", lista != null ? lista : Collections.emptyList());
        model.addAttribute("categoria", categoria != null ? categoria : Collections.emptyList());
        model.addAttribute("totalProductos", lista != null ? lista.size() : 0);
        return "tienda/tienda";
    }
}
