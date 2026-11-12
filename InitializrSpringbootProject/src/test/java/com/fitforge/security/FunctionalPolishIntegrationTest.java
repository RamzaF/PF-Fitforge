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
class FunctionalPolishIntegrationTest {
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

    @Test void inventoryRedirectEndsAtListWithVisibleUnavailableNotice() throws Exception {
        var result = mvc.perform(get("/admin/productos/inventario/1").with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(redirectedUrl("/admin/productos/listado")).andExpect(flash().attributeExists("mensaje")).andReturn();
        var html = mvc.perform(get("/admin/productos/listado").with(user("admin@fitforge.example").roles("ADMIN"))
            .flashAttrs(result.getFlashMap())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Inventario no disponible");
    }

    @ParameterizedTest
    @ValueSource(strings={"/admin/ropa-deportiva", "/admin/accesorios", "/admin/suplementos"})
    void secondaryNavigationOnlyOffersAvailableActions(String path) throws Exception {
        jdbc.update("UPDATE categoria SET descripcion=\"Ropa Deportiva\" WHERE id_categoria=1");
        jdbc.update("UPDATE categoria SET descripcion=\"Accesorios\" WHERE id_categoria=2");
        var html = mvc.perform(get(path).with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("href=\"/admin/productos/nuevo", "href=\"/admin/productos/inventario/", "href=\"/admin/productos/eliminar/");
        assertThat(html).contains("/admin/productos/listado", "Inventario no disponible");
    }

    @ParameterizedTest
    @ValueSource(strings={"/admin/productos/listado", "/admin/productos/editar/1"})
    void uploadControlsAreDisabledAndFormsKeepCsrf(String path) throws Exception {
        var html = mvc.perform(get(path).with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Subida de imágenes no disponible", "name=\"_csrf\"");
        var inputs = java.util.regex.Pattern.compile("<input[^>]*type=\"file\"[^>]*>").matcher(html);
        int count = 0;
        while (inputs.find()) { assertThat(inputs.group()).contains("disabled"); count++; }
        assertThat(count).isGreaterThan(0);
    }

    @ParameterizedTest
    @ValueSource(strings={"", "1"})
    void rejectedFileNeverCreatesOrChangesProduct(String id) throws Exception {
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
        assertThat(response.getFlashMap().get("tipo")).isEqualTo("warning");
        assertThat(response.getFlashMap().get("mensaje").toString()).contains("Subida de imágenes no disponible");
        assertThat(response.getResponse().getRedirectedUrl()).isEqualTo("/admin/productos/listado");
    }

    @Test void imageApiRejectsBeforeStorageWithSafeMessage() throws Exception {
        mvc.perform(multipart("/api/imagenes/subir").file(new MockMultipartFile("archivo", "demo.png", "image/png", new byte[]{1}))
            .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
            .andExpect(status().isServiceUnavailable()).andExpect(content().string("Subida de imágenes no disponible en esta demo."));
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
}
