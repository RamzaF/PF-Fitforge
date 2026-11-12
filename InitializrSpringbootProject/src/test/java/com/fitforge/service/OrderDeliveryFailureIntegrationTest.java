package com.fitforge.service;

import java.util.concurrent.atomic.AtomicBoolean;
import com.fitforge.service.impl.PedidoServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Import(OrderDeliveryFailureIntegrationTest.LostDelivery.class)
class OrderDeliveryFailureIntegrationTest {
    @TestConfiguration static class LostDelivery {
        @Bean AtomicBoolean loseNextResult() {return new AtomicBoolean();}
        @Bean @Primary PedidoService deliveredOrders(PedidoServiceImpl committed,AtomicBoolean loseNextResult) {
            return new PedidoService() {
                public Integer propietario(){return committed.propietario();}
                public Comprobante obtenerPropio(Long id){return committed.obtenerPropio(id);}
                public Confirmacion confirmar(Comando c){
                    var result=committed.confirmar(c); // Real proxy, real MySQL commit has already returned.
                    if(loseNextResult.getAndSet(false)) throw new org.springframework.transaction.TransactionSystemException("Controlled loss of result delivery AFTER real commit");
                    return result;
                }
            };
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AtomicBoolean loseNextResult;
    @BeforeEach @AfterEach void reset(){
        OrderTestDatabase.requireDisposable();loseNextResult.set(false);
        jdbc.update("DELETE FROM venta");jdbc.update("DELETE FROM factura");jdbc.update("UPDATE producto SET precio=25 WHERE id_producto=1");
        jdbc.update("UPDATE producto_color_talla SET existencias=5 WHERE id_producto_color_talla=1");
    }
    @Test void lostResultAfterCommitMustRetainOriginalKeyEvenIfPriceChangesBeforeRetry() throws Exception {
        var session=(MockHttpSession)mvc.perform(formLogin().user("cliente@fitforge.example").password("FitForge-demo-2026!"))
            .andReturn().getRequest().getSession(false);
        mvc.perform(post("/carrito/agregar/1").session(session).with(csrf()).param("cantidad","2")).andExpect(status().is3xxRedirection());
        String key=(String)mvc.perform(get("/carrito/ver").session(session)).andReturn().getModelAndView().getModel().get("claveConfirmacion");
        loseNextResult.set(true);
        mvc.perform(post("/carrito/confirmar-pedido").session(session).with(csrf()).param("clave",key)).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura",Integer.class)).isEqualTo(1);
        assertThat((java.util.List<?>)session.getAttribute("carrito")).hasSize(1);
        jdbc.update("UPDATE producto SET precio=30 WHERE id_producto=1");
        String retry=(String)mvc.perform(get("/carrito/ver").session(session)).andReturn().getModelAndView().getModel().get("claveConfirmacion");
        assertThat(retry).as("delivery failure must not issue a different key on cart refresh").isEqualTo(key);
        mvc.perform(post("/carrito/confirmar-pedido").session(session).with(csrf()).param("clave",retry)).andExpect(status().isSeeOther());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total FROM factura",Double.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1",Integer.class)).isEqualTo(3);
        System.out.println("EVIDENCE injected delivery loss AFTER real commit, same key retained despite price change, replay header=1 total=50 stock=3");
    }
}
