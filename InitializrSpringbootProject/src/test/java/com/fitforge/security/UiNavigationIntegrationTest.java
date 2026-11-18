package com.fitforge.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Transactional
class UiNavigationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    String page(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings={"/", "/tienda/listado", "/categoria/Accesorios", "/productos/1", "/login", "/register", "/forgot-password", "/contacto", "/nosotros"})
    void publicPagesRenderOneNavigationAndFilterBundle(String path) throws Exception {
        var html = page(path);
        assertThat(html.split("src=\"/webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js\"", -1)).hasSize(2);
        assertThat(html.split("src=\"/js/rutinas.js\"", -1)).hasSize(2);
        assertThat(html).doesNotContain("/Uploads/favicon.ico", "href=\"/terminos\"", "href=\"/privacidad\"");
    }

    @Test void productsAliasHasFiniteRedirectAndKeepsDetails() throws Exception {
        mvc.perform(get("/productos")).andExpect(redirectedUrl("/tienda/listado"));
        assertThat(page("/tienda/listado")).contains("Camiseta deportiva");
        assertThat(page("/productos/1")).contains("Camiseta deportiva");
        mvc.perform(get("/productos/999999")).andExpect(redirectedUrl("/tienda/listado"));
    }

    @Test void searchAndLoginErrorHaveAccessibleNames() throws Exception {
        assertThat(page("/tienda/listado")).contains("aria-label=\"Buscar productos\"", "aria-label=\"Buscar\"");
        assertThat(page("/login?error")).contains("role=\"alert\"", "Correo o contraseña incorrectos");
    }

    @Test void adminAccountIsVisibleWithoutChangingPermissions() throws Exception {
        var html = mvc.perform(get("/admin/productos/listado").with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("href=\"/perfil\"");
    }

    @Test void inventoryPageRendersProductVariantsAndMissingProductRedirects() throws Exception {
        var html = mvc.perform(get("/admin/productos/inventario/1").with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Camiseta deportiva", "Bitácora");

        var missing = mvc.perform(get("/admin/productos/inventario/999999").with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(redirectedUrl("/admin/productos/listado")).andExpect(flash().attribute("tipo", "danger")).andReturn();
        assertThat(missing.getFlashMap().get("mensaje").toString()).contains("Producto no encontrado");
    }

    @ParameterizedTest
    @ValueSource(strings={"/admin/ropa-deportiva", "/admin/accesorios", "/admin/suplementos"})
    void secondaryNavigationOnlyOffersAvailableActions(String path) throws Exception {
        jdbc.update("UPDATE categoria SET descripcion=\"Ropa Deportiva\" WHERE id_categoria=1");
        jdbc.update("UPDATE categoria SET descripcion=\"Accesorios\" WHERE id_categoria=2");
        var html = mvc.perform(get(path).with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("href=\"/admin/productos/nuevo", "href=\"/admin/productos/eliminar/");
        assertThat(html).contains("/admin/productos/listado");
    }

    @ParameterizedTest
    @ValueSource(strings={"/admin/productos/listado", "/admin/productos/editar/1"})
    void uploadControlsAreEnabledAndFormsKeepCsrf(String path) throws Exception {
        var html = mvc.perform(get(path).with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("name=\"_csrf\"").doesNotContain("Subida de imágenes no disponible");
        var inputs = java.util.regex.Pattern.compile("<input[^>]*type=\"file\"[^>]*>").matcher(html);
        int count = 0;
        while (inputs.find()) { assertThat(inputs.group()).doesNotContain("disabled"); count++; }
        assertThat(count).isGreaterThan(0);
    }

    @ParameterizedTest
    @ValueSource(strings={"", "1"})
    void rejectedInvalidFileNeverCreatesOrChangesProduct(String id) throws Exception {
        var before = jdbc.queryForList("SELECT * FROM producto ORDER BY id_producto");
        var stock = jdbc.queryForList("SELECT * FROM producto_color_talla ORDER BY id_producto_color_talla");
        var response = mvc.perform(multipart("/admin/productos/guardar-producto")
            .file(new MockMultipartFile("archivo", "demo.png", "image/png", new byte[]{1,2,3}))
            .param("idProducto", id).param("descripcion", "No debe guardarse").param("precio", "25")
            .param("categoria.idCategoria", "1").param("activo", "true").param("detalle", "Detalle ficticio")
            .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
            .andReturn();
        assertThat(jdbc.queryForList("SELECT * FROM producto ORDER BY id_producto")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM producto_color_talla ORDER BY id_producto_color_talla")).isEqualTo(stock);
        assertThat(response.getFlashMap().get("tipo")).isEqualTo("danger");
        assertThat(response.getResponse().getStatus()).isEqualTo(302);
    }

    @Test void imageApiRejectsInvalidFileWithBadRequest() throws Exception {
        mvc.perform(multipart("/api/imagenes/subir").file(new MockMultipartFile("archivo", "demo.png", "image/png", new byte[]{1}))
            .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
            .andExpect(status().isBadRequest());
    }

    @Test void emptyFileAllowsEditingAndPreservesImage() throws Exception {
        String image = jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class);
        mvc.perform(multipart("/admin/productos/guardar-producto").file(new MockMultipartFile("archivo", new byte[0]))
            .param("idProducto", "1").param("descripcion", "Edición sin archivo").param("precio", "25")
            .param("categoria.idCategoria", "1").param("activo", "true").param("detalle", "Detalle ficticio").param("rutaImagen", "/forged.png")
            .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
            .andExpect(redirectedUrl("/admin/productos/listado")).andExpect(flash().attribute("tipo", "success"));
        assertThat(jdbc.queryForObject("SELECT descripcion FROM producto WHERE id_producto=1", String.class)).isEqualTo("Edición sin archivo");
        assertThat(jdbc.queryForObject("SELECT imagen FROM producto WHERE id_producto=1", String.class)).isEqualTo(image);
    }

    @Test void uploadRejectionKeepsSecurityAndCsrf() throws Exception {
        for (String path : new String[]{"/api/imagenes/subir", "/admin/productos/guardar-producto"}) {
            mvc.perform(multipart(path).file(new MockMultipartFile("archivo", new byte[]{1})).with(csrf()))
                .andExpect(status().is3xxRedirection());
            mvc.perform(multipart(path).file(new MockMultipartFile("archivo", new byte[]{1})).with(user("cliente@fitforge.example").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
            mvc.perform(multipart(path).file(new MockMultipartFile("archivo", new byte[]{1})).with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isForbidden());
        }
    }

    @Test void catalogAndAdminNavigationUseProgressiveDisclosure() throws Exception {
        var css = page("/css/estilos.css");
        assertThat(css).contains("--ff-primary: #117a42", "--ff-teal: #0e6b78", "prefers-reduced-motion: reduce");

        var homeHtml = page("/");
        assertThat(homeHtml).contains("class=\"skip-link\"", "class=\"demo-banner\"", "Camiseta deportiva")
            .doesNotContain("Camiseta de la colección deportiva");

        var storeHtml = page("/tienda/listado");
        assertThat(storeHtml).contains("class=\"skip-link\"", "class=\"demo-banner\"", "id=\"botonAbrirModal\"",
                "data-testid=\"catalog-product-card\"", "Camiseta deportiva", "Disponible")
            .doesNotContain("Camiseta de la colección deportiva");

        var detailHtml = page("/productos/1");
        assertThat(detailHtml).contains("Camiseta deportiva", "Camiseta de la colección deportiva",
                "id=\"variante\"", "Stock: 5", "data-testid=\"variant-pill\"");
        assertThat(detailHtml.split("data-testid=\"detail-main-image\"", -1)).hasSize(2);

        var clientOrdersHtml = mvc.perform(get("/pedidos").with(user("cliente@fitforge.example").roles("USER")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(clientOrdersHtml).contains("Mis pedidos").doesNotContain("class=\"admin-shell-bar\"");

        for (String adminPath : new String[]{
                "/admin",
                "/admin/productos/listado",
                "/admin/productos/editar/1",
                "/admin/productos/inventario/1",
                "/admin/categorias/listado",
                "/admin/colores/listado",
                "/admin/tallas/listado",
                "/admin/inventario",
                "/admin/pedidos"
        }) {
            var adminHtml = mvc.perform(get(adminPath).with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(adminHtml).as("Admin shell on " + adminPath)
                .contains("class=\"admin-shell-bar\"",
                        "href=\"/admin/productos/listado\"",
                        "href=\"/admin/categorias/listado\"",
                        "href=\"/admin/colores/listado\"",
                        "href=\"/admin/tallas/listado\"",
                        "href=\"/admin/inventario\"",
                        "href=\"/admin/pedidos\"");
        }
    }
}
