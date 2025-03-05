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

    @Override
    public String guardarImagen(MultipartFile archivo, Long id) throws IOException {
        // Asegurar que el directorio de almacenamiento existe
        File directorio = new File(UPLOAD_DIR);
        if (!directorio.exists()) {
            directorio.mkdirs();
        }

        // Obtener la extensión del archivo original
        String extension = archivo.getOriginalFilename().substring(archivo.getOriginalFilename().lastIndexOf("."));

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
