package com.fitforge.controller;

import com.fitforge.domain.Producto;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.LocalStorageService;
import com.fitforge.service.ProductoService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador para la administración de productos.
 * 
 * @author ktrejos
 */
@Controller
@RequestMapping("/admin/productos")
@Secured("ROLE_ADMIN")
public class AdminProductoController {

    private static final Logger logger = LoggerFactory.getLogger(AdminProductoController.class);
    
    @Autowired
    private ProductoService productoService;
    
    @Autowired
    private CategoriaService categoriaService;
    
    @Autowired
    private LocalStorageService localStorageService;
    
    /**
     * Muestra la lista de todos los productos para administración.
     * 
     * @param model Modelo de la vista
     * @return Vista de listado de productos
     */
    @GetMapping("/listado")
    public String listarProductos(Model model) {
        try {
            var productos = productoService.listarProductos();
            var categorias = categoriaService.getCategoria(true);
            
            model.addAttribute("productos", productos);
            model.addAttribute("categorias", categorias);
            model.addAttribute("totalProductos", productos.size());
            return "admin/productos/listado";
        } catch (Exception e) {
            logger.error("Error al listar productos: {}", e.getMessage());
            model.addAttribute("error", "Ocurrió un error al cargar los productos");
            return "redirect:/admin";
        }
    }
    
