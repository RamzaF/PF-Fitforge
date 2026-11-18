package com.fitforge.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fitforge.domain.Categoria;
import com.fitforge.domain.Producto;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.TiendaService;
/**
  *
  * @author bryam
  */
@Controller
@RequestMapping("/tienda")
public class TiendaController {

    @Autowired
    private TiendaService tiendaService;

    @Autowired
    private CategoriaService categoriaService;

    private void cargarFiltros(Model model, List<Producto> lista) {
        List<Categoria> categorias = categoriaService.getCategoria(true);
        model.addAttribute("productos", lista != null ? lista : Collections.emptyList());
        model.addAttribute("categorias", categorias != null ? categorias : Collections.emptyList());
        model.addAttribute("colores", tiendaService.getColores());
        model.addAttribute("tallas", tiendaService.getTallas());
        model.addAttribute("precioMin", tiendaService.getPrecioMinimo());
        model.addAttribute("precioMax", tiendaService.getPrecioMaximo());
        model.addAttribute("totalProductos", lista != null ? lista.size() : 0);
    }

    @GetMapping({"/", "/listado", "/productos"})
    public String mostrarListado(Model model) {
        List<Producto> lista = tiendaService.getProducto(true);
        cargarFiltros(model, lista);
        return "tienda/listado";
    }

    @GetMapping("/filtrar")
    public String filtrarProductos(
            @RequestParam(value = "categorias", required = false) List<Long> categorias,
            @RequestParam(value = "colores", required = false) List<Long> colores,
            @RequestParam(value = "tallas", required = false) List<Long> tallas,
            @RequestParam(value = "precioMin", required = false) Double precioMin,
            @RequestParam(value = "precioMax", required = false) Double precioMax,
            Model model) {
        List<Producto> lista = tiendaService.getProductosFiltrados(categorias, colores, tallas, precioMin, precioMax);
        cargarFiltros(model, lista);
        return "tienda/listado";
    }
    
    @GetMapping("/buscar")
    public String buscarProductos(
        @RequestParam(value = "termino", required = false) String termino,
        Model model) {
        List<Producto> lista = tiendaService.buscarProductos(termino);
        cargarFiltros(model, lista);
        model.addAttribute("terminoBusqueda", termino);
        return "tienda/listado";
    }

}