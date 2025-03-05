package com.fitforge.controller;

import com.fitforge.service.LocalStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private LocalStorageService localStorageService;

    @PostMapping("/subir-imagen")
    public String subirImagen(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        return localStorageService.guardarImagen(archivo, 1L); // ID de prueba
    }
}
