package com.fitforge.controller;

import com.fitforge.domain.Categoria;
import com.fitforge.service.CategoriaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categorias")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminCategoriaController {

    private final CategoriaService categoriaService;

    public AdminCategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping({"", "/"})
    public String redirigirListado() {
        return "redirect:/admin/categorias/listado";
    }

    @GetMapping("/listado")
    public String listar(@RequestParam(required = false) Long editarId, Model model) {
        model.addAttribute("categorias", categoriaService.getCategoria(false));
        Categoria categoriaForm = editarId != null ? categoriaService.obtenerPorId(editarId) : null;
        if (categoriaForm == null) {
            categoriaForm = new Categoria();
            categoriaForm.setActivo(true);
        }
        model.addAttribute("categoriaForm", categoriaForm);
        return "admin/categorias/listado";
    }

    @PostMapping("/guardar")
    public String guardar(
            @RequestParam(required = false) Long idCategoria,
            @RequestParam String descripcion,
            @RequestParam(defaultValue = "false") boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            boolean esNueva = idCategoria == null || idCategoria == 0;
            categoriaService.guardar(idCategoria, descripcion, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", esNueva ? "Categoría creada correctamente." : "Categoría actualizada correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensaje", ex.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return "redirect:/admin/categorias/listado";
    }

    @PostMapping("/estado")
    public String cambiarEstado(
            @RequestParam Long idCategoria,
            @RequestParam boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            categoriaService.cambiarEstado(idCategoria, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", activo ? "Categoría activada correctamente." : "Categoría desactivada correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensaje", ex.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return "redirect:/admin/categorias/listado";
    }
}
