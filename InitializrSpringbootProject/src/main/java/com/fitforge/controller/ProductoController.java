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

    @GetMapping("/productos")
    public String listado(Model model) {
        var lista = productoService.listarProductos(); // Cambiado de getProductos(false)
        var categoria = categoriaService.getCategoria(false); // Asumiendo que este método existe
        model.addAttribute("productos", lista);
        model.addAttribute("categoria", categoria);
        model.addAttribute("totalProductos", lista.size());
        return "/productos/productos";
    }

    // Método para mostrar los detalles de un producto
    @GetMapping("/detalles/{id}")
    public String detallesProducto(@PathVariable("id") Long id, Model model) {
        // Buscar el producto por su ID
        Producto producto = productoService.obtenerProductoPorId(id); // Cambiado de getProductoById(id)

        if (producto != null) {
            // Agregar el producto al modelo para que esté disponible en la vista
            model.addAttribute("producto", producto);
            return "/productos/detalles"; // Nombre de la vista de detalles
        } else {
            // Si el producto no existe, redirigir a la lista de productos
            return "redirect:/productos/productos";
        }
    }
}