package com.fitforge.controller;

import com.fitforge.service.LocalStorageService;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imagenes")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class ImagenController {

    private final LocalStorageService localStorageService;

    public ImagenController(LocalStorageService localStorageService) {
        this.localStorageService = localStorageService;
    }

    @PostMapping("/subir")
    public ResponseEntity<Map<String, String>> subirImagen(@RequestParam("archivo") MultipartFile archivo) {
        try {
            var validada = localStorageService.validarImagen(archivo);
            String url = localStorageService.guardarImagenValidada(validada);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo almacenar la imagen."));
        }
    }
}
