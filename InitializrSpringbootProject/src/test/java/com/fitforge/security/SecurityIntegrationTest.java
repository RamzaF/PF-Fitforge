package com.fitforge.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fitforge.domain.Item;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Transactional
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    MockHttpSession login(String account) throws Exception {
        var result = mvc.perform(formLogin().user(account + "@fitforge.example").password("FitForge-demo-2026!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(account.equals("admin") ? "/admin/productos/listado" : "/"))
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test void anonymousCannotAdminister() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM producto", Integer.class)).isEqualTo(2);
        mvc.perform(get("/admin/productos/listado")).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(post("/usuario/guardar").with(csrf())).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test void clientCannotAdministerEvenWithCsrf() throws Exception {
        var session = login("cliente");
        mvc.perform(get("/admin/productos/listado").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/productos/eliminar").session(session).with(csrf()).param("id", "999999"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/imagenes/subir").session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/usuario/guardar").session(session).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void adminCanReadManagementButCannotUseClientCart() throws Exception {
        var session = login("admin");
        mvc.perform(get("/admin/productos/listado").session(session))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Camiseta deportiva")));
        mvc.perform(get("/perfil").session(session)).andExpect(status().isOk());
        mvc.perform(post("/carrito/agregar/1").session(session).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void adminMutationRequiresCsrfAndValidTokenReachesController() throws Exception {
        var session = login("admin");
        mvc.perform(post("/admin/productos/inventario/guardar").session(session)
            .param("producto.idProducto", "1").param("existencias", "-1"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/admin/productos/inventario/guardar").session(session).with(csrf())
            .param("producto.idProducto", "1").param("existencias", "-1"))
            .andExpect(redirectedUrl("/admin/productos/inventario/1"))
            .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/productos/inventario/guardar").session(session).with(csrf())
            .param("idProductoColorTalla", "1").param("producto.idProducto", "1")
            .param("color.idColor", "1").param("talla.idTalla", "1").param("existencias", "4"))
            .andExpect(redirectedUrl("/admin/productos/inventario/1"));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1", Integer.class)).isEqualTo(4);
        mvc.perform(get("/admin/productos/inventario/eliminar/999999").session(session)).andExpect(status().isMethodNotAllowed());
    }

    @Test void cartMutationRequiresSessionAndCsrfAndNeverUsesGet() throws Exception {
        var session = login("cliente");
        mvc.perform(post("/carrito/agregar/1").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/carrito/agregar/1").session(session).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        assertThat(session.getAttribute("carrito")).isNull();
        mvc.perform(post("/carrito/agregar/1").with(csrf())).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(post("/carrito/agregar/1").session(session).with(csrf()).param("cantidad", "2")
            .param("precio", "0.01").param("total", "0.02"))
            .andExpect(redirectedUrl("/carrito/ver"));
        var items = (List<Item>) session.getAttribute("carrito");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getCantidad()).isEqualTo(2);
        assertThat(items.getFirst().getSubtotal()).isEqualTo(50.0);
        mvc.perform(get("/carrito/agregar/1").session(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/carrito/eliminar/1").session(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/carrito/ver").session(session)).andExpect(status().isOk())
            .andExpect(model().attribute("total", 50.0));
        mvc.perform(post("/carrito/eliminar/1").session(session).with(csrf())).andExpect(redirectedUrl("/carrito/ver"));
        assertThat((List<?>) session.getAttribute("carrito")).isEmpty();
    }

    @Test void rejectedCartUpdateKeepsOriginalAndShowsFeedback() throws Exception {
        var session = login("cliente");
        mvc.perform(post("/carrito/agregar/1").session(session).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/carrito/actualizar").session(session).with(csrf()).param("id", "1").param("cantidad", "6"))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(((List<Item>) session.getAttribute("carrito")).getFirst().getCantidad()).isEqualTo(1);
    }

    @Test void loginAndLogoutRequireCsrfAndLogoutInvalidatesCart() throws Exception {
        mvc.perform(post("/login").param("username", "cliente@fitforge.example").param("password", "FitForge-demo-2026!"))
            .andExpect(status().isForbidden());
        var session = login("cliente");
        mvc.perform(post("/carrito/agregar/1").session(session).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        assertThat(session.isInvalid()).isFalse();
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void twoAuthenticatedSessionsHaveIndependentCarts() throws Exception {
        var first = login("cliente");
        var second = login("cliente");
        mvc.perform(post("/carrito/agregar/1").session(first).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/carrito/ver").session(second)).andExpect(status().isOk()).andExpect(model().attribute("total", 0.0));
        mvc.perform(post("/carrito/agregar/3").session(second).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/carrito/ver").session(first)).andExpect(status().isOk()).andExpect(model().attribute("total", 25.0));
        mvc.perform(get("/carrito/ver").session(second)).andExpect(status().isOk()).andExpect(model().attribute("total", 15.0));
    }

    @Test void changingLoginEmailRequiresFreshSessionAndNewIdentityWorks() throws Exception {
        var session = login("cliente");
        mvc.perform(post("/perfil").session(session).with(csrf())
            .param("username", "demo_cliente").param("nombre", "Cliente ficticio")
            .param("correo", "cliente-cambiado@fitforge.example"))
            .andExpect(redirectedUrl("/login"));
        assertThat(session.isInvalid()).isTrue();
        assertThat(jdbc.queryForObject("SELECT correo FROM usuario WHERE id_usuario=2", String.class))
            .isEqualTo("cliente-cambiado@fitforge.example");
        mvc.perform(formLogin().user("cliente-cambiado@fitforge.example").password("FitForge-demo-2026!"))
            .andExpect(redirectedUrl("/"));
    }

    @ParameterizedTest
    @ValueSource(strings={"/tienda/listado", "/productos/1", "/css/estilos.css", "/js/rutinas.js", "/uploads/guantes_gym.png", "/webjars/bootstrap/5.3.3/css/bootstrap.min.css"})
    void catalogAndActualResourcesRemainPublic(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test void legacyResourceRouteIsPublicEvenWhenMissingWithoutProxyRewrite() throws Exception {
        mvc.perform(get("/Uploads/Banner.PNG")).andExpect(status().isNotFound());
    }

    @Test void unknownRoutesAndUnownedInvoicesAreNotPublic() throws Exception {
        mvc.perform(get("/private-unmapped")).andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/carrito/factura/1").session(login("cliente"))).andExpect(status().isForbidden());
    }

    @Test void demoUnsupportedMailNeverClaimsSuccessOrWritesContact() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM contacto", Integer.class);
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "cliente@fitforge.example"))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/contacto/enviar").with(csrf()).param("nombre", "Demo").param("apellido", "Demo")
            .param("correo", "demo@fitforge.example").param("mensaje", "Mensaje ficticio"))
            .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contacto", Integer.class)).isEqualTo(before);
        mvc.perform(get("/contacto?exito")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("¡Mensaje enviado con éxito!"))));
    }

    @Test void pendingCheckoutCannotEmptyCartOrClaimPurchase() throws Exception {
        var session = login("cliente");
        mvc.perform(post("/carrito/agregar/1").session(session).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/carrito/confirmar-pedido").session(session).with(csrf())).andExpect(status().isForbidden());
        assertThat((List<?>) session.getAttribute("carrito")).hasSize(1);
    }
}
