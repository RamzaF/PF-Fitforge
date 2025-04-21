package com.fitforge.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fitforge.service.ProductoService;

@Controller
public class IndexController {

    @Autowired
    private ProductoService productoService;

    @GetMapping("/")
    public String index(Model model) {

        // Obtengo la lista de productos activos desde la base de datos
        var productos = productoService.getProductos(true);

        // Envio la lista de productos a la vista para mostrar productos destacados
        model.addAttribute("productos", productos);

        // Retorno la vista index.html
        return "index";
    }
}
