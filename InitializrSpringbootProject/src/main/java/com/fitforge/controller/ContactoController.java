package com.fitforge.controller;

import com.fitforge.domain.Contacto;
import com.fitforge.service.ContactoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/contacto")
public class ContactoController {

    @Autowired
    private ContactoService contactoService;

    @GetMapping
    public String mostrarContacto(Model model) {
        model.addAttribute("contacto", new Contacto());
        return "contacto/contacto";
    }

    @PostMapping("/enviar")
    public String enviarContacto(@Valid Contacto contacto, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "contacto/contacto"; // Volver al formulario si hay errores
        }
        contacto.setActivo(true);
        contactoService.saveContacto(contacto);
        return "redirect:/contacto?exito";
    }
}
