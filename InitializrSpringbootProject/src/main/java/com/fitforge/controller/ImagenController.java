package com.fitforge.controller;

import com.fitforge.service.LocalStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    private final LocalStorageService localStorageService;
    private final AtomicLong contador = new AtomicLong(1); // Generar IDs únicos si no hay otro sistema de IDs

    public ImagenController(LocalStorageService localStorageService) {
        this.localStorageService = localStorageService;
    }

    @PostMapping("/subir")
    public ResponseEntity<String> subirImagen(@RequestParam("archivo") MultipartFile archivo) {
        try {
            // Obtener un ID único para la imagen
            Long id = contador.getAndIncrement();

            // Guardar la imagen
            String ruta = localStorageService.guardarImagen(archivo, id);

            return ResponseEntity.ok(ruta);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al subir la imagen: " + e.getMessage());
        }
    }
}
