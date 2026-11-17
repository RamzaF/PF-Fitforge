package com.fitforge.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitforge.dao.PedidoInventarioDao;
import com.fitforge.domain.Producto;
import com.fitforge.service.CategoriaService;
import com.fitforge.service.InventarioService;
import com.fitforge.service.LocalStorageService;
import com.fitforge.service.OrderTestDatabase;
import com.fitforge.service.ProductoService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=3")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class MediaConsistencyIntegrationTest {

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

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoSpyBean
    CategoriaService categoriaService;

    @MockitoSpyBean
    ProductoService productoService;

    @MockitoSpyBean
    LocalStorageService localStorageService;

    @MockitoSpyBean
    PedidoInventarioDao pedidoInventarioDao;

    @MockitoSpyBean
    InventarioService inventarioService;

    @BeforeEach
    void resetDatabaseAndUploads() throws Exception {
        OrderTestDatabase.requireDisposable();
        Mockito.reset(categoriaService, productoService, localStorageService, pedidoInventarioDao, inventarioService);
        cleanUploadRoot();
        restoreBaseline();
    }

    @AfterEach
    void cleanup() throws Exception {
        OrderTestDatabase.requireDisposable();
        Mockito.reset(categoriaService, productoService, localStorageService, pedidoInventarioDao, inventarioService);
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
        jdbc.update("DELETE FROM movimiento_inventario");
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

    private String uploadInitialImageForProduct(long productId, String format) throws Exception {
        byte[] bytes = createImageBytes(format, 24, 24);
        String ext = "jpg".equals(format) ? "jpg" : "png";
        String mime = "jpg".equals(format) ? "image/jpeg" : "image/png";
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "seed." + ext, mime, bytes))
                .param("idProducto", String.valueOf(productId))
                .param("descripcion", "Producto Inicial " + productId)
                .param("detalle", "Detalle inicial " + productId)
                .param("precio", "25.00")
                .param("categoria.idCategoria", String.valueOf(productId == 2 ? 2 : 1))
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        String mediaUrl = jdbc.queryForObject(
                "SELECT imagen FROM producto WHERE id_producto = ?", String.class, productId);
        assertThat(mediaUrl).startsWith("/media/");
        return mediaUrl;
    }

    @Test
    void postCommitRefCountQueryFailureNeverDeletesCommittedMediaOrClaimsRollback() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 28, 28);

        // Simulate real DB failure AFTER transactional save commits
        doThrow(new DataAccessResourceFailureException("INTERNAL_SQL_STATE_42S02"))
                .when(productoService).contarProductosPorImagen(imageA);

        MvcResult result = mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "replacement.jpg", "image/jpeg", jpegB))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta PostCommit Query Fail")
                .param("detalle", "Detalle actualizado")
                .param("precio", "31.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andReturn();

        String imageB = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
        assertThat(imageB).startsWith("/media/").endsWith(".jpg").isNotEqualTo(imageA);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM producto WHERE id_producto = 1", String.class))
                .isEqualTo("Camiseta PostCommit Query Fail");

        // Committed image B MUST be preserved and servable via GET
        mvc.perform(get(imageB))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        // Previous image A was not verified as unreferenced, so it remains safely on disk
        mvc.perform(get(imageA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));

        Object tipo = result.getFlashMap().get("tipo");
        String mensaje = String.valueOf(result.getFlashMap().get("mensaje"));
        assertThat(tipo).isNotEqualTo("danger");
        assertThat(mensaje).doesNotContain("INTERNAL_SQL_STATE_42S02");
        assertThat(mensaje.toLowerCase()).doesNotContain("error al guardar", "rollback");
    }

    @Test
    void postCommitOldFileCleanupFailureNeverDeletesCommittedMedia() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 28, 28);

        // Simulate runtime failure during post-commit cleanup of old image A
        doThrow(new RuntimeException("INTERNAL_FS_IO_DETAIL"))
                .when(localStorageService).eliminarMediaGestionada(imageA);

        MvcResult result = mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "replacement.jpg", "image/jpeg", jpegB))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta Cleanup Fail")
                .param("detalle", "Detalle actualizado")
                .param("precio", "32.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andReturn();

        String imageB = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
        assertThat(imageB).startsWith("/media/").endsWith(".jpg").isNotEqualTo(imageA);

        mvc.perform(get(imageB))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));

        Object tipo = result.getFlashMap().get("tipo");
        String mensaje = String.valueOf(result.getFlashMap().get("mensaje"));
        assertThat(tipo).isNotEqualTo("danger");
        assertThat(mensaje).doesNotContain("INTERNAL_FS_IO_DETAIL");
        assertThat(mensaje.toLowerCase()).doesNotContain("error al guardar", "rollback");
    }

    @Test
    void preCommitSqlFailureRollsBackAndCleansNewFileWithoutLeakingInternalMessage() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 24, 24);

        // Trigger a real MySQL constraint / SQL error inside the transaction (NOT negative price validation)
        // categoria.idCategoria = 999999 passes non-null check and hits service lookup, or we inject DataAccessException during lock
        doThrow(new DataAccessResourceFailureException("INTERNAL_LOCK_ERR_SECRET_TABLE"))
                .when(pedidoInventarioDao).bloquearProducto(1L);

        MvcResult result = mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "fail-precommit.jpg", "image/jpeg", jpegB))
                .param("idProducto", "1")
                .param("descripcion", "No debe persistir")
                .param("detalle", "Detalle")
                .param("precio", "40.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("tipo", "danger"))
                .andReturn();

        assertThat(jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class))
                .isEqualTo(imageA);
        assertThat(filesInUploadRoot()).hasSize(1);
        mvc.perform(get(imageA)).andExpect(status().isOk());
        String mensaje = String.valueOf(result.getFlashMap().get("mensaje"));
        assertThat(mensaje).doesNotContain("INTERNAL_LOCK_ERR_SECRET_TABLE");
    }

    @Test
    void uncertainCommitOutcomeNeverDeletesReferencedOrAmbiguousMedia() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 24, 24);

        // Simulate commit-phase heuristic/system failure where DB actually committed B in an independent tx
        TransactionTemplate requiresNew = new TransactionTemplate(txManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(inv -> {
            Producto p = inv.getArgument(0);
            String newImg = p.getRutaImagen();
            requiresNew.executeWithoutResult(status ->
                    jdbc.update("UPDATE producto SET imagen = ? WHERE id_producto = ?", newImg, 1L));
            throw new TransactionSystemException("INTERNAL_COMMIT_HEURISTIC_ERR");
        }).when(productoService).actualizarProductoConMedia(any(Producto.class), anyBoolean());

        MvcResult result = mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "ambiguous.jpg", "image/jpeg", jpegB))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta Ambigua")
                .param("detalle", "Detalle")
                .param("precio", "40.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String stored = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
        assertThat(stored).startsWith("/media/").endsWith(".jpg").isNotEqualTo(imageA);
        mvc.perform(get(stored)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));
        String mensaje = String.valueOf(result.getFlashMap().get("mensaje"));
        assertThat(mensaje).doesNotContain("INTERNAL_COMMIT_HEURISTIC_ERR");
        assertThat(mensaje.toLowerCase()).doesNotContain("rollback");
    }

    @Test
    void uncertainCommitOutcomeWithoutConfirmedRollbackPreservesUnverifiedFileAsSafeOrphan() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 24, 24);

        // Simulate commit outcome unknown without confirmed rollback even when DB row stayed A
        doThrow(new TransactionSystemException("INTERNAL_UNKNOWN_COMMIT_STATE"))
                .when(productoService).actualizarProductoConMedia(any(Producto.class), anyBoolean());

        MvcResult result = mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", "uncertain-orphan.jpg", "image/jpeg", jpegB))
                .param("idProducto", "1")
                .param("descripcion", "Camiseta Incertidumbre")
                .param("detalle", "Detalle")
                .param("precio", "40.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class))
                .isEqualTo(imageA);
        // Both A and potentially-committed B remain safely on disk (safe orphan, never deleted blindly)
        assertThat(filesInUploadRoot()).hasSize(2);
        String mensaje = String.valueOf(result.getFlashMap().get("mensaje"));
        assertThat(mensaje).doesNotContain("INTERNAL_UNKNOWN_COMMIT_STATE");
        assertThat(mensaje.toLowerCase()).doesNotContain("rollback");
    }

    @Test
    void concurrentEditWithoutFilePausedBeforeLockDoesNotRestoreDeletedImageReplacedConcurrently() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 30, 30);

        CountDownLatch eBeforeLock = new CountDownLatch(1);
        CountDownLatch rCompleted = new CountDownLatch(1);
        AtomicReference<Thread> editThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            if (Thread.currentThread().equals(editThreadRef.get())) {
                eBeforeLock.countDown();
                assertThat(rCompleted.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(pedidoInventarioDao).bloquearProducto(1L);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> editFuture = pool.submit(() -> {
                editThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", new byte[0]))
                        .param("idProducto", "1")
                        .param("descripcion", "Camiseta Editada Sin Archivo")
                        .param("detalle", "Detalle editado sin archivo")
                        .param("precio", "33.00")
                        .param("categoria.idCategoria", "1")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/listado"))
                        .andExpect(flash().attribute("tipo", "success"))
                        .andReturn();
            });

            assertThat(eBeforeLock.await(10, TimeUnit.SECONDS)).isTrue();

            // Concurrent replacement R runs completely while E is paused before lock
            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(new MockMultipartFile("archivo", "replace.jpg", "image/jpeg", jpegB))
                    .param("idProducto", "1")
                    .param("descripcion", "Camiseta Reemplazo B")
                    .param("detalle", "Detalle reemplazo")
                    .param("precio", "29.00")
                    .param("categoria.idCategoria", "1")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(redirectedUrl("/admin/productos/listado"))
                    .andExpect(flash().attribute("tipo", "success"));

            String imageB = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(imageB).startsWith("/media/").endsWith(".jpg").isNotEqualTo(imageA);
            mvc.perform(get(imageA)).andExpect(status().isNotFound());

            rCompleted.countDown();
            editFuture.get(15, TimeUnit.SECONDS);

            // Final DB state MUST still reference B (never deleted A!), and GET B MUST return 200 OK
            String finalImg = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(finalImg).isEqualTo(imageB);
            assertThat(jdbc.queryForObject("SELECT descripcion FROM producto WHERE id_producto = 1", String.class))
                    .isEqualTo("Camiseta Editada Sin Archivo");
            mvc.perform(get(imageB))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/jpeg"));
            assertThat(filesInUploadRoot()).hasSize(1);
        } finally {
            rCompleted.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentSharedReferenceEditWithoutFileAndReplacePreservesSharedFile() throws Exception {
        String sharedA = uploadInitialImageForProduct(1L, "png");
        jdbc.update("UPDATE producto SET imagen = ? WHERE id_producto = 2", sharedA);
        byte[] jpegB = createImageBytes("jpg", 26, 26);

        CountDownLatch e2BeforeLock = new CountDownLatch(1);
        CountDownLatch r1Completed = new CountDownLatch(1);
        AtomicReference<Thread> edit2ThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            if (Thread.currentThread().equals(edit2ThreadRef.get())) {
                e2BeforeLock.countDown();
                assertThat(r1Completed.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(pedidoInventarioDao).bloquearProducto(2L);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> edit2Future = pool.submit(() -> {
                edit2ThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", new byte[0]))
                        .param("idProducto", "2")
                        .param("descripcion", "Guantes Editados Compartidos")
                        .param("detalle", "Detalle guantes")
                        .param("precio", "18.00")
                        .param("categoria.idCategoria", "2")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/listado"))
                        .andExpect(flash().attribute("tipo", "success"))
                        .andReturn();
            });

            assertThat(e2BeforeLock.await(10, TimeUnit.SECONDS)).isTrue();

            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(new MockMultipartFile("archivo", "p1.jpg", "image/jpeg", jpegB))
                    .param("idProducto", "1")
                    .param("descripcion", "Camiseta Nueva B")
                    .param("detalle", "Detalle")
                    .param("precio", "27.00")
                    .param("categoria.idCategoria", "1")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(redirectedUrl("/admin/productos/listado"))
                    .andExpect(flash().attribute("tipo", "success"));

            r1Completed.countDown();
            edit2Future.get(15, TimeUnit.SECONDS);

            String img1 = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            String img2 = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 2", String.class);
            assertThat(img1).startsWith("/media/").endsWith(".jpg").isNotEqualTo(sharedA);
            assertThat(img2).isEqualTo(sharedA);
            assertThat(filesInUploadRoot()).hasSize(2);
            mvc.perform(get(img1)).andExpect(status().isOk());
            mvc.perform(get(sharedA)).andExpect(status().isOk());
        } finally {
            r1Completed.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentSharedReferenceBothReplacedCleansOldFileOnceAndPreservesBothNewFiles() throws Exception {
        String sharedA = uploadInitialImageForProduct(1L, "png");
        jdbc.update("UPDATE producto SET imagen = ? WHERE id_producto = 2", sharedA);
        byte[] jpegB = createImageBytes("jpg", 26, 26);
        byte[] pngC = createImageBytes("png", 30, 30);

        CountDownLatch r1BeforeRefCheck = new CountDownLatch(1);
        CountDownLatch r2Completed = new CountDownLatch(1);
        AtomicReference<Thread> r1ThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            if (Thread.currentThread().equals(r1ThreadRef.get())) {
                r1BeforeRefCheck.countDown();
                assertThat(r2Completed.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(productoService).contarProductosPorImagen(sharedA);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> r1Future = pool.submit(() -> {
                r1ThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", "b.jpg", "image/jpeg", jpegB))
                        .param("idProducto", "1")
                        .param("descripcion", "Producto 1 B")
                        .param("detalle", "Detalle 1")
                        .param("precio", "27.00")
                        .param("categoria.idCategoria", "1")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/listado"))
                        .andExpect(flash().attribute("tipo", "success"))
                        .andReturn();
            });

            assertThat(r1BeforeRefCheck.await(10, TimeUnit.SECONDS)).isTrue();

            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(new MockMultipartFile("archivo", "c.png", "image/png", pngC))
                    .param("idProducto", "2")
                    .param("descripcion", "Producto 2 C")
                    .param("detalle", "Detalle 2")
                    .param("precio", "19.00")
                    .param("categoria.idCategoria", "2")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(redirectedUrl("/admin/productos/listado"))
                    .andExpect(flash().attribute("tipo", "success"));

            r2Completed.countDown();
            r1Future.get(15, TimeUnit.SECONDS);

            String img1 = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            String img2 = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 2", String.class);
            assertThat(img1).startsWith("/media/").endsWith(".jpg");
            assertThat(img2).startsWith("/media/").endsWith(".png").isNotEqualTo(sharedA);
            assertThat(filesInUploadRoot()).hasSize(2);
            mvc.perform(get(sharedA)).andExpect(status().isNotFound());
            mvc.perform(get(img1)).andExpect(status().isOk());
            mvc.perform(get(img2)).andExpect(status().isOk());
        } finally {
            r2Completed.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentDoubleReplacementReadsCurrentLockedPreviousImageAndLeavesNoOrphans() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 24, 24);
        byte[] pngC = createImageBytes("png", 32, 32);

        CountDownLatch r2BeforeLock = new CountDownLatch(1);
        CountDownLatch r1Completed = new CountDownLatch(1);
        AtomicReference<Thread> r2ThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            if (Thread.currentThread().equals(r2ThreadRef.get())) {
                r2BeforeLock.countDown();
                assertThat(r1Completed.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(pedidoInventarioDao).bloquearProducto(1L);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> r2Future = pool.submit(() -> {
                r2ThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", "c.png", "image/png", pngC))
                        .param("idProducto", "1")
                        .param("descripcion", "Producto Final C")
                        .param("detalle", "Detalle C")
                        .param("precio", "35.00")
                        .param("categoria.idCategoria", "1")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/listado"))
                        .andExpect(flash().attribute("tipo", "success"))
                        .andReturn();
            });

            assertThat(r2BeforeLock.await(10, TimeUnit.SECONDS)).isTrue();

            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(new MockMultipartFile("archivo", "b.jpg", "image/jpeg", jpegB))
                    .param("idProducto", "1")
                    .param("descripcion", "Producto Intermedio B")
                    .param("detalle", "Detalle B")
                    .param("precio", "30.00")
                    .param("categoria.idCategoria", "1")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(redirectedUrl("/admin/productos/listado"))
                    .andExpect(flash().attribute("tipo", "success"));

            String imageB = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(imageB).startsWith("/media/").endsWith(".jpg").isNotEqualTo(imageA);

            r1Completed.countDown();
            r2Future.get(15, TimeUnit.SECONDS);

            String imageC = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(imageC).startsWith("/media/").endsWith(".png").isNotEqualTo(imageB);
            assertThat(filesInUploadRoot()).hasSize(1);
            mvc.perform(get(imageA)).andExpect(status().isNotFound());
            mvc.perform(get(imageB)).andExpect(status().isNotFound());
            mvc.perform(get(imageC)).andExpect(status().isOk());
        } finally {
            r1Completed.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void internalInventoryExceptionsNeverLeakRawExceptionMessagesToAdminUi() throws Exception {
        doThrow(new DataAccessResourceFailureException("INTERNAL_DB_SECRET_CONSTRAINT"))
                .when(inventarioService).registrarMovimiento(any(), any(), any(), Mockito.anyInt(), any());

        MvcResult r1 = mvc.perform(post("/admin/inventario/movimiento")
                .param("idVariante", "1")
                .param("idProducto", "1")
                .param("tipo", "AJUSTE")
                .param("cantidad", "4")
                .param("motivo", "Conteo seguro")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/inventario/1"))
                .andExpect(flash().attribute("tipo", "danger"))
                .andReturn();
        assertThat(String.valueOf(r1.getFlashMap().get("mensaje")))
                .doesNotContain("INTERNAL_DB_SECRET_CONSTRAINT");

        MvcResult r2 = mvc.perform(post("/admin/productos/inventario/guardar")
                .param("idProductoColorTalla", "1")
                .param("producto.idProducto", "1")
                .param("existencias", "4")
                .param("motivo", "Conteo seguro")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/inventario/1"))
                .andExpect(flash().attribute("tipo", "danger"))
                .andReturn();
        assertThat(String.valueOf(r2.getFlashMap().get("error")))
                .doesNotContain("INTERNAL_DB_SECRET_CONSTRAINT");
    }

    @Test
    void categoryActivationBeforeValidationMustNotRestoreDeletedImage() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 30, 30);

        jdbc.update("UPDATE categoria SET activo=false WHERE id_categoria=2");
        CountDownLatch beforeCategoryLookup = new CountDownLatch(1);
        CountDownLatch categoryActivated = new CountDownLatch(1);

        CountDownLatch eBeforeLock = new CountDownLatch(1);
        CountDownLatch rCompleted = new CountDownLatch(1);
        AtomicReference<Thread> editThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (Thread.currentThread().equals(editThreadRef.get())) {
                beforeCategoryLookup.countDown();
                assertThat(categoryActivated.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(categoriaService).getCategoria(true);

        doAnswer(inv -> {
            if (Thread.currentThread().equals(editThreadRef.get())) {
                eBeforeLock.countDown();
                assertThat(rCompleted.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(pedidoInventarioDao).bloquearProducto(1L);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> editFuture = pool.submit(() -> {
                editThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", new byte[0]))
                        .param("idProducto", "1")
                        .param("descripcion", "Camiseta Editada Sin Archivo")
                        .param("detalle", "Detalle editado sin archivo")
                        .param("precio", "33.00")
                        .param("categoria.idCategoria", "2")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/listado"))
                        .andExpect(flash().attribute("tipo", "success"))
                        .andReturn();
            });

            assertThat(beforeCategoryLookup.await(10, TimeUnit.SECONDS)).isTrue();
            mvc.perform(post("/admin/categorias/estado")
                    .param("idCategoria", "2").param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("tipo", "success"));
            categoryActivated.countDown();
            assertThat(eBeforeLock.await(10, TimeUnit.SECONDS)).isTrue();

            // Concurrent replacement R runs completely while E is paused before lock
            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(new MockMultipartFile("archivo", "replace.jpg", "image/jpeg", jpegB))
                    .param("idProducto", "1")
                    .param("descripcion", "Camiseta Reemplazo B")
                    .param("detalle", "Detalle reemplazo")
                    .param("precio", "29.00")
                    .param("categoria.idCategoria", "1")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(redirectedUrl("/admin/productos/listado"))
                    .andExpect(flash().attribute("tipo", "success"));

            String imageB = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(imageB).startsWith("/media/").endsWith(".jpg").isNotEqualTo(imageA);
            mvc.perform(get(imageA)).andExpect(status().isNotFound());

            rCompleted.countDown();
            editFuture.get(15, TimeUnit.SECONDS);

            // Final DB state MUST still reference B (never deleted A!), and GET B MUST return 200 OK
            String finalImg = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(finalImg).isEqualTo(imageB);
            assertThat(jdbc.queryForObject("SELECT descripcion FROM producto WHERE id_producto = 1", String.class))
                    .isEqualTo("Camiseta Editada Sin Archivo");
            mvc.perform(get(imageB))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "image/jpeg"));
            assertThat(filesInUploadRoot()).hasSize(1);
        } finally {
            categoryActivated.countDown();
            rCompleted.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void categoryDeactivationAfterNavigationLookupRejectsMoveAndCleansStagedMedia() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 28, 28);

        CountDownLatch afterNavLookup = new CountDownLatch(1);
        CountDownLatch categoryDeactivated = new CountDownLatch(1);
        AtomicReference<Thread> editThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (Thread.currentThread().equals(editThreadRef.get())) {
                afterNavLookup.countDown();
                assertThat(categoryDeactivated.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(categoriaService).getCategoria(true);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> editFuture = pool.submit(() -> {
                editThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", "staged.jpg", "image/jpeg", jpegB))
                        .param("idProducto", "1")
                        .param("descripcion", "Camiseta Movida a Inactiva")
                        .param("detalle", "No debe persistir")
                        .param("precio", "33.00")
                        .param("categoria.idCategoria", "2")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/editar/1"))
                        .andExpect(flash().attribute("tipo", "danger"))
                        .andReturn();
            });

            assertThat(afterNavLookup.await(10, TimeUnit.SECONDS)).isTrue();
            mvc.perform(post("/admin/categorias/estado")
                    .param("idCategoria", "2").param("activo", "false")
                    .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("tipo", "success"));
            categoryDeactivated.countDown();

            MvcResult editResult = editFuture.get(15, TimeUnit.SECONDS);
            assertThat(String.valueOf(editResult.getFlashMap().get("mensaje")))
                    .contains("categoría inactiva");
            assertThat(jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class))
                    .isEqualTo(imageA);
            assertThat(jdbc.queryForObject("SELECT id_categoria FROM producto WHERE id_producto = 1", Long.class))
                    .isEqualTo(1L);
            assertThat(filesInUploadRoot()).hasSize(1);
            mvc.perform(get(imageA)).andExpect(status().isOk());
        } finally {
            categoryDeactivated.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void categoryActivationBeforeDoubleReplacementReadsCurrentPreviousImageAndCleansIntermediate() throws Exception {
        String imageA = uploadInitialImageForProduct(1L, "png");
        byte[] jpegB = createImageBytes("jpg", 24, 24);
        byte[] pngC = createImageBytes("png", 32, 32);

        jdbc.update("UPDATE categoria SET activo=false WHERE id_categoria=2");
        CountDownLatch beforeCategoryLookup = new CountDownLatch(1);
        CountDownLatch categoryActivated = new CountDownLatch(1);
        CountDownLatch r2BeforeLock = new CountDownLatch(1);
        CountDownLatch r1Completed = new CountDownLatch(1);
        AtomicReference<Thread> r2ThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (Thread.currentThread().equals(r2ThreadRef.get())) {
                beforeCategoryLookup.countDown();
                assertThat(categoryActivated.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(categoriaService).getCategoria(true);

        doAnswer(inv -> {
            if (Thread.currentThread().equals(r2ThreadRef.get())) {
                r2BeforeLock.countDown();
                assertThat(r1Completed.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(pedidoInventarioDao).bloquearProducto(1L);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> r2Future = pool.submit(() -> {
                r2ThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", "c.png", "image/png", pngC))
                        .param("idProducto", "1")
                        .param("descripcion", "Producto Final C")
                        .param("detalle", "Detalle C")
                        .param("precio", "35.00")
                        .param("categoria.idCategoria", "2")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/listado"))
                        .andExpect(flash().attribute("tipo", "success"))
                        .andReturn();
            });

            assertThat(beforeCategoryLookup.await(10, TimeUnit.SECONDS)).isTrue();
            mvc.perform(post("/admin/categorias/estado")
                    .param("idCategoria", "2").param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("tipo", "success"));
            categoryActivated.countDown();
            assertThat(r2BeforeLock.await(10, TimeUnit.SECONDS)).isTrue();

            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(new MockMultipartFile("archivo", "b.jpg", "image/jpeg", jpegB))
                    .param("idProducto", "1")
                    .param("descripcion", "Producto Intermedio B")
                    .param("detalle", "Detalle B")
                    .param("precio", "30.00")
                    .param("categoria.idCategoria", "1")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(redirectedUrl("/admin/productos/listado"))
                    .andExpect(flash().attribute("tipo", "success"));

            String imageB = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(imageB).startsWith("/media/").endsWith(".jpg").isNotEqualTo(imageA);
            mvc.perform(get(imageA)).andExpect(status().isNotFound());

            r1Completed.countDown();
            r2Future.get(15, TimeUnit.SECONDS);

            String imageC = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            assertThat(imageC).startsWith("/media/").endsWith(".png").isNotEqualTo(imageB);
            assertThat(filesInUploadRoot()).hasSize(1);
            mvc.perform(get(imageA)).andExpect(status().isNotFound());
            mvc.perform(get(imageB)).andExpect(status().isNotFound());
            mvc.perform(get(imageC)).andExpect(status().isOk());
        } finally {
            categoryActivated.countDown();
            r1Completed.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void categoryActivationBeforeSharedReferenceEditWithoutFilePreservesSharedFile() throws Exception {
        String sharedA = uploadInitialImageForProduct(1L, "png");
        jdbc.update("UPDATE producto SET imagen = ? WHERE id_producto = 2", sharedA);
        jdbc.update("UPDATE categoria SET activo=false WHERE id_categoria=2");
        byte[] jpegB = createImageBytes("jpg", 26, 26);

        CountDownLatch beforeCategoryLookup = new CountDownLatch(1);
        CountDownLatch categoryActivated = new CountDownLatch(1);
        CountDownLatch e2BeforeLock = new CountDownLatch(1);
        CountDownLatch r1Completed = new CountDownLatch(1);
        AtomicReference<Thread> e2ThreadRef = new AtomicReference<>();

        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            if (Thread.currentThread().equals(e2ThreadRef.get())) {
                beforeCategoryLookup.countDown();
                assertThat(categoryActivated.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(categoriaService).getCategoria(true);

        doAnswer(inv -> {
            if (Thread.currentThread().equals(e2ThreadRef.get())) {
                e2BeforeLock.countDown();
                assertThat(r1Completed.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return inv.callRealMethod();
        }).when(pedidoInventarioDao).bloquearProducto(2L);

        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> e2Future = pool.submit(() -> {
                e2ThreadRef.set(Thread.currentThread());
                return mvc.perform(multipart("/admin/productos/guardar-producto")
                        .file(new MockMultipartFile("archivo", new byte[0]))
                        .param("idProducto", "2")
                        .param("descripcion", "Guantes Compartidos Cat2")
                        .param("detalle", "Detalle guantes")
                        .param("precio", "18.00")
                        .param("categoria.idCategoria", "2")
                        .param("activo", "true")
                        .with(user("admin@fitforge.example").roles("ADMIN"))
                        .with(csrf()))
                        .andExpect(redirectedUrl("/admin/productos/listado"))
                        .andExpect(flash().attribute("tipo", "success"))
                        .andReturn();
            });

            assertThat(beforeCategoryLookup.await(10, TimeUnit.SECONDS)).isTrue();
            mvc.perform(post("/admin/categorias/estado")
                    .param("idCategoria", "2").param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("tipo", "success"));
            categoryActivated.countDown();
            assertThat(e2BeforeLock.await(10, TimeUnit.SECONDS)).isTrue();

            mvc.perform(multipart("/admin/productos/guardar-producto")
                    .file(new MockMultipartFile("archivo", "p1.jpg", "image/jpeg", jpegB))
                    .param("idProducto", "1")
                    .param("descripcion", "Camiseta Nueva B")
                    .param("detalle", "Detalle")
                    .param("precio", "27.00")
                    .param("categoria.idCategoria", "1")
                    .param("activo", "true")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(redirectedUrl("/admin/productos/listado"))
                    .andExpect(flash().attribute("tipo", "success"));

            r1Completed.countDown();
            e2Future.get(15, TimeUnit.SECONDS);

            String img1 = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 1", String.class);
            String img2 = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto = 2", String.class);
            assertThat(img1).startsWith("/media/").endsWith(".jpg").isNotEqualTo(sharedA);
            assertThat(img2).isEqualTo(sharedA);
            assertThat(filesInUploadRoot()).hasSize(2);
            mvc.perform(get(img1)).andExpect(status().isOk());
            mvc.perform(get(sharedA)).andExpect(status().isOk());
        } finally {
            categoryActivated.countDown();
            r1Completed.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
