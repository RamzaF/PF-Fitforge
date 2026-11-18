package com.fitforge.service.impl;

import com.fitforge.service.LocalStorageService;
import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalStorageServiceImpl implements LocalStorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageServiceImpl.class);
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5 MB
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 25_000_000L;
    private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final Pattern SAFE_MEDIA_FILENAME = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(png|jpg)$");
    private static final Pattern STAGING_FILENAME = Pattern.compile("^\\.tmp-[0-9a-fA-F-]+\\.part$");
    private static final String MEDIA_PREFIX = "/media/";

    private final Path baseDir;

    public LocalStorageServiceImpl(
            @Value("${fitforge.storage.uploads-dir:/var/fitforge/uploads}") String uploadsDir) {
        this.baseDir = Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    /**
     * Inicializa el directorio de medios y limpia únicamente temporales huérfanos de staging
     * (.tmp-*.part) que hayan quedado tras una caída abrupta del proceso.
     */
    @PostConstruct
    public void inicializarYLimpiarTemporales() {
        try {
            Files.createDirectories(baseDir);
            try (var entries = Files.list(baseDir)) {
                entries.filter(Files::isRegularFile)
                        .filter(p -> STAGING_FILENAME.matcher(p.getFileName().toString()).matches())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ex) {
                                logger.warn("No se pudo eliminar temporal huérfano {}: {}", p.getFileName(), ex.getMessage());
                            }
                        });
            }
        } catch (IOException ex) {
            logger.warn("No se pudo inicializar el directorio de medios {}: {}", baseDir, ex.getMessage());
        }
    }

    @Override
    public String guardarImagen(MultipartFile archivo, Long id) throws IOException {
        ValidatedImage validated = validarImagen(archivo);
        return guardarImagenValidada(validated);
    }

    @Override
    public ValidatedImage validarImagen(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar una imagen.");
        }
        if (archivo.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen excede el tamaño máximo permitido (5 MB).");
        }

        String originalName = archivo.getOriginalFilename();
        if (originalName != null && !originalName.isBlank()) {
            int dotIdx = originalName.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < originalName.length() - 1) {
                String ext = originalName.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
                if (!ALLOWED_UPLOAD_EXTENSIONS.contains(ext)) {
                    throw new IllegalArgumentException(
                            "Formato de archivo no permitido. Solo se permiten imágenes PNG y JPG/JPEG.");
                }
            }
        }

        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo enviado.");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("El archivo de imagen está vacío.");
        }
        if (bytes.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen excede el tamaño máximo permitido (5 MB).");
        }

        String expectedFormat;
        String canonicalExt;
        MediaType mediaType;
        if (esPng(bytes)) {
            expectedFormat = "png";
            canonicalExt = "png";
            mediaType = MediaType.IMAGE_PNG;
        } else if (esJpeg(bytes)) {
            expectedFormat = "jpeg";
            canonicalExt = "jpg";
            mediaType = MediaType.IMAGE_JPEG;
        } else {
            throw new IllegalArgumentException(
                    "El contenido del archivo no corresponde a una imagen PNG o JPEG válida.");
        }

        decodificarYValidarDimensiones(bytes, expectedFormat);
        return new ValidatedImage(bytes, canonicalExt, mediaType);
    }

    @Override
    public String guardarImagenValidada(ValidatedImage imagen) throws IOException {
        if (imagen == null || imagen.bytes() == null || imagen.bytes().length == 0) {
            throw new IllegalArgumentException("Imagen validada requerida.");
        }
        Files.createDirectories(baseDir);
        String finalName = UUID.randomUUID() + "." + imagen.canonicalExtension();
        Path tempPath = baseDir.resolve(".tmp-" + UUID.randomUUID() + ".part").normalize();
        Path finalPath = baseDir.resolve(finalName).normalize();
        if (!tempPath.startsWith(baseDir) || !finalPath.startsWith(baseDir)) {
            throw new IOException("Ruta de almacenamiento fuera de directorio autorizado.");
        }

        try {
            Files.write(tempPath, imagen.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return MEDIA_PREFIX + finalName;
        } catch (IOException | RuntimeException ex) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupEx) {
                logger.warn("No se pudo limpiar archivo temporal {}: {}", tempPath.getFileName(), cleanupEx.getMessage());
            }
            throw ex;
        }
    }

    @Override
    public java.util.Optional<MediaResource> cargarRecurso(String filename) {
        if (filename == null || !SAFE_MEDIA_FILENAME.matcher(filename).matches()) {
            return java.util.Optional.empty();
        }
        Path resolved = baseDir.resolve(filename).normalize();
        if (!resolved.startsWith(baseDir)
                || Files.isSymbolicLink(resolved)
                || !Files.isRegularFile(resolved)
                || !Files.isReadable(resolved)) {
            return java.util.Optional.empty();
        }
        MediaType mediaType = filename.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return java.util.Optional.of(new MediaResource(new FileSystemResource(resolved), mediaType));
    }

    @Override
    public void eliminarMediaGestionada(String rutaImagen) {
        if (rutaImagen == null || !rutaImagen.startsWith(MEDIA_PREFIX)) {
            return;
        }
        String filename = rutaImagen.substring(MEDIA_PREFIX.length());
        if (!SAFE_MEDIA_FILENAME.matcher(filename).matches()) {
            return;
        }
        Path resolved = baseDir.resolve(filename).normalize();
        if (!resolved.startsWith(baseDir) || Files.isSymbolicLink(resolved)) {
            return;
        }
        try {
            Files.deleteIfExists(resolved);
        } catch (IOException ex) {
            logger.warn("No se pudo eliminar el medio anterior {}: {}", filename, ex.getMessage());
        }
    }

    private boolean esPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private boolean esJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private void decodificarYValidarDimensiones(byte[] bytes, String expectedFormat) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                throw new IllegalArgumentException("No se pudo leer el flujo de imagen.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("No se encontró un decodificador compatible para la imagen.");
            }
            ImageReader reader = readers.next();
            try {
                String actualFormat = reader.getFormatName() == null
                        ? ""
                        : reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean formatMatches = ("png".equals(expectedFormat) && "png".equals(actualFormat))
                        || ("jpeg".equals(expectedFormat) && ("jpeg".equals(actualFormat) || "jpg".equals(actualFormat)));
                if (!formatMatches) {
                    throw new IllegalArgumentException("El formato real de la imagen no coincide con su cabecera.");
                }
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                            "Las dimensiones de la imagen exceden los límites permitidos (máximo 8192x8192 y 25 MP).");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
                    throw new IllegalArgumentException("La imagen está dañada o incompleta.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "No se pudo decodificar la imagen; verificá que sea un archivo PNG o JPEG válido.");
        }
    }
}
