package com.fitforge.controller;

import com.fitforge.domain.Color;
import com.fitforge.service.ColorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/colores")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminColorController {

    private final ColorService colorService;

    public AdminColorController(ColorService colorService) {
        this.colorService = colorService;
    }

    @GetMapping({"", "/"})
    public String redirigirListado() {
        return "redirect:/admin/colores/listado";
    }

    @GetMapping("/listado")
    public String listar(@RequestParam(required = false) Long editarId, Model model) {
        model.addAttribute("colores", colorService.listarTodos());
        Color colorForm = editarId != null ? colorService.obtenerPorId(editarId) : null;
        if (colorForm == null) {
            colorForm = new Color();
            colorForm.setActivo(true);
        }
        model.addAttribute("colorForm", colorForm);
        return "admin/colores/listado";
    }

    @PostMapping("/guardar")
    public String guardar(
            @RequestParam(required = false) Long idColor,
            @RequestParam String nombreColor,
            @RequestParam(defaultValue = "false") boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            boolean esNuevo = idColor == null || idColor == 0;
            colorService.guardar(idColor, nombreColor, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", esNuevo ? "Color creado correctamente." : "Color actualizado correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensaje", ex.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return "redirect:/admin/colores/listado";
    }

    @PostMapping("/estado")
    public String cambiarEstado(
            @RequestParam Long idColor,
            @RequestParam boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            colorService.cambiarEstado(idColor, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", activo ? "Color activado correctamente." : "Color desactivado correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensaje", ex.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return "redirect:/admin/colores/listado";
    }
}
