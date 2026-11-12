package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fitforge.domain.Producto;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.ProductoService;

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
        return "redirect:/tienda/listado";
    }

    @GetMapping("/{id}")
    public String detallesProducto(@PathVariable("id") Long id, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean esAdmin = false;
        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if (authority.getAuthority().equals("ROLE_ADMIN")) {
                    esAdmin = true;
                    break;
                }
            }
        }
        if (esAdmin) {
            return "redirect:/admin/productos/listado";
        }
        Producto producto = productoService.getProductoById(id);
        if (producto != null) {
            boolean isTallaUnica = producto.getProductoColorTallas().stream()
                    .anyMatch(pct -> pct.getExistencias() > 0 && pct.getTalla() == null);
            boolean hasAvailableTallas = producto.getProductoColorTallas().stream()
                    .anyMatch(pct -> pct.getTalla() != null && pct.getExistencias() > 0);
            model.addAttribute("producto", producto);
            model.addAttribute("isTallaUnica", isTallaUnica);
            model.addAttribute("hasAvailableTallas", hasAvailableTallas);
            model.addAttribute("esAdmin", false);
            return "productos/productos";
        } else {
            return "redirect:/tienda/listado";
        }
    }
}