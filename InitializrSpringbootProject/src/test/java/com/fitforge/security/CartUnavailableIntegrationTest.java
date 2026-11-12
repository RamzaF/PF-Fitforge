package com.fitforge.security;

import com.fitforge.domain.Item;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class CartUnavailableIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @ParameterizedTest
    @ValueSource(strings = {"deleted", "inactive"})
    @SuppressWarnings("unchecked")
    void unavailableLineDoesNotTrapSessionOrLoseValidLines(String change) throws Exception {
        var session = (MockHttpSession) mvc.perform(formLogin()
                .user("cliente@fitforge.example").password("FitForge-demo-2026!"))
            .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        for (long id : new long[]{1, 3}) {
            mvc.perform(post("/carrito/agregar/" + id).session(session).with(csrf()).param("cantidad", "2"))
                .andExpect(redirectedUrl("/carrito/ver"));
        }
        // Real persistence change AFTER both items have been added to this HTTP session.
        if (change.equals("deleted")) {
            jdbc.update("DELETE FROM producto_color_talla WHERE id_producto_color_talla=1");
        } else {
            jdbc.update("UPDATE producto SET activo=false WHERE id_producto=1");
        }
        jdbc.update("UPDATE producto SET precio=17 WHERE id_producto=2");
        em.clear();

        var result = mvc.perform(get("/carrito/ver").session(session)).andExpect(status().isOk())
            .andExpect(view().name("carrito/listado"))
            .andExpect(content().string(containsString("No disponible")))
            .andExpect(content().string(containsString("no se incluye en el total")))
            .andExpect(content().string(containsString("Pedidos no disponibles")))
            .andExpect(content().string(containsString("/carrito/eliminar/1")))
            .andExpect(content().string(containsString("_csrf")))
            .andExpect(model().attribute("total", 34.0)).andReturn();
        var displayed = (List<Item>) result.getModelAndView().getModel().get("items");
        assertThat(displayed).hasSize(2);
        assertThat(displayed.getFirst().getCantidad()).isEqualTo(2);
        assertThat(displayed.getFirst().getSubtotal()).isZero();
        assertThat(displayed.getLast().getProductoColorTalla().getProducto().getPrecio()).isEqualTo(17);
        assertThat((List<Item>) session.getAttribute("carrito")).hasSize(2);

        mvc.perform(post("/carrito/actualizar").session(session).with(csrf())
            .param("id", "1").param("cantidad", "1"))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(((List<Item>) session.getAttribute("carrito")).getFirst().getCantidad()).isEqualTo(2);
        mvc.perform(post("/carrito/actualizar").session(session).with(csrf())
            .param("id", "3").param("cantidad", "1").param("precio", "0.01"))
            .andExpect(redirectedUrl("/carrito/ver"));
        mvc.perform(get("/carrito/ver").session(session)).andExpect(status().isOk())
            .andExpect(model().attribute("total", 17.0));
        mvc.perform(post("/carrito/eliminar/1").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/carrito/confirmar-pedido").session(session).with(csrf())).andExpect(status().isForbidden());
        assertThat((List<Item>) session.getAttribute("carrito")).hasSize(2);
        mvc.perform(post("/carrito/eliminar/1").session(session).with(csrf()))
            .andExpect(redirectedUrl("/carrito/ver"));
        mvc.perform(get("/carrito/ver").session(session)).andExpect(status().isOk())
            .andExpect(model().attribute("total", 17.0))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("No disponible"))));
        assertThat((List<Item>) session.getAttribute("carrito")).hasSize(1);
        assertThat(((List<Item>) session.getAttribute("carrito")).getFirst()
            .getProductoColorTalla().getIdProductoColorTalla()).isEqualTo(3L);
    }
}
