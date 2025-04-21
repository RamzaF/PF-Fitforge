package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fitforge.domain.Item;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.service.ItemService;
import com.fitforge.service.ProductoColorTallaService;

@Controller
@RequestMapping("/carrito")
public class CarritoController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ProductoColorTallaService productoColorTallaService;

    @GetMapping("")
    public String redirigirCarrito() {
        return "redirect:/carrito/ver";
    }

    @GetMapping("/ver")
    public String verCarrito(Model model) {
        model.addAttribute("items", itemService.getItems());
        model.addAttribute("total", itemService.getTotal());
        return "carrito/listado";
    }

    @GetMapping("/agregar/{id}")
    public String agregar(@PathVariable("id") Long id) {
        ProductoColorTalla pct = productoColorTallaService.getProductoColorTalla(id);
        if (pct != null && pct.getExistencias() > 0) {
            itemService.save(new Item(pct));
        }
        return "redirect:/carrito/ver";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable("id") Long id) {
        itemService.delete(id);
        return "redirect:/carrito/ver";
    }

    @PostMapping("/actualizar")
    public String actualizar(@RequestParam("id") Long id,
                             @RequestParam("cantidad") int cantidad) {
        itemService.updateCantidad(id, cantidad);
        return "redirect:/carrito/ver";
    }
}
