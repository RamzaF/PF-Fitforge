package com.fitforge.controller;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fitforge.domain.Producto;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.LocalStorageService;
import com.fitforge.service.ProductoColorTallaService;
import com.fitforge.service.ProductoService;

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
    
    @Autowired
    private ProductoColorTallaService productoColorTallaService;
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
            var colores = productoColorTallaService.listarColores();
            var tallas = productoColorTallaService.listarTallas();
            model.addAttribute("productos", productos);
            model.addAttribute("categorias", categorias);
            model.addAttribute("colores", colores);
            model.addAttribute("tallas", tallas);
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
    /**
     * Método para mostrar el formulario de edición de un producto existente.
     * 
     * @param id ID del producto a editar
     * @param model Modelo para la vista
     * @param redirectAttributes Atributos para mensajes de redirección
     * @return Vista de formulario de producto o redirección si no existe
     */
    @GetMapping("/editar/{id}")
    public String editarProducto(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var producto = productoService.getProductoById(id);
            
            if (producto == null) {
                redirectAttributes.addFlashAttribute("mensaje", "Producto no encontrado");
                redirectAttributes.addFlashAttribute("tipo", "danger");
                return "redirect:/admin/productos/listado";
            }
            
            var categorias = categoriaService.getCategoria(true);
            model.addAttribute("producto", producto);
            model.addAttribute("categorias", categorias);
            model.addAttribute("titulo", "Editar Producto");
            return "admin/productos/formulario";
        } catch (Exception e) {
            logger.error("Error al cargar producto para edición: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", "Error al cargar el producto: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
            return "redirect:/admin/productos/listado";
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
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @RequestParam(value = "color.idColor", required = false) Long idColor,
            @RequestParam(value = "talla.idTalla", required = false) Long idTalla,
            @RequestParam(value = "existencias", required = false) Integer existencias,
            RedirectAttributes redirectAttributes) {
        
        try {
            boolean esNuevo = (producto.getIdProducto() == null || producto.getIdProducto() == 0);
            logger.info("Guardando producto: {} (esNuevo: {}, activo: {})", producto.getDescripcion(), esNuevo, producto.isActivo());
            
            // Para productos nuevos, asegurarse que el ID sea null para auto-generar
            if (esNuevo) {
                producto.setIdProducto(null);
            } else {
                // Para productos existentes, obtener el producto actual para asegurar que mantengamos todos los datos
                Producto productoActual = productoService.obtenerProductoPorId(producto.getIdProducto());
                if (productoActual != null) {
                    // Conservar la imagen si no se está subiendo una nueva
                    if ((archivo == null || archivo.isEmpty()) && productoActual.getRutaImagen() != null) {
                        producto.setRutaImagen(productoActual.getRutaImagen());
                    }
                    
                    // Mantener la colección de ProductoColorTalla
                    producto.setProductoColorTallas(productoActual.getProductoColorTallas());
                }
            }
            
            // Si hay archivo de imagen, procesarlo
            if (archivo != null && !archivo.isEmpty()) {
                try {
                    // Para productos nuevos, necesitamos guardar primero para obtener el ID
                    if (esNuevo) {
                        productoService.guardarProducto(producto);
                    }
                    
                    // Guardar la imagen y actualizar la ruta
                    String rutaImagen = localStorageService.guardarImagen(archivo, producto.getIdProducto());
                    producto.setRutaImagen(rutaImagen);
                    logger.info("Imagen guardada para producto ID: {}, ruta: {}", producto.getIdProducto(), rutaImagen);
                } catch (IOException e) {
                    logger.error("Error al guardar imagen: {}", e.getMessage());
                    redirectAttributes.addFlashAttribute("mensaje", "Error al procesar la imagen: " + e.getMessage());
                    redirectAttributes.addFlashAttribute("tipo", "warning");
                }
            }
            
            // Guardar producto con forzamiento de estado activo
            logger.info("Estado del producto antes de guardar: activo={}", producto.isActivo());
            productoService.guardarProducto(producto);
            
            // Crear entrada de inventario inicial para productos nuevos
            if (esNuevo && idColor != null && idTalla != null && existencias != null) {
                var color = productoColorTallaService.listarColores().stream().filter(c -> c.getIdColor().equals(idColor)).findFirst().orElse(null);
                var talla = productoColorTallaService.listarTallas().stream().filter(t -> t.getIdTalla().equals(idTalla)).findFirst().orElse(null);
                if (color != null && talla != null) {
                    ProductoColorTalla inventarioInicial = new ProductoColorTalla();
                    inventarioInicial.setProducto(producto);
                    inventarioInicial.setColor(color);
                    inventarioInicial.setTalla(talla);
                    inventarioInicial.setExistencias(existencias);
                    productoColorTallaService.guardar(inventarioInicial);
                }
            }
            
            // Forzar refrescar el producto desde la base de datos para confirmar que el estado es correcto
            Producto productoGuardado = productoService.obtenerProductoPorId(producto.getIdProducto());
            logger.info("Producto guardado correctamente, ID: {}, estado activo: {}", 
                      productoGuardado.getIdProducto(), productoGuardado.isActivo());
            
            // Mensaje de éxito
            String mensaje = esNuevo ? "Producto creado correctamente" : "Producto actualizado correctamente";
            redirectAttributes.addFlashAttribute("mensaje", mensaje);
            redirectAttributes.addFlashAttribute("tipo", "success");
            
            // Siempre redirigir al listado después de guardar
            return "redirect:/admin/productos/listado";
            
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
}

