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

import com.fitforge.domain.Producto;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.LocalStorageService;
import com.fitforge.service.ProductoService;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private LocalStorageService localStorageService;

    @Autowired
    private CategoriaService categoriaService;

    @GetMapping("/productos")
    public String listarProductos(Model model) {
        var productos = productoService.listarProductos();
        model.addAttribute("productos", productos);
        model.addAttribute("totalProductos", productos.size());
        return "admin/productos/listado";
    }

    @GetMapping("/productos/nuevo")
    public String nuevoProducto(Model model) {
        model.addAttribute("producto", new Producto());
        model.addAttribute("categorias", categoriaService.getCategoria(true));
        return "admin/productos/formulario";
    }

    @PostMapping("/productos/guardar")
    public String guardarProducto(Producto producto, @RequestParam("archivo") MultipartFile archivo) {
        if (!archivo.isEmpty()) {
            try {
                String rutaImagen = localStorageService.guardarImagen(archivo, producto.getIdProducto() != null ? producto.getIdProducto() : System.currentTimeMillis());
                producto.setRutaImagen(rutaImagen);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        productoService.guardarProducto(producto);
        return "redirect:/admin/productos";
    }

    @GetMapping("/productos/editar/{idProducto}")
    public String editarProducto(@PathVariable("idProducto") Long id, Model model) {
        Producto producto = productoService.obtenerProductoPorId(id);
        if (producto == null) {
            return "redirect:/admin/productos";
        }
        model.addAttribute("producto", producto);
        model.addAttribute("categorias", categoriaService.getCategoria(true));
        return "admin/productos/formulario";
    }

    @GetMapping("/productos/eliminar/{idProducto}")
    public String eliminarProducto(@PathVariable("idProducto") Long id) {
        productoService.eliminarProducto(id);
        return "redirect:/admin/productos";
    }

    @GetMapping("/ropa-deportiva")
    public String listarRopaDeportiva(Model model) {
        var productos = productoService.obtenerPorCategoria("Ropa Deportiva");
        model.addAttribute("productos", productos);
        model.addAttribute("categoria", "Ropa Deportiva");
        return "categoria-listado"; // Cambio aquí
    }

    @GetMapping("/suplementos")
    public String listarSuplementos(Model model) {
        var productos = productoService.obtenerPorCategoria("Suplementos");
        model.addAttribute("productos", productos);
        model.addAttribute("categoria", "Suplementos");
        return "categoria-listado"; // Cambio aquí
    }

    @GetMapping("/accesorios")
    public String listarAccesorios(Model model) {
        var productos = productoService.obtenerPorCategoria("Accesorios");
        model.addAttribute("productos", productos);
        model.addAttribute("categoria", "Accesorios");
        return "categoria-listado"; // Cambio aquí
    }
}