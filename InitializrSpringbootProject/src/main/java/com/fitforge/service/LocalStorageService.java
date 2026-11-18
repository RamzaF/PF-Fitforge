package com.fitforge.service;

import java.io.IOException;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

public interface LocalStorageService {

    record ValidatedImage(byte[] bytes, String canonicalExtension, MediaType mediaType) {}

    record MediaResource(Resource resource, MediaType mediaType) {}

    String guardarImagen(MultipartFile archivo, Long id) throws IOException;

    ValidatedImage validarImagen(MultipartFile archivo);

    String guardarImagenValidada(ValidatedImage imagen) throws IOException;

    Optional<MediaResource> cargarRecurso(String filename);

    void eliminarMediaGestionada(String rutaImagen);
}
