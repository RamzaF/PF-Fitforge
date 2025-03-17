/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author ktrejos
 */

import com.fitforge.domain.Producto;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.LocalStorageService;
import com.fitforge.service.ProductoService;

@Controller
@RequestMapping("/admin/productos")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private LocalStorageService localStorageService;

    @Autowired
    private CategoriaService categoriaService;

    @GetMapping
    public String listarProductos(Model model) {
        var productos = productoService.listarProductos();
        model.addAttribute("productos", productos);
        model.addAttribute("totalProductos", productos.size());
        return "admin/productos/listado";
    }

    @GetMapping("/nuevo")
    public String nuevoProducto(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("categorias", categoriaService.getCategoria(true)); // Agregar lista de categorías
        return "admin/productos/formulario";
    }

    @PostMapping("/guardar")
    public String guardarProducto(Producto producto, @RequestParam("archivo") MultipartFile archivo) {
        if (!archivo.isEmpty()) {
            try {
                String rutaImagen = localStorageService.guardarImagen(archivo, producto.getIdProducto() != null ? producto.getIdProducto() : System.currentTimeMillis());
                producto.setRutaImagen(rutaImagen);
            } catch (Exception e) {
                // aqui para manejar el error 
            }
        }
        productoService.guardarProducto(producto);
        return "redirect:/admin/productos";
    }

    @GetMapping("/editar/{idProducto}")
    public String editarProducto(@PathVariable("idProducto") Long id, Model model) {
        Producto producto = productoService.obtenerProductoPorId(id);
        if (producto == null) {
            return "redirect:/admin/productos";
        }
        model.addAttribute("producto", producto);
        model.addAttribute("categorias", categoriaService.getCategoria(true));
        return "admin/productos/formulario";
    }

    @GetMapping("/eliminar/{idProducto}")
    public String eliminarProducto(@PathVariable("idProducto") Long id) {
        productoService.eliminarProducto(id);
        return "redirect:/admin/productos";
    }
}