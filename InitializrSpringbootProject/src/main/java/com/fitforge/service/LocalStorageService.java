package com.fitforge.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface LocalStorageService {
    String guardarImagen(MultipartFile archivo, Long id) throws IOException;
}