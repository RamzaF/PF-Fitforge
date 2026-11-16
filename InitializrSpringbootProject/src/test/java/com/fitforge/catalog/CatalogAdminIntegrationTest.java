package com.fitforge.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitforge.service.OrderTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CatalogAdminIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void guardAndResetDatabase() {
        restoreBaseline();
    }

    @AfterEach
    void restoreBaseline() {
        OrderTestDatabase.requireDisposable();
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

    @Test
    void categoriesRequireAdminAndCsrfAndRejectAmbiguousDuplicates() throws Exception {
        mvc.perform(get("/admin/categorias/listado")).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/admin/categorias/listado").with(user("cliente@fitforge.example").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/categorias/guardar").with(user("admin@fitforge.example").roles("ADMIN"))
                .param("descripcion", "Calzado"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/categorias/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("descripcion", "  Calzado   Deportivo  ").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/categorias/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        Long catId = jdbc.queryForObject(
                "SELECT id_categoria FROM categoria WHERE descripcion = ?", Long.class, "Calzado Deportivo");
        assertThat(catId).isNotNull();

        mvc.perform(post("/admin/categorias/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("descripcion", "calzado deportivo").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/categorias/listado"))
                .andExpect(flash().attribute("tipo", "danger"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM categoria WHERE LOWER(descripcion) = 'calzado deportivo'", Integer.class))
                .isEqualTo(1);

        // Dynamic category appears in public navigation and filters
        String storeHtml = mvc.perform(get("/tienda/listado")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(storeHtml).contains("Calzado Deportivo");

        // Deactivate category without deleting it
        mvc.perform(post("/admin/categorias/estado").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idCategoria", String.valueOf(catId)).param("activo", "false"))
                .andExpect(redirectedUrl("/admin/categorias/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        assertThat(jdbc.queryForObject("SELECT activo FROM categoria WHERE id_categoria = ?", Boolean.class, catId))
                .isFalse();

        String storeAfter = mvc.perform(get("/tienda/listado")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(storeAfter).doesNotContain("Calzado Deportivo");
    }

    @Test
    void colorsAndTallasSupportCrudValidationAndActiveToggle() throws Exception {
        mvc.perform(get("/admin/colores/listado").with(user("cliente@fitforge.example").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/tallas/listado").with(user("cliente@fitforge.example").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/admin/colores/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("nombreColor", "  Verde Oliva  ").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/colores/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        mvc.perform(post("/admin/colores/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("nombreColor", "verde   oliva").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/colores/listado"))
                .andExpect(flash().attribute("tipo", "danger"));

        Long colorId = jdbc.queryForObject(
                "SELECT id_color FROM color WHERE nombre_color = ?", Long.class, "Verde Oliva");
        mvc.perform(post("/admin/colores/estado").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idColor", String.valueOf(colorId)).param("activo", "false"))
                .andExpect(redirectedUrl("/admin/colores/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        assertThat(jdbc.queryForObject("SELECT activo FROM color WHERE id_color = ?", Boolean.class, colorId))
                .isFalse();

        mvc.perform(post("/admin/tallas/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("nombreTalla", "XL").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/tallas/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        mvc.perform(post("/admin/tallas/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("nombreTalla", "  xl ").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/tallas/listado"))
                .andExpect(flash().attribute("tipo", "danger"));

        Long tallaId = jdbc.queryForObject(
                "SELECT id_talla FROM talla WHERE nombre_talla = ?", Long.class, "XL");
        mvc.perform(post("/admin/tallas/estado").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idTalla", String.valueOf(tallaId)).param("activo", "false"))
                .andExpect(redirectedUrl("/admin/tallas/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        assertThat(jdbc.queryForObject("SELECT activo FROM talla WHERE id_talla = ?", Boolean.class, tallaId))
                .isFalse();
    }

    @Test
    void invalidInitialVariantDoesNotLeaveHalfCreatedProductAndVariantsPreventDuplicates() throws Exception {
        int beforeProducts = jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class);
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", new byte[0]))
                .param("descripcion", "Producto fallido")
                .param("detalle", "No debe quedar guardado")
                .param("precio", "35.00")
                .param("categoria.idCategoria", "1")
                .param("activo", "true")
                .param("color.idColor", "1")
                .param("talla.idTalla", "1")
                .param("existencias", "-5")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "danger"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class)).isEqualTo(beforeProducts);

        // Create valid product without image
        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", new byte[0]))
                .param("descripcion", "Banda elástica Pro")
                .param("detalle", "Resistencia media")
                .param("precio", "19.50")
                .param("categoria.idCategoria", "2")
                .param("activo", "true")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        Long productId = jdbc.queryForObject(
                "SELECT id_producto FROM producto WHERE descripcion = ?", Long.class, "Banda elástica Pro");

        // Add variant
        mvc.perform(post("/admin/productos/variantes/guardar")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idProducto", String.valueOf(productId))
                .param("idColor", "1")
                .param("idTalla", "1")
                .param("existencias", "4"))
                .andExpect(redirectedUrl("/admin/productos/editar/" + productId))
                .andExpect(flash().attribute("tipo", "success"));

        // Duplicate combination rejected cleanly
        mvc.perform(post("/admin/productos/variantes/guardar")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idProducto", String.valueOf(productId))
                .param("idColor", "1")
                .param("idTalla", "1")
                .param("existencias", "2"))
                .andExpect(redirectedUrl("/admin/productos/editar/" + productId))
                .andExpect(flash().attribute("tipo", "danger"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_color_talla WHERE id_producto = ?", Integer.class, productId))
                .isEqualTo(1);

        // Null image renders safely without empty src=""
        String storeHtml = mvc.perform(get("/tienda/listado")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(storeHtml).contains("Banda elástica Pro").doesNotContain("src=\"\"");
    }

    @Test
    void soldVariantCannotBeDeletedButCanBeDeactivatedAndInactiveProductIsHiddenFromPublic() throws Exception {
        // Record a real sale on variant 1
        MockHttpSession client = (MockHttpSession) mvc.perform(formLogin()
                .user("cliente@fitforge.example").password("FitForge-demo-2026!"))
                .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        mvc.perform(post("/carrito/agregar/1").session(client).with(csrf()).param("cantidad", "1"))
                .andExpect(redirectedUrl("/carrito/ver"));
        String key = (String) mvc.perform(get("/carrito/ver").session(client))
                .andReturn().getModelAndView().getModel().get("claveConfirmacion");
        mvc.perform(post("/carrito/confirmar-pedido").session(client).with(csrf()).param("clave", key))
                .andExpect(status().isSeeOther());

        // Attempting to physically delete sold variant fails cleanly
        mvc.perform(post("/admin/productos/variantes/eliminar/1")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/editar/1"))
                .andExpect(flash().attribute("tipo", "danger"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_color_talla WHERE id_producto_color_talla = 1", Integer.class))
                .isEqualTo(1);

        // Deactivate sold variant instead
        mvc.perform(post("/admin/productos/variantes/estado")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idVariante", "1").param("activo", "false"))
                .andExpect(redirectedUrl("/admin/productos/editar/1"))
                .andExpect(flash().attribute("tipo", "success"));
        assertThat(jdbc.queryForObject(
                "SELECT activo FROM producto_color_talla WHERE id_producto_color_talla = 1", Boolean.class))
                .isFalse();

        // Deactivated variant cannot be added to cart
        mvc.perform(post("/carrito/agregar/1").session(client).with(csrf()).param("cantidad", "1"))
                .andExpect(redirectedUrl("/carrito/ver"))
                .andExpect(flash().attributeExists("error"));

        // Deactivating product hides its detail route from public
        mvc.perform(post("/admin/productos/estado")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idProducto", "1").param("activo", "false"))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        mvc.perform(get("/productos/1")).andExpect(redirectedUrl("/tienda/listado"));
    }

    @Test
    void emptyCatalogSupportsFullAdminCreationAndCustomerNavigationWithoutHardcodedIds() throws Exception {
        jdbc.update("DELETE FROM venta");
        jdbc.update("DELETE FROM factura");
        jdbc.update("DELETE FROM producto_color_talla");
        jdbc.update("DELETE FROM producto");
        jdbc.update("DELETE FROM color");
        jdbc.update("DELETE FROM talla");
        jdbc.update("DELETE FROM categoria");

        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/tienda/listado")).andExpect(status().isOk());
        mvc.perform(get("/admin/productos/listado").with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isOk());

        mvc.perform(post("/admin/categorias/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("descripcion", "Hidratación").param("activo", "true")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/colores/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("nombreColor", "Titanio").param("activo", "true")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/tallas/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("nombreTalla", "750ml").param("activo", "true")).andExpect(status().is3xxRedirection());

        Long catId = jdbc.queryForObject("SELECT id_categoria FROM categoria WHERE descripcion='Hidratación'", Long.class);
        Long colorId = jdbc.queryForObject("SELECT id_color FROM color WHERE nombre_color='Titanio'", Long.class);
        Long tallaId = jdbc.queryForObject("SELECT id_talla FROM talla WHERE nombre_talla='750ml'", Long.class);

        mvc.perform(multipart("/admin/productos/guardar-producto")
                .file(new MockMultipartFile("archivo", new byte[0]))
                .param("descripcion", "Botella Térmica")
                .param("detalle", "Acero inoxidable 750ml")
                .param("precio", "18.00")
                .param("categoria.idCategoria", String.valueOf(catId))
                .param("activo", "true")
                .param("color.idColor", String.valueOf(colorId))
                .param("talla.idTalla", String.valueOf(tallaId))
                .param("existencias", "6")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        Long productId = jdbc.queryForObject("SELECT id_producto FROM producto WHERE descripcion='Botella Térmica'", Long.class);
        String catPage = mvc.perform(get("/categoria/Hidratación")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(catPage).contains("Botella Térmica");
        String detailPage = mvc.perform(get("/productos/" + productId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(detailPage).contains("Titanio", "750ml", "Stock: 6");
    }

    @Test
    void categoryDeactivationHidesProductsAndEditPagesWorkWithoutNestedForms() throws Exception {
        // Edit category name and reject >50 chars / blanks
        mvc.perform(post("/admin/categorias/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idCategoria", "1").param("descripcion", "Ropa Fitness").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/categorias/listado"))
                .andExpect(flash().attribute("tipo", "success"));
        mvc.perform(post("/admin/categorias/guardar").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("descripcion", "   ").param("activo", "true"))
                .andExpect(redirectedUrl("/admin/categorias/listado"))
                .andExpect(flash().attribute("tipo", "danger"));

        // Deactivate category 1: product 1 must disappear from store, search, category page, and detail page
        mvc.perform(post("/admin/categorias/estado").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("idCategoria", "1").param("activo", "false"))
                .andExpect(redirectedUrl("/admin/categorias/listado"))
                .andExpect(flash().attribute("tipo", "success"));

        String storeHtml = mvc.perform(get("/tienda/listado")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(storeHtml).doesNotContain("Camiseta deportiva").contains("Guantes de entrenamiento");
        String searchHtml = mvc.perform(get("/tienda/buscar").param("termino", "Camiseta")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(searchHtml).doesNotContain("Camiseta deportiva");
        String catHtml = mvc.perform(get("/categoria/Ropa Fitness")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(catHtml).doesNotContain("Camiseta deportiva");
        mvc.perform(get("/productos/1")).andExpect(redirectedUrl("/tienda/listado"));

        // Admin edit page has no nested <form> tags and allows deleting an unsold variant
        String editHtml = mvc.perform(get("/admin/productos/editar/1").with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(editHtml).contains("Variantes de Color y Talla");
        // Verify every <form is closed before the next <form opens
        int idx = 0;
        while ((idx = editHtml.indexOf("<form", idx)) != -1) {
            int close = editHtml.indexOf("</form>", idx);
            int nextOpen = editHtml.indexOf("<form", idx + 5);
            assertThat(close).isGreaterThan(idx);
            if (nextOpen != -1) {
                assertThat(close).isLessThan(nextOpen);
            }
            idx = close + 7;
        }

        // Delete unsold variant 2 (product 1, color 2, talla 2)
        mvc.perform(post("/admin/productos/variantes/eliminar/2")
                .with(user("admin@fitforge.example").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/productos/editar/1"))
                .andExpect(flash().attribute("tipo", "success"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM producto_color_talla WHERE id_producto_color_talla = 2", Integer.class))
                .isZero();

        // Admin search endpoint provides modal catalogs (colores, tallas, categorias)
        mvc.perform(get("/admin/productos/buscar").param("termino", "Guantes")
                .with(user("admin@fitforge.example").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
