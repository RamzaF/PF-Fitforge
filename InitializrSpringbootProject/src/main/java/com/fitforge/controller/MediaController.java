package com.fitforge.controller;

import com.fitforge.service.LocalStorageService;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MediaController {

    private final LocalStorageService storageService;

    public MediaController(LocalStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping({"/media", "/media/"})
    public ResponseEntity<Void> sinListadoDirectorio() {
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/media/{filename}")
    public ResponseEntity<Resource> servirMedio(@PathVariable("filename") String filename) {
        return storageService.cargarRecurso(filename)
                .map(media -> ResponseEntity.ok()
                        .contentType(media.mediaType())
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "default-src 'none'")
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                        .body(media.resource()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
