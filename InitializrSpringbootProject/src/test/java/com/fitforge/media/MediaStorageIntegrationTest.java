package com.fitforge.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitforge.service.OrderTestDatabase;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class MediaStorageIntegrationTest {

    @TempDir
    static Path uploadRoot;

    @DynamicPropertySource
    static void registerUploadDir(DynamicPropertyRegistry registry) {
        registry.add("fitforge.storage.uploads-dir", () -> uploadRoot.toAbsolutePath().toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabaseAndUploads() throws Exception {
        OrderTestDatabase.requireDisposable();
        cleanUploadRoot();
        restoreBaseline();
    }

    @AfterEach
    void cleanup() throws Exception {
        OrderTestDatabase.requireDisposable();
        cleanUploadRoot();
        restoreBaseline();
    }

    private void cleanUploadRoot() throws Exception {
        if (Files.exists(uploadRoot)) {
            try (var stream = Files.walk(uploadRoot)) {
                List<Path> paths = stream.sorted((a, b) -> b.compareTo(a)).toList();
                for (Path p : paths) {
                    if (!p.equals(uploadRoot)) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        }
    }

    private void restoreBaseline() {
        jdbc.update("DELETE FROM venta");
        jdbc.update("DELETE FROM factura");
        jdbc.update("DELETE FROM producto_color_talla");
        jdbc.update("DELETE FROM producto");
        jdbc.update("DELETE FROM color");
        jdbc.update("DELETE FROM talla");
        jdbc.update("DELETE FROM categoria");
        jdbc.update("INSERT INTO categoria (id_categoria,descripcion,activo) VALUES (1,'Ropa demo',true),(2,'Accesorios demo',true)");
        jdbc.update("INSERT INTO producto (id_producto,id_categoria,descripcion,detalle,precio,imagen,activo) VALUES "
                + "(1,1,'Camiseta deportiva','Camiseta de la colección deportiva',25.0,'/uploads/gymshark_camisa.png',true),"
                + "(2,2,'Guantes de entrenamiento','Guantes para tu rutina de entrenamiento',15.0,'/uploads/guantes_gym.png',true)");
        jdbc.update("INSERT INTO color (id_color,nombre_color,activo) VALUES (1,'Negro',true),(2,'Azul',true)");
        jdbc.update("INSERT INTO talla (id_talla,nombre_talla,activo) VALUES (1,'M',true),(2,'L',true)");
        jdbc.update("INSERT INTO producto_color_talla (id_producto_color_talla,id_producto,id_color,id_talla,existencias,activo) VALUES "
                + "(1,1,1,1,5,true),(2,1,2,2,0,true),(3,2,1,1,3,true)");
    }

    private byte[] createImageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, baos)).isTrue();
        return baos.toByteArray();
    }

    private List<Path> filesInUploadRoot() throws Exception {
        try (var stream = Files.list(uploadRoot)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void adminCanCreateProductWithValidPngAndServePubliclyWithSecurityHeaders() throws Exception {
        byte[] png = createImageBytes("png", 32, 32);

        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "foto-original.png", "image/png", png))
                .param("descripcion", "Botella Media PNG")
                .param("detalle", "Botella con imagen subida")
                .param("precio", "22.50")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        String storedUrl = jdbc.queryForObject(
                "SELECT imagen FROM producto WHERE descripcion = ?", String.class, "Botella Media PNG");
        assertThat(storedUrl).startsWith("/media/").endsWith(".png").doesNotContain("foto-original");

        List<Path> storedFiles = filesInUploadRoot();
        assertThat(storedFiles).hasSize(1);
        assertThat("/media/" + storedFiles.getFirst().getFileName()).isEqualTo(storedUrl);

        // Public media fetch includes nosniff + exact content type
        byte[] served = mvc.perform(get(storedUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isNotEmpty();

        // No directory listing or path traversal on /media
        mvc.perform(get("/media")).andExpect(status().isNotFound());
        mvc.perform(get("/media/")).andExpect(status().isNotFound());
        mvc.perform(get("/media/../../etc/passwd")).andExpect(status().is4xxClientError());

        // Public store shows uploaded URL
        String storeHtml = mvc.perform(get("/tienda/listado")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(storeHtml).contains(storedUrl);
    }

    @Test
    void adminCanReplaceImageWithJpegPreserveWithoutFileAndRespectSharedReferences() throws Exception {
        byte[] png = createImageBytes("png", 24, 24);
        byte[] jpg = createImageBytes("jpg", 24, 24);

        // Replace product 1's historical /uploads/gymshark_camisa.png with uploaded PNG
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "v1.png", "image/png", png))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta V1")
                .param("detalle", "Detalle V1")
                .param("precio", "25.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        String v1Url = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class);
        assertThat(v1Url).startsWith("/media/").endsWith(".png");
        assertThat(filesInUploadRoot()).hasSize(1);

        // Edit without file keeps v1Url even if rutaImagen param is forged
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", new byte[0]))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta V1 Editada")
                .param("detalle", "Detalle V1 Editado")
                .param("precio", "27.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .param("rutaImagen", "/media/forged.png")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        assertThat(jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class)).isEqualTo(v1Url);

        // Replace with JPEG -> removes unreferenced v1Url file after DB commit
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "v2.jpeg", "image/jpeg", jpg))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta V2")
                .param("detalle", "Detalle V2")
                .param("precio", "28.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        String v2Url = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class);
        assertThat(v2Url).startsWith("/media/").endsWith(".jpg").isNotEqualTo(v1Url);
        List<Path> afterReplace = filesInUploadRoot();
        assertThat(afterReplace).hasSize(1);
        assertThat("/media/" + afterReplace.getFirst().getFileName()).isEqualTo(v2Url);
        mvc.perform(get(v2Url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        // If another product also references v2Url, replacing product 1 must NOT delete shared file
        jdbc.update("UPDATE producto SET imagen = ? WHERE id_producto = 2", v2Url);
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "v3.png", "image/png", png))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta V3")
                .param("detalle", "Detalle V3")
                .param("precio", "29.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        assertThat(filesInUploadRoot()).hasSize(2);
        mvc.perform(get(v2Url)).andExpect(status().isOk());
    }

    @Test
    void invalidActiveOversizedAndCorruptFilesAreRejectedWithoutTouchingDbOrDisk() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] html = "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        byte[] oversized = new byte[5 * 1024 * 1024 + 10];
        oversized[0] = (byte) 0x89;
        oversized[1] = 0x50;
        oversized[2] = 0x4E;
        oversized[3] = 0x47;

        MockMultipartFile[] badFiles = new MockMultipartFile[]{
                new MockMultipartFile("archivo", "xss.svg", "image/svg+xml", svg),
                new MockMultipartFile("archivo", "page.html", "text/html", html),
                new MockMultipartFile("archivo", "../../evil.png", "image/png", fakePng),
                new MockMultipartFile("archivo", "huge.png", "image/png", oversized)
        };

        String originalImg = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class);
        int originalCount = jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class);

        for (MockMultipartFile bad : badFiles) {
            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(bad)
                    .param("idProducto", "1")
                    .param("descripcion", "No debe cambiar")
                    .param("detalle", "Detalle")
                    .param("precio", "99.00")
                    .param("categoria.idCategoria", "1")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("tipo", "danger"));

            assertThat(jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class))
                    .isEqualTo(originalImg);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class))
                    .isEqualTo(originalCount);
            assertThat(filesInUploadRoot()).isEmpty();
        }
    }

    @Test
    void dbFailureAfterValidUploadCompensatesFileAndPreservesPreviousImage() throws Exception {
        byte[] png = createImageBytes("png", 20, 20);
        // Set initial valid managed image on product 1
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "initial.png", "image/png", png))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta deportiva")
                .param("detalle", "Detalle inicial")
                .param("precio", "25.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(flash().attribute("tipo", "success"));

        String initialMedia = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class);
        assertThat(filesInUploadRoot()).hasSize(1);

        // Attempt edit with a valid PNG AND invalid product price (-10)
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "replacement.png", "image/png", png))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta fallida")
                .param("detalle", "Detalle")
                .param("precio", "-10.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("tipo", "danger"));

        // Previous image in DB and on disk is intact; no orphan replacement file
        assertThat(jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class))
                .isEqualTo(initialMedia);
        List<Path> remaining = filesInUploadRoot();
        assertThat(remaining).hasSize(1);
        assertThat("/media/" + remaining.getFirst().getFileName()).isEqualTo(initialMedia);

        // Attempt creating new product with valid PNG AND negative initial variant stock
        int beforeCount = jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class);
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "newbad.png", "image/png", png))
                .param("descripcion", "Producto fallo stock")
                .param("detalle", "Detalle")
                .param("precio", "30.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .param("color.idColor", "1")
                .param("talla.idTalla", "1")
                .param("existencias", "-3")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "danger"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class)).isEqualTo(beforeCount);
        assertThat(filesInUploadRoot()).hasSize(1);
    }

    @Test
    void imageApiRequiresAdminCsrfAndValidImage() throws Exception {
        byte[] png = createImageBytes("png", 16, 16);

        mvc.perform(multipart("/api/imagenes/subir")
                .file(new MockMultipartFile("archivo", "ok.png", "image/png", png))
                .with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(multipart("/api/imagenes/subir")
                .file(new MockMultipartFile("archivo", "ok.png", "image/png", png))
                .with(user("cliente@fitforge.example").roles("USER"))
                .with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/api/imagenes/subir")
                .file(new MockMultipartFile("archivo", "ok.png", "image/png", png))
                .with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/api/imagenes/subir")
                .file(new MockMultipartFile("archivo", "bad.svg", "image/svg+xml", "<svg/>".getBytes()))
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(filesInUploadRoot()).isEmpty();

        String body = mvc.perform(multipart("/api/imagenes/subir")
                .file(new MockMultipartFile("archivo", "ok.png", "image/png", png))
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("/media/");
        assertThat(filesInUploadRoot()).hasSize(1);
    }

    @Test
    void traversalOriginalFilenameUsesUuidInsideRootAndStartupCleansOnlyTmpPartFiles() throws Exception {
        byte[] png = createImageBytes("png", 16, 16);

        // Even with traversal in originalFilename, valid PNG is stored strictly under uploadRoot with UUID name
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "../../outside.png", "image/png", png))
                .param("descripcion", "Producto Traversal Seguro")
                .param("detalle", "Nombre seguro")
                .param("precio", "20.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        assertThat(filesInUploadRoot()).hasSize(1);
        Path committed = filesInUploadRoot().getFirst();
        assertThat(committed.getFileName().toString()).doesNotContain("outside");

        // Orphan staging .tmp-*.part is cleaned on initialization without touching committed media
        Path orphanTmp = Files.write(uploadRoot.resolve(".tmp-1234-5678.part"), new byte[]{1, 2, 3});
        var freshService = new com.fitforge.service.impl.LocalStorageServiceImpl(uploadRoot.toAbsolutePath().toString());
        freshService.inicializarYLimpiarTemporales();
        assertThat(Files.exists(orphanTmp)).isFalse();
        assertThat(Files.exists(committed)).isTrue();
    }
}
