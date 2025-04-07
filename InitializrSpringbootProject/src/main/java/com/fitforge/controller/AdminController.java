package com.fitforge.controller;

import java.util.List;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fitforge.domain.Producto;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.domain.Color;
import com.fitforge.domain.Talla;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.LocalStorageService;
import com.fitforge.service.ProductoService;
import com.fitforge.service.ProductoColorTallaService;

/**
 * Controlador para la administración de productos, inventario y categorías.
 * Este controlador maneja las operaciones CRUD de productos, la gestión de inventario
 * y la visualización de productos por categoría.
 * 
 * @author ktrejos
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {

    @Autowired
    private CategoriaService categoriaService;
    
    @Autowired
    private ProductoColorTallaService productoColorTallaService;

    @Autowired
    private LocalStorageService localStorageService;
    
    @Autowired
    private ProductoService productoService;

    /**
     * Método para redirigir al listado de productos.
     * 
     * @return Redirección al listado de productos en AdminProductoController
     */
    @GetMapping("/productos")
    public String redirigirProductos() {
        return "redirect:/admin/productos/listado";
    }
    
    /**
     * Método para mostrar la página de gestión de inventario de un producto.
     * 
     * @param idProducto ID del producto
     * @param model Modelo para la vista
     * @param redirectAttributes Atributos para mensajes de retroalimentación
     * @return Vista de inventario o redirección si no existe el producto
     */
    @GetMapping("/productos/inventario/{idProducto}")
    public String mostrarInventario(@PathVariable("idProducto") Long idProducto, 
                                   RedirectAttributes redirectAttributes) {
        // Redirigimos a la ruta gestionada por AdminProductoController
        return "redirect:/admin/productos/inventario/" + idProducto;
    }
    
    /**
     * Método para guardar o actualizar una entrada de inventario.
     * 
     * @param productoColorTalla Objeto ProductoColorTalla a guardar
     * @param redirectAttributes Atributos para mensajes de retroalimentación
     * @return Redirección a la página de inventario del producto
     */
    @PostMapping("/productos/inventario/guardar")
    public String guardarInventario(ProductoColorTalla productoColorTalla, 
                                  RedirectAttributes redirectAttributes) {
        try {
            // Validaciones
            if (productoColorTalla.getExistencias() < 0) {
                redirectAttributes.addFlashAttribute("error", "Las existencias no pueden ser negativas");
                return "redirect:/admin/productos/inventario/" + productoColorTalla.getProducto().getIdProducto();
            }
            
            // Verificar que el producto exista
            Producto producto = productoService.obtenerProductoPorId(productoColorTalla.getProducto().getIdProducto());
            if (producto == null) {
                redirectAttributes.addFlashAttribute("error", "El producto no existe");
                return "redirect:/admin/productos";
            }
            
            // Verificar que la combinación de color y talla sea válida
            if (productoColorTalla.getColor() == null || productoColorTalla.getTalla() == null) {
                redirectAttributes.addFlashAttribute("error", "Debe seleccionar un color y una talla");
                return "redirect:/admin/productos/inventario/" + productoColorTalla.getProducto().getIdProducto();
            }
            
            productoColorTallaService.guardar(productoColorTalla);
            redirectAttributes.addFlashAttribute("mensaje", "Inventario actualizado correctamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al guardar inventario: " + e.getMessage());
            e.printStackTrace();
        }
        
        return "redirect:/admin/productos/inventario/" + productoColorTalla.getProducto().getIdProducto();
    }
    
    /**
     * Método para eliminar una entrada de inventario.
     * 
     * @param id ID de la entrada ProductoColorTalla a eliminar
     * @param redirectAttributes Atributos para mensajes de retroalimentación
     * @return Redirección a la página de inventario del producto o a la lista de productos
     */
    @GetMapping("/productos/inventario/eliminar/{id}")
    public String eliminarInventario(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            ProductoColorTalla pct = productoColorTallaService.getProductoColorTalla(id);
            if (pct == null) {
                redirectAttributes.addFlashAttribute("error", "No se pudo encontrar la combinación solicitada");
                return "redirect:/admin/productos";
            }
            
            Long idProducto = pct.getProducto().getIdProducto();
            productoColorTallaService.eliminar(id);
            redirectAttributes.addFlashAttribute("mensaje", "Combinación eliminada correctamente");
            return "redirect:/admin/productos/inventario/" + idProducto;
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al eliminar la combinación: " + e.getMessage());
            return "redirect:/admin/productos";
        }
    }

    /**
     * Método para mostrar los productos de la categoría Ropa Deportiva.
     * 
     * @param model Modelo para la vista
     * @return Vista de listado de productos por categoría
     */
    @GetMapping("/ropa-deportiva")
    public String listarRopaDeportiva(Model model) {
        var productos = productoService.obtenerPorCategoria("Ropa Deportiva");
        model.addAttribute("productos", productos);
        model.addAttribute("categoria", "Ropa Deportiva");
        return "admin/categoria-listado";
    }

    /**
     * Método para mostrar los productos de la categoría Suplementos.
     * 
     * @param model Modelo para la vista
     * @return Vista de listado de productos por categoría
     */
    @GetMapping("/suplementos")
    public String listarSuplementos(Model model) {
        var productos = productoService.obtenerPorCategoria("Suplementos");
        model.addAttribute("productos", productos);
        model.addAttribute("categoria", "Suplementos");
        return "admin/categoria-listado";
    }
    
    /**
     * Método para mostrar los productos de la categoría Accesorios.
     * 
     * @param model Modelo para la vista
     * @return Vista de listado de productos por categoría
     */
    @GetMapping("/accesorios")
    public String listarAccesorios(Model model) {
        var productos = productoService.obtenerPorCategoria("Accesorios");
        model.addAttribute("productos", productos);
        model.addAttribute("categoria", "Accesorios");
        return "admin/categoria-listado";
    }
}
