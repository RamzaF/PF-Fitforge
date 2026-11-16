package com.fitforge.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitforge.service.OrderTestDatabase;
import com.fitforge.service.PedidoException;
import com.fitforge.service.PedidoService;
import com.fitforge.service.ProductoService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class InventoryAuditIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PedidoService orders;

    @Autowired
    ProductoService products;

    @BeforeEach
    void resetDatabase() {
        OrderTestDatabase.requireDisposable();
        restoreBaseline();
    }

    @AfterEach
    void cleanup() {
        OrderTestDatabase.requireDisposable();
        SecurityContextHolder.clearContext();
        restoreBaseline();
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
                + "(1,1,1,1,5,true),(2,1,2,2,0,false),(3,2,1,1,3,true)");
    }

    private <T> T as(String email, String role, Supplier<T> action) {
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                email, "unused", List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(ctx);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private List<Object> race(Callable<Object> a, Callable<Object> b) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (var task : List.of(a, b)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void inventoryPagesAndMutationsRequireAdminAndCsrf() throws Exception {
        mvc.perform(get("/admin/inventario")).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/admin/productos/inventario/1")).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/admin/inventario").with(user("cliente@fitforge.example").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/productos/inventario/1").with(user("cliente@fitforge.example").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/inventario/movimiento")
                .param("idVariante", "1").param("tipo", "INGRESO").param("cantidad", "2").param("motivo", "Reposición")
                .with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/inventario/movimiento")
                .param("idVariante", "1").param("tipo", "INGRESO").param("cantidad", "2").param("motivo", "Reposición")
                .with(user("cliente@fitforge.example").roles("USER"))
                .with(csrf()))
                .andExpect(status().isForbidden());

        String generalHtml = mvc.perform(get("/admin/inventario").with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(generalHtml).contains("Camiseta deportiva", "Guantes de entrenamiento");

        String productHtml = mvc.perform(get("/admin/productos/inventario/1").with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(productHtml).contains("Camiseta deportiva", "Negro", "M");
    }

    @Test
    void adminCanAddAndAdjustStockWithAuditTrailAndPublicCatalogReflectsStock() throws Exception {
        // Ingreso +7 (5 -> 12)
        mvc.perform(post("/admin/inventario/movimiento")
                .param("idVariante", "1")
                .param("idProducto", "1")
                .param("tipo", "INGRESO")
                .param("cantidad", "7")
                .param("motivo", "Reposición semanal")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/inventario/1"))
                .andExpect(flash().attribute("tipo", "success"));

        assertThat(jdbc.queryForObject(
                "SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla = 1", Integer.class))
                .isEqualTo(12);

        // Ajuste fijo a 4 (12 -> 4, delta -8)
        mvc.perform(post("/admin/inventario/movimiento")
                .param("idVariante", "1")
                .param("idProducto", "1")
                .param("tipo", "AJUSTE")
                .param("cantidad", "4")
                .param("motivo", "Conteo físico")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/inventario/1"))
                .andExpect(flash().attribute("tipo", "success"));

        assertThat(jdbc.queryForObject(
                "SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla = 1", Integer.class))
                .isEqualTo(4);

        var movements = jdbc.queryForList(
                "SELECT tipo, cantidad_anterior, cantidad_delta, cantidad_nueva, motivo, id_usuario_admin "
                + "FROM movimiento_inventario WHERE id_producto_color_talla = 1 ORDER BY id_movimiento ASC");
        assertThat(movements).hasSize(2);
        assertThat(movements.get(0)).containsEntry("tipo", "INGRESO")
                .containsEntry("cantidad_anterior", 5)
                .containsEntry("cantidad_delta", 7)
                .containsEntry("cantidad_nueva", 12)
                .containsEntry("motivo", "Reposición semanal")
                .containsEntry("id_usuario_admin", 1);
        assertThat(movements.get(1)).containsEntry("tipo", "AJUSTE")
                .containsEntry("cantidad_anterior", 12)
                .containsEntry("cantidad_delta", -8)
                .containsEntry("cantidad_nueva", 4)
                .containsEntry("motivo", "Conteo físico")
                .containsEntry("id_usuario_admin", 1);

        String productPublicHtml = mvc.perform(get("/productos/1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(productPublicHtml).contains("Stock: 4");

        String inventoryHtml = mvc.perform(get("/admin/productos/inventario/1")
                .with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(inventoryHtml).contains("Reposición semanal", "Conteo físico");
    }

    @Test
    void invalidQuantitiesOverflowBlankMotivoInactiveAndMissingVariantsAreRejected() throws Exception {
        String[][] invalidCases = new String[][]{
                {"1", "INGRESO", "0", "Motivo válido"},
                {"1", "INGRESO", "-3", "Motivo válido"},
                {"1", "AJUSTE", "-1", "Motivo válido"},
                {"1", "INGRESO", "1.5", "Motivo válido"},
                {"1", "INGRESO", "abc", "Motivo válido"},
                {"1", "INGRESO", "2147483647", "Overflow int"},
                {"1", "INGRESO", "2", "   "},
                {"999999", "INGRESO", "2", "No existe"},
                {"2", "INGRESO", "2", "Variante inactiva"}
        };

        for (String[] tc : invalidCases) {
            mvc.perform(post("/admin/inventario/movimiento")
                    .param("idVariante", tc[0])
                    .param("tipo", tc[1])
                    .param("cantidad", tc[2])
                    .param("motivo", tc[3])
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("tipo", "danger"));
        }

        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=2", Integer.class))
                .isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_inventario", Integer.class)).isZero();
    }

    @Test
    void concurrentIngresoAndOrderPreserveExactStockAndAuditTrail() throws Exception {
        jdbc.update("UPDATE producto_color_talla SET existencias = 3 WHERE id_producto_color_talla = 1");

        var orderCmd = new PedidoService.Comando(
                UUID.randomUUID(), List.of(new PedidoService.Linea(1L, 2, new BigDecimal("25.00"))));

        var results = race(
                () -> as("cliente@fitforge.example", "ROLE_USER", () -> orders.confirmar(orderCmd)),
                () -> {
                    mvc.perform(post("/admin/inventario/movimiento")
                            .param("idVariante", "1")
                            .param("tipo", "INGRESO")
                            .param("cantidad", "5")
                            .param("motivo", "Ingreso concurrente")
                            .with(user("admin@fitforge.example").roles("ADMIN"))
                            .with(csrf()))
                            .andExpect(status().is3xxRedirection())
                            .andExpect(flash().attribute("tipo", "success"));
                    return "ingressed";
                });

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1", Integer.class))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM venta WHERE id_producto_color_talla=1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cantidad_delta FROM movimiento_inventario WHERE id_producto_color_talla=1", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void concurrentAjusteAndOrderSerializeWithoutNegativeStock() throws Exception {
        jdbc.update("UPDATE producto_color_talla SET existencias = 3 WHERE id_producto_color_talla = 1");

        var orderCmd = new PedidoService.Comando(
                UUID.randomUUID(), List.of(new PedidoService.Linea(1L, 2, new BigDecimal("25.00"))));

        var results = race(
                () -> as("cliente@fitforge.example", "ROLE_USER", () -> orders.confirmar(orderCmd)),
                () -> {
                    mvc.perform(post("/admin/inventario/movimiento")
                            .param("idVariante", "1")
                            .param("tipo", "AJUSTE")
                            .param("cantidad", "1")
                            .param("motivo", "Ajuste concurrente")
                            .with(user("admin@fitforge.example").roles("ADMIN"))
                            .with(csrf()))
                            .andExpect(status().is3xxRedirection())
                            .andExpect(flash().attribute("tipo", "success"));
                    return "adjusted";
                });

        int finalStock = jdbc.queryForObject(
                "SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1", Integer.class);
        assertThat(finalStock).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT MIN(existencias) FROM producto_color_talla", Integer.class))
                .isGreaterThanOrEqualTo(0);

        Object orderResult = results.get(0);
        if (orderResult instanceof Exception ex) {
            assertThat(ex).isInstanceOf(PedidoException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura", Integer.class)).isZero();
        } else {
            assertThat(orderResult).isInstanceOf(PedidoService.Confirmacion.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void concurrentProductEditAndIngresoNeverRestoreStock() throws Exception {
        var stale = products.obtenerProductoPorId(1L);
        stale.setDescripcion("Camiseta Editada Concurrente");

        var results = race(
                () -> {
                    products.guardarProducto(stale);
                    return "edited";
                },
                () -> {
                    mvc.perform(post("/admin/inventario/movimiento")
                            .param("idVariante", "1")
                            .param("tipo", "INGRESO")
                            .param("cantidad", "4")
                            .param("motivo", "Ingreso vs edición")
                            .with(user("admin@fitforge.example").roles("ADMIN"))
                            .with(csrf()))
                            .andExpect(status().is3xxRedirection())
                            .andExpect(flash().attribute("tipo", "success"));
                    return "ingressed";
                });

        assertThat(results).noneMatch(r -> r instanceof Exception);
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1", Integer.class))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT descripcion FROM producto WHERE id_producto=1", String.class))
                .isEqualTo("Camiseta Editada Concurrente");
    }

    @Test
    void alternativeInventorySaveEnforcesRoleCsrfMotivoAndProductVariantOwnership() throws Exception {
        // Anonymous and USER cannot mutate via /admin/productos/inventario/guardar; ADMIN needs CSRF
        mvc.perform(post("/admin/productos/inventario/guardar")
                .param("idProductoColorTalla", "1")
                .param("producto.idProducto", "1")
                .param("existencias", "4")
                .param("motivo", "Ajuste válido")
                .with(csrf()))
                .andExpect(redirectedUrl("http://localhost/login"));

        mvc.perform(post("/admin/productos/inventario/guardar")
                .param("idProductoColorTalla", "1")
                .param("producto.idProducto", "1")
                .param("existencias", "4")
                .param("motivo", "Ajuste válido")
                .with(user("cliente@fitforge.example").roles("USER"))
                .with(csrf()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/productos/inventario/guardar")
                .param("idProductoColorTalla", "1")
                .param("producto.idProducto", "1")
                .param("existencias", "4")
                .param("motivo", "Ajuste válido")
                .with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isForbidden());

        // Missing, blank, or too-short motivo on existing variant adjustment MUST be rejected with zero mutations
        for (String badMotivo : new String[]{null, "   ", "ab"}) {
            var req = post("/admin/productos/inventario/guardar")
                    .param("idProductoColorTalla", "1")
                    .param("producto.idProducto", "1")
                    .param("color.idColor", "1")
                    .param("talla.idTalla", "1")
                    .param("existencias", "2")
                    .with(user("admin@fitforge.example").roles("ADMIN"))
                    .with(csrf());
            if (badMotivo != null) {
                req.param("motivo", badMotivo);
            }
            mvc.perform(req)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("tipo", "danger"));
        }

        // Variant 3 belongs to Product 2, NOT Product 1 -> MUST be rejected on both endpoints
        mvc.perform(post("/admin/productos/inventario/guardar")
                .param("idProductoColorTalla", "3")
                .param("producto.idProducto", "1")
                .param("existencias", "9")
                .param("motivo", "Conteo producto incorrecto")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("tipo", "danger"));

        mvc.perform(post("/admin/inventario/movimiento")
                .param("idVariante", "3")
                .param("idProducto", "1")
                .param("tipo", "AJUSTE")
                .param("cantidad", "9")
                .param("motivo", "Conteo producto incorrecto")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("tipo", "danger"));

        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=3", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimiento_inventario", Integer.class)).isZero();

        // Creating a NEW variant (no idProductoColorTalla) via /admin/productos/inventario/guardar still works
        mvc.perform(post("/admin/productos/inventario/guardar")
                .param("producto.idProducto", "1")
                .param("color.idColor", "2")
                .param("talla.idTalla", "1")
                .param("existencias", "6")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/inventario/1"))
                .andExpect(flash().attribute("tipo", "success"));

        assertThat(jdbc.queryForObject(
                "SELECT existencias FROM producto_color_talla WHERE id_producto=1 AND id_color=2 AND id_talla=1",
                Integer.class)).isEqualTo(6);

        // Valid adjustment on matching product + valid operador motivo succeeds and logs exact motivo
        mvc.perform(post("/admin/productos/inventario/guardar")
                .param("idProductoColorTalla", "1")
                .param("producto.idProducto", "1")
                .param("existencias", "4")
                .param("motivo", "Conteo físico autorizado")
                .with(user("admin@fitforge.example").roles("ADMIN"))
                .with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/inventario/1"))
                .andExpect(flash().attribute("tipo", "success"));

        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1", Integer.class))
                .isEqualTo(4);
        String loggedMotivo = jdbc.queryForObject(
                "SELECT motivo FROM movimiento_inventario WHERE id_producto_color_talla=1 ORDER BY id_movimiento DESC LIMIT 1",
                String.class);
        assertThat(loggedMotivo).isEqualTo("Conteo físico autorizado");
    }
}
