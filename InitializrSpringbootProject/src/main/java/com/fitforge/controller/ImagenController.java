package com.fitforge.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    @PostMapping("/subir")
    public ResponseEntity<String> subirImagen(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Subida de imágenes no disponible en esta demo.");
    }
}
