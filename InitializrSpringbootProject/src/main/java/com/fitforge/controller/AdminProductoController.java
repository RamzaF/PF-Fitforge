package com.fitforge.controller;

import java.util.ArrayList;
import java.util.List;
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

import com.fitforge.domain.Categoria;
import com.fitforge.domain.Producto;
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
    private ProductoColorTallaService productoColorTallaService;

    @Autowired
    private LocalStorageService localStorageService;

    private void cargarCatalogosModal(Model model) {
        model.addAttribute("categorias", categoriaService.getCategoria(true));
        model.addAttribute("colores", productoColorTallaService.listarColoresActivos());
        model.addAttribute("tallas", productoColorTallaService.listarTallasActivas());
    }

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
            cargarCatalogosModal(model);
            model.addAttribute("productos", productos);
            model.addAttribute("totalProductos", productos.size());
            return "admin/productos/listado";
        } catch (Exception e) {
            logger.error("Error al listar productos: {}", e.getMessage());
            model.addAttribute("error", "Ocurrió un error al cargar los productos");
            return "redirect:/admin";
        }
    }
    
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
            
            List<Categoria> categorias = new ArrayList<>(categoriaService.getCategoria(true));
            if (producto.getCategoria() != null
                    && categorias.stream().noneMatch(c -> c.getIdCategoria().equals(producto.getCategoria().getIdCategoria()))) {
                categorias.add(producto.getCategoria());
            }
            model.addAttribute("producto", producto);
            model.addAttribute("categorias", categorias);
            model.addAttribute("variantes", productoColorTallaService.listarPorProducto(id));
            model.addAttribute("variantesVendidas", productoColorTallaService.variantesConHistorialDeProducto(id));
            model.addAttribute("colores", productoColorTallaService.listarColoresActivos());
            model.addAttribute("tallas", productoColorTallaService.listarTallasActivas());
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
            cargarCatalogosModal(model);
            model.addAttribute("productos", productos);
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
     * @param archivo Archivo de imagen (opcional)
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

        boolean esNuevo = (producto.getIdProducto() == null || producto.getIdProducto() == 0);
        Producto productoActual = null;
        String imagenAnterior = null;
        String nuevaMediaUrl = null;

        if (!esNuevo) {
            productoActual = productoService.obtenerProductoPorId(producto.getIdProducto());
            if (productoActual == null) {
                redirectAttributes.addFlashAttribute("mensaje", "Producto no encontrado");
                redirectAttributes.addFlashAttribute("tipo", "danger");
                return "redirect:/admin/productos/listado";
            }
            imagenAnterior = productoActual.getRutaImagen();
        }

        try {
            if (archivo != null && !archivo.isEmpty()) {
                var imagenValidada = localStorageService.validarImagen(archivo);
                nuevaMediaUrl = localStorageService.guardarImagenValidada(imagenValidada);
                producto.setRutaImagen(nuevaMediaUrl);
            } else {
                producto.setRutaImagen(esNuevo ? null : imagenAnterior);
            }

            logger.info("Guardando producto: {} (esNuevo: {}, activo: {})", producto.getDescripcion(), esNuevo, producto.isActivo());

            if (esNuevo) {
                producto.setIdProducto(null);
                productoService.guardarProductoConVarianteInicial(producto, idColor, idTalla, existencias);
            } else {
                productoService.guardarProducto(producto);
            }

            if (nuevaMediaUrl != null && imagenAnterior != null
                    && !imagenAnterior.equals(nuevaMediaUrl)
                    && productoService.contarProductosPorImagen(imagenAnterior) == 0) {
                localStorageService.eliminarMediaGestionada(imagenAnterior);
            }

            Producto productoGuardado = productoService.obtenerProductoPorId(producto.getIdProducto());
            logger.info("Producto guardado correctamente, ID: {}, estado activo: {}",
                    productoGuardado != null ? productoGuardado.getIdProducto() : producto.getIdProducto(),
                    productoGuardado != null ? productoGuardado.isActivo() : producto.isActivo());

            String mensaje = esNuevo ? "Producto creado correctamente" : "Producto actualizado correctamente";
            redirectAttributes.addFlashAttribute("mensaje", mensaje);
            redirectAttributes.addFlashAttribute("tipo", "success");

            return "redirect:/admin/productos/listado";

        } catch (Exception e) {
            if (nuevaMediaUrl != null) {
                localStorageService.eliminarMediaGestionada(nuevaMediaUrl);
            }
            logger.error("Error al guardar producto: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", "Error al guardar el producto: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
            if (!esNuevo && productoActual != null) {
                return "redirect:/admin/productos/editar/" + productoActual.getIdProducto();
            }
            return "redirect:/admin/productos/listado";
        }
    }

    @PostMapping("/estado")
    public String cambiarEstadoProducto(
            @RequestParam Long idProducto,
            @RequestParam boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            productoService.cambiarEstado(idProducto, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", activo ? "Producto activado correctamente" : "Producto desactivado correctamente");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (Exception e) {
            logger.error("Error al cambiar estado del producto: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", "Error al cambiar el estado del producto: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return "redirect:/admin/productos/listado";
    }

    private String redirectDestinoVariante(Long idProducto, String origen) {
        if (idProducto == null) {
            return "redirect:/admin/productos/listado";
        }
        return "inventario".equalsIgnoreCase(origen)
                ? "redirect:/admin/productos/inventario/" + idProducto
                : "redirect:/admin/productos/editar/" + idProducto;
    }

    @PostMapping("/variantes/guardar")
    public String guardarVariante(
            @RequestParam Long idProducto,
            @RequestParam Long idColor,
            @RequestParam Long idTalla,
            @RequestParam(defaultValue = "0") int existencias,
            @RequestParam(required = false) String origen,
            RedirectAttributes redirectAttributes) {
        try {
            productoColorTallaService.guardarVariante(idProducto, idColor, idTalla, existencias);
            redirectAttributes.addFlashAttribute("mensaje", "Variante agregada correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (Exception e) {
            logger.error("Error al guardar variante: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return redirectDestinoVariante(idProducto, origen);
    }

    @PostMapping("/variantes/estado")
    public String cambiarEstadoVariante(
            @RequestParam Long idVariante,
            @RequestParam boolean activo,
            @RequestParam(required = false) Long idProducto,
            @RequestParam(required = false) String origen,
            RedirectAttributes redirectAttributes) {
        Long redirectProductId = idProducto;
        if (redirectProductId == null) {
            var variante = productoColorTallaService.getProductoColorTalla(idVariante);
            if (variante != null && variante.getProducto() != null) {
                redirectProductId = variante.getProducto().getIdProducto();
            }
        }
        try {
            productoColorTallaService.cambiarEstado(idVariante, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", activo ? "Variante activada correctamente." : "Variante desactivada correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (Exception e) {
            logger.error("Error al cambiar estado de variante: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return redirectDestinoVariante(redirectProductId, origen);
    }

    @PostMapping("/variantes/eliminar/{id}")
    public String eliminarVariante(
            @PathVariable Long id,
            @RequestParam(required = false) String origen,
            RedirectAttributes redirectAttributes) {
        var variante = productoColorTallaService.getProductoColorTalla(id);
        Long idProducto = (variante != null && variante.getProducto() != null)
                ? variante.getProducto().getIdProducto()
                : null;
        try {
            productoColorTallaService.eliminar(id);
            redirectAttributes.addFlashAttribute("mensaje", "Variante eliminada correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (Exception e) {
            logger.error("Error al eliminar variante: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", e.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return redirectDestinoVariante(idProducto, origen);
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
            Producto producto = productoService.obtenerProductoPorId(idProducto);
            
            if (producto == null) {
                redirectAttributes.addFlashAttribute("mensaje", "Producto no encontrado");
                redirectAttributes.addFlashAttribute("tipo", "danger");
                return "redirect:/admin/productos/listado";
            }
            
            productoService.eliminarProducto(producto.getIdProducto());
            
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
            cargarCatalogosModal(model);
            model.addAttribute("productos", productos);
            model.addAttribute("categoriaActual", categoria);
            model.addAttribute("totalProductos", productos.size());
            return "admin/productos/listado";
        } catch (Exception e) {
            logger.error("Error al obtener productos por categoría: {}", e.getMessage());
            return "redirect:/admin/productos/listado?mensaje=Error+al+filtrar+por+categoría&tipo=danger";
        }
    }
}