    /**
     * Muestra la página de edición de un producto específico.
     * 
     * @param id ID del producto a editar
     * @param model Modelo de la vista
     * @return Vista de edición de producto
     */
    @GetMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id, Model model) {
        try {
            var producto = productoService.getProductoById(id);
            
            if (producto == null) {
                return "redirect:/admin/productos/listado?mensaje=Producto+no+encontrado&tipo=danger";
            }
            
            var categorias = categoriaService.getCategoria(true);
            model.addAttribute("producto", producto);
            model.addAttribute("categorias", categorias);
            return "admin/productos/modifica";
        } catch (Exception e) {
            logger.error("Error al cargar producto para edición: {}", e.getMessage());
            return "redirect:/admin/productos/listado?mensaje=Error+al+cargar+el+producto&tipo=danger";
        }
    }
    
    /**
     * Busca productos por término.
     * 
     * @param termino Término de búsqueda
     * @param model Modelo de la vista
     * @return Vista de listado con resultados filtrados
     */
    @GetMapping("/buscar")
    public String buscarProductos(@RequestParam(required = false) String termino, Model model) {
        try {
            var productos = productoService.buscarProductos(termino);
            var categorias = categoriaService.getCategoria(true);
            
            model.addAttribute("productos", productos);
            model.addAttribute("categorias", categorias);
            model.addAttribute("totalProductos", productos.size());
            model.addAttribute("terminoBusqueda", termino);
            return "admin/productos/listado";
        } catch (Exception e) {
            logger.error("Error al buscar productos: {}", e.getMessage());
            return "redirect:/admin/productos/listado?mensaje=Error+en+la+búsqueda&tipo=danger";
        }
    }
    
    /**
     * Guarda un producto nuevo o actualiza uno existente.
     * 
     * @param producto Producto a guardar
     * @param imagenFile Archivo de imagen (opcional)
     * @param redirectAttributes Para mensajes de redirección
     * @return Redirección a listado o página de edición
     */
    @PostMapping("/guardar-producto")
    public String guardarProducto(
            @ModelAttribute Producto producto,
            @RequestParam(value = "imagenFile", required = false) MultipartFile imagenFile,
            RedirectAttributes redirectAttributes) {
        
        try {
            boolean esNuevo = (producto.getIdProducto() == null || producto.getIdProducto() == 0);
            
            // Si es un producto nuevo, guardarlo primero para obtener el ID
            if (esNuevo) {
                productoService.guardarProducto(producto);
            }
            
            // Procesar imagen si se ha subido una nueva
            if (imagenFile != null && !imagenFile.isEmpty()) {
                try {
                    String rutaImagen = localStorageService.guardarImagen(imagenFile, producto.getIdProducto());
                    producto.setImagen(rutaImagen);
                } catch (IOException e) {
                    logger.error("Error al guardar imagen: {}", e.getMessage());
                    redirectAttributes.addFlashAttribute("mensaje", "El producto se guardó pero hubo un problema con la imagen: " + e.getMessage());
                    redirectAttributes.addFlashAttribute("tipo", "warning");
                }
            }
            
            // Guardar el producto con la imagen actualizada si corresponde
            productoService.guardarProducto(producto);
            
            // Mensaje de éxito
            String mensaje = esNuevo ? "Producto creado correctamente" : "Producto actualizado correctamente";
            redirectAttributes.addFlashAttribute("mensaje", mensaje);
            redirectAttributes.addFlashAttribute("tipo", "success");
            
            // Redirección dependiendo de si es nuevo o edición
            if (esNuevo) {
                return "redirect:/admin/productos/listado";
            } else {
                return "redirect:/admin/productos/editar/" + producto.getIdProducto();
            }
            
        } catch (Exception e) {
            logger.error("Error al guardar producto: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", "Error al guardar el producto: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
            return "redirect:/admin/productos/listado";
        }
    }
    
    /**
     * Elimina un producto (lo marca como inactivo).
     * 
     * @param idProducto ID del producto a eliminar
     * @param redirectAttributes Para mensajes de redirección
     * @return Redirección a listado
     */
    @PostMapping("/eliminar")
    public String eliminarProducto(
            @RequestParam Long idProducto,
            RedirectAttributes redirectAttributes) {
        
        try {
            // Obtener el producto
            Producto producto = productoService.obtenerProductoPorId(idProducto);
            
            if (producto == null) {
                redirectAttributes.addFlashAttribute("mensaje", "Producto no encontrado");
                redirectAttributes.addFlashAttribute("tipo", "danger");
                return "redirect:/admin/productos/listado";
            }
            
            // Marcar como inactivo en lugar de eliminar físicamente
            producto.setActivo(false);
            productoService.guardarProducto(producto);
            
            redirectAttributes.addFlashAttribute("mensaje", "Producto desactivado correctamente");
            redirectAttributes.addFlashAttribute("tipo", "success");
            return "redirect:/admin/productos/listado";
            
        } catch (Exception e) {
            logger.error("Error al eliminar producto: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", "Error al desactivar el producto: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
            return "redirect:/admin/productos/listado";
        }
    }
    
    /**
     * Obtiene productos por categoría.
     * 
     * @param categoria Nombre de la categoría
     * @param model Modelo de la vista
     * @return Vista de listado con productos filtrados por categoría
     */
    @GetMapping("/categoria/{categoria}")
    public String productosPorCategoria(@PathVariable String categoria, Model model) {
        try {
            var productos = productoService.obtenerPorCategoria(categoria);
            var categorias = categoriaService.getCategoria(true);
            
            model.addAttribute("productos", productos);
            model.addAttribute("categorias", categorias);
            model.addAttribute("categoriaActual", categoria);
            model.addAttribute("totalProductos", productos.size());
            return "admin/productos/listado";
        } catch (Exception e) {
            logger.error("Error al obtener productos por categoría: {}", e.getMessage());
            return "redirect:/admin/productos/listado?mensaje=Error+al+filtrar+por+categoría&tipo=danger";
        }
    }
    
    /**
     * Redirige a la página de inventario para un producto específico.
     * 
     * @param id ID del producto
     * @param model Modelo de la vista
     * @return Vista de gestión de inventario
     */
    @GetMapping("/inventario/{id}")
    public String gestionarInventario(@PathVariable Long id, Model model) {
        try {
            var producto = productoService.getProductoById(id);
            
            if (producto == null) {
                return "redirect:/admin/productos/listado?mensaje=Producto+no+encontrado&tipo=danger";
            }
            
            model.addAttribute("producto", producto);
            return "admin/productos/inventario";
        } catch (Exception e) {
            logger.error("Error al cargar gestión de inventario: {}", e.getMessage());
            return "redirect:/admin/productos/listado?mensaje=Error+al+cargar+el+inventario&tipo=danger";
        }
    }
}

