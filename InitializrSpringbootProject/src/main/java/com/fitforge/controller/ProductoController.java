package com.fitforge.controller;

import com.fitforge.domain.Producto;
import com.fitforge.service.ProductoService;
import com.fitforge.service.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 *
 * @author bryam
 */
@Controller
@RequestMapping("/productos")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    private final CategoriaService categoriaService;

    @Autowired // Inyección por constructor
    public ProductoController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public String listado(Model model) {
        var lista = productoService.getProductos(true); 
        var categoria = categoriaService.getCategoria(false); 
        model.addAttribute("productos", lista);
        model.addAttribute("categoria", categoria);
        model.addAttribute("totalProductos", lista.size());
        return "/productos/productos"; 
    }

    @GetMapping("/{id}")
    public String detallesProducto(@PathVariable("id") Long id, Model model) {
        Producto producto = productoService.getProductoById(id);
        if (producto != null) {
        boolean isTallaUnica = producto.getProductoColorTallas().stream()
                .anyMatch(pct -> pct.getExistencias() > 0 && pct.getTalla() == null);
        boolean hasAvailableTallas = producto.getProductoColorTallas().stream()
                .anyMatch(pct -> pct.getTalla() != null && pct.getExistencias() > 0);
        model.addAttribute("producto", producto);
        model.addAttribute("isTallaUnica", isTallaUnica);
        model.addAttribute("hasAvailableTallas", hasAvailableTallas);
        return "productos/productos";
    } else {
        return "redirect:/tienda/listado";
    }
}
}