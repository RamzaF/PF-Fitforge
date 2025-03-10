package com.fitforge.controller;

import com.fitforge.service.ContactoService;
import com.fitforge.domain.Contacto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/contacto")
public class ContactoController {

    @Autowired
    private ContactoService contactoService;

    @GetMapping("/contacto")
    public String mostrarContacto(Model model) {
        // Obtener la lista de contactos (si es necesario)
        List<Contacto> lista = contactoService.getContacto(false); 
        model.addAttribute("contactos", lista != null ? lista : Collections.emptyList());

        // Agregar un nuevo objeto Contacto para el formulario
        model.addAttribute("contacto", new Contacto());

        // Agregar el total de contactos (si es necesario)
        model.addAttribute("totalContactos", lista != null ? lista.size() : 0);

        // Retornar la vista 
        return "/contacto/contacto";
    }
}
