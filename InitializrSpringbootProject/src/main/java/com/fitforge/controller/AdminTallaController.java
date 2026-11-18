package com.fitforge.controller;

import com.fitforge.domain.Talla;
import com.fitforge.service.TallaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/tallas")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminTallaController {

    private final TallaService tallaService;

    public AdminTallaController(TallaService tallaService) {
        this.tallaService = tallaService;
    }

    @GetMapping({"", "/"})
    public String redirigirListado() {
        return "redirect:/admin/tallas/listado";
    }

    @GetMapping("/listado")
    public String listar(@RequestParam(required = false) Long editarId, Model model) {
        model.addAttribute("tallas", tallaService.listarTodos());
        Talla tallaForm = editarId != null ? tallaService.obtenerPorId(editarId) : null;
        if (tallaForm == null) {
            tallaForm = new Talla();
            tallaForm.setActivo(true);
        }
        model.addAttribute("tallaForm", tallaForm);
        return "admin/tallas/listado";
    }

    @PostMapping("/guardar")
    public String guardar(
            @RequestParam(required = false) Long idTalla,
            @RequestParam String nombreTalla,
            @RequestParam(defaultValue = "false") boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            boolean esNueva = idTalla == null || idTalla == 0;
            tallaService.guardar(idTalla, nombreTalla, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", esNueva ? "Talla creada correctamente." : "Talla actualizada correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensaje", ex.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return "redirect:/admin/tallas/listado";
    }

    @PostMapping("/estado")
    public String cambiarEstado(
            @RequestParam Long idTalla,
            @RequestParam boolean activo,
            RedirectAttributes redirectAttributes) {
        try {
            tallaService.cambiarEstado(idTalla, activo);
            redirectAttributes.addFlashAttribute(
                    "mensaje", activo ? "Talla activada correctamente." : "Talla desactivada correctamente.");
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensaje", ex.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return "redirect:/admin/tallas/listado";
    }
}
