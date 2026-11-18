package com.fitforge.controller;

import com.fitforge.service.InventarioService;
import com.fitforge.service.InventarioService.TipoMovimiento;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/inventario")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminInventarioController {

    private static final Logger logger = LoggerFactory.getLogger(AdminInventarioController.class);

    private final InventarioService inventarioService;

    public AdminInventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @GetMapping({"", "/"})
    public String listarGeneral(
            @RequestParam(required = false) String termino,
            @RequestParam(required = false, defaultValue = "todos") String filtroStock,
            Model model) {
        var variantes = inventarioService.listarInventarioGeneral(termino, filtroStock);
        var todas = (termino == null || termino.isBlank()) && "todos".equalsIgnoreCase(filtroStock)
                ? variantes
                : inventarioService.listarInventarioGeneral(null, "todos");

        long agotadas = todas.stream().filter(v -> v.varianteActiva() && v.existencias() == 0).count();
        long bajoStock = todas.stream().filter(v -> v.varianteActiva() && v.existencias() > 0 && v.existencias() <= 5).count();
        long unidadesTotales = todas.stream().filter(InventarioService.VarianteInventarioView::varianteActiva)
                .mapToLong(InventarioService.VarianteInventarioView::existencias).sum();

        model.addAttribute("variantes", variantes);
        model.addAttribute("movimientos", inventarioService.listarMovimientosRecientes(50));
        model.addAttribute("termino", termino);
        model.addAttribute("filtroStock", filtroStock);
        model.addAttribute("totalVariantes", todas.size());
        model.addAttribute("variantesAgotadas", agotadas);
        model.addAttribute("variantesStockBajo", bajoStock);
        model.addAttribute("unidadesTotales", unidadesTotales);
        return "admin/inventario/listado";
    }

    @PostMapping("/movimiento")
    public String registrarMovimiento(
            @RequestParam(required = false) Long idVariante,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String cantidad,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) Long idProducto,
            RedirectAttributes redirectAttributes) {
        try {
            if (tipo == null || tipo.isBlank()) {
                throw new IllegalArgumentException("Debe indicar el tipo de movimiento.");
            }
            TipoMovimiento tipoMov;
            try {
                tipoMov = TipoMovimiento.valueOf(tipo.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Tipo de movimiento no permitido.");
            }
            if (cantidad == null || cantidad.isBlank()) {
                throw new IllegalArgumentException("Debe ingresar una cantidad entera.");
            }
            int cantidadEntera;
            try {
                cantidadEntera = Integer.parseInt(cantidad.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("La cantidad debe ser un número entero válido dentro de rango.");
            }

            var movimiento = inventarioService.registrarMovimiento(
                    idVariante, idProducto, tipoMov, cantidadEntera, motivo);
            redirectAttributes.addFlashAttribute("mensaje",
                    String.format("Stock actualizado (%s): %d → %d (%+d) — %s",
                            movimiento.tipo(),
                            movimiento.cantidadAnterior(),
                            movimiento.cantidadNueva(),
                            movimiento.cantidadDelta(),
                            movimiento.motivo()));
            redirectAttributes.addFlashAttribute("tipo", "success");
        } catch (ResponseStatusException | AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensaje", ex.getMessage());
            redirectAttributes.addFlashAttribute("tipo", "danger");
        } catch (Exception ex) {
            logger.error("Error al registrar movimiento de inventario: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("mensaje", "No se pudo registrar el movimiento de inventario.");
            redirectAttributes.addFlashAttribute("tipo", "danger");
        }
        return idProducto != null
                ? "redirect:/admin/productos/inventario/" + idProducto
                : "redirect:/admin/inventario";
    }
}
