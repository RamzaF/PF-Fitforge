package com.fitforge.service.impl;

import com.fitforge.service.LocalStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class LocalStorageServiceImpl implements LocalStorageService {

    private final String UPLOAD_DIR = "src/main/resources/static/uploads/";
    private final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif"};

    @Override
    public String guardarImagen(MultipartFile archivo, Long id) throws IOException {
        // Asegura que el directorio de almacenamiento existe
        File directorio = new File(UPLOAD_DIR);
        if (!directorio.exists()) {
            directorio.mkdirs();
        }

        // Validar que el archivo no esté vacio
        if (archivo.isEmpty()) {
            throw new IOException("El archivo está vacío.");
        }

        // Validar el tamaño del archivo
        if (archivo.getSize() > MAX_FILE_SIZE) {
            throw new IOException("El archivo excede el tamaño máximo permitido (5MB).");
        }

        // Obtener la extensión del archivo original
        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null || !nombreOriginal.contains(".")) {
            throw new IOException("El archivo no tiene una extensión válida.");
        }
        String extension = nombreOriginal.substring(nombreOriginal.lastIndexOf(".")).toLowerCase();

        // Validar la extensión del archivo
        boolean extensionValida = false;
        for (String ext : ALLOWED_EXTENSIONS) {
            if (extension.equals(ext)) {
                extensionValida = true;
                break;
            }
        }
        if (!extensionValida) {
            throw new IOException("Formato de archivo no permitido. Solo se permiten imágenes (.jpg, .jpeg, .png, .gif).");
        }

        // Generar un nombre único para el archivo
        String nombreArchivo = "img" + String.format("%019d", id) + extension;

        // Crear la ruta final del archivo
        Path rutaArchivo = Paths.get(UPLOAD_DIR + nombreArchivo);

        // Guardar el archivo en el sistema de archivos
        Files.write(rutaArchivo, archivo.getBytes());

        // Retornar la URL relativa
        return "/uploads/" + nombreArchivo;
    }
}
