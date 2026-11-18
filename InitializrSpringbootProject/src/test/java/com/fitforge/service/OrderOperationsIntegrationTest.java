package com.fitforge.service;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class OrderOperationsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PedidoService pedidos;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PedidoOperacionService operaciones;
    @BeforeEach void reset() {
        OrderTestDatabase.requireDisposable();
        org.mockito.Mockito.reset(operaciones);
        jdbc.update("DELETE FROM pedido_evento"); jdbc.update("DELETE FROM venta"); jdbc.update("DELETE FROM factura");
        jdbc.update("UPDATE producto_color_talla SET existencias=CASE id_producto_color_talla WHEN 1 THEN 5 WHEN 2 THEN 0 ELSE 3 END");
    }
    @AfterEach void clean() { reset(); SecurityContextHolder.clearContext(); }
    long buy() {
        var ctx=SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken("cliente@fitforge.example","unused",List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(ctx);
        try {return pedidos.confirmar(new PedidoService.Comando(UUID.randomUUID(),List.of(new PedidoService.Linea(1L,1,new BigDecimal("25.00"))))).idFactura();}
        finally {SecurityContextHolder.clearContext();}
    }
    @Test void adminQueueAndOwnerHistoryReadCommittedOrders() throws Exception {
        long id=buy();
        mvc.perform(get("/admin/pedidos").with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("cliente@fitforge.example")))
            .andExpect(content().string(containsString("Pendiente")));
        mvc.perform(get("/admin/pedidos/"+id).with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Camiseta deportiva")));
        mvc.perform(get("/pedidos").with(user("cliente@fitforge.example").roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("25.00")));
    }
    @Test void preparationAndDispatchAreAuditedOnceWithoutTouchingStock() throws Exception {
        long id=buy();int stock=jdbc.queryForObject("SELECT SUM(existencias) FROM producto_color_talla",Integer.class);
        for(String[] edge : List.of(new String[]{"Pendiente","En preparación"},new String[]{"En preparación","Despachado"},new String[]{"Pendiente","En preparación"})) {
            mvc.perform(post("/admin/pedidos/"+id+"/estado").with(user("admin@fitforge.example").roles("ADMIN")).with(csrf())
                .param("origen",edge[0]).param("destino",edge[1])).andExpect(status().isSeeOther());
        }
        assertThat(jdbc.queryForObject("SELECT estado FROM factura WHERE id_factura=?",String.class,id)).isEqualTo("Despachado");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento WHERE id_factura=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id_usuario FROM pedido_evento WHERE id_factura=?",Integer.class,id)).containsOnly(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento WHERE fecha IS NOT NULL AND origen<>destino",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(existencias) FROM producto_color_talla",Integer.class)).isEqualTo(stock);
        mvc.perform(get("/admin/pedidos/"+id).with(user("admin@fitforge.example").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("admin@fitforge.example")));
        mvc.perform(get("/carrito/factura/"+id).with(user("cliente@fitforge.example").roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Despachado")));
    }

    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder delivery(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r) {
        return r.param("destinatario","Persona Demo OrderOps").param("telefonoEntrega","000-000-0000")
            .param("direccionEntrega","Calle Ficticia 123, Ciudad Demo").param("notaEntrega","Solo demostración").param("consentimiento","true");
    }
    @Test void checkoutFreezesFictitiousDestinationAndRejectsAlteredReplay() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/carrito/agregar/1").session(session).with(user("cliente@fitforge.example").roles("USER")).with(csrf()));
        String key=(String)mvc.perform(get("/carrito/ver").session(session).with(user("cliente@fitforge.example").roles("USER")))
            .andReturn().getModelAndView().getModel().get("claveConfirmacion");
        var first=mvc.perform(delivery(post("/carrito/confirmar-pedido").session(session).with(user("cliente@fitforge.example").roles("USER")).with(csrf()).param("clave",key)))
            .andExpect(status().isSeeOther()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(first).with(user("cliente@fitforge.example").roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Calle Ficticia 123, Ciudad Demo")));
        mvc.perform(delivery(post("/carrito/confirmar-pedido").session(session).with(user("cliente@fitforge.example").roles("USER")).with(csrf()).param("clave",key)))
            .andExpect(redirectedUrl(first));
        mvc.perform(delivery(post("/carrito/confirmar-pedido").session(session).with(user("cliente@fitforge.example").roles("USER")).with(csrf()).param("clave",key))
                .with(req->{req.setParameter("direccionEntrega","Otra dirección ficticia 456");return req;}))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT direccion_entrega FROM factura",String.class)).isEqualTo("Calle Ficticia 123, Ciudad Demo");
    }

    <T> T asAdmin(java.util.concurrent.Callable<T> action) throws Exception {
        var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(new UsernamePasswordAuthenticationToken("admin@fitforge.example","unused",List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(ctx);try{return action.call();}finally{SecurityContextHolder.clearContext();}
    }
    @Test void simultaneousPreparationRequestsReturnOneChangeAndOneReplay() throws Exception {
        long id=buy();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> task=()->asAdmin(()->{ready.countDown();assertThat(go.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return operaciones.transicionar(id,"Pendiente","En preparación");});
            var a=pool.submit(task);var b=pool.submit(task);assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento WHERE id_factura=?",Integer.class,id)).isEqualTo(1);
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
    }

    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {return user("admin@fitforge.example").roles("ADMIN");}
    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ownerUser() {return user("cliente@fitforge.example").roles("USER");}
    @Test void invalidTransitionsAre409AndDoNotWriteAuditOrStock() throws Exception {
        long id=buy();
        for(String[] edge:List.of(new String[]{"Pendiente","Despachado"},new String[]{"En preparación","Despachado"},new String[]{"Pendiente","Pendiente"},new String[]{"Pendiente","Cancelado"})) {
            mvc.perform(post("/admin/pedidos/"+id+"/estado").with(adminUser()).with(csrf()).param("origen",edge[0]).param("destino",edge[1]))
                .andExpect(status().isConflict());
        }
        assertThat(jdbc.queryForObject("SELECT estado FROM factura WHERE id_factura=?",String.class,id)).isEqualTo("Pendiente");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(existencias) FROM producto_color_talla",Integer.class)).isEqualTo(7);
    }
    @Test void adminRoutesRequireAdminAndPostWithCsrf() throws Exception {
        long id=buy();String route="/admin/pedidos/"+id+"/estado";
        mvc.perform(get("/admin/pedidos")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/pedidos").with(ownerUser())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/pedidos/"+id).with(ownerUser())).andExpect(status().isForbidden());
        mvc.perform(post(route).with(ownerUser()).with(csrf()).param("origen","Pendiente").param("destino","En preparación")).andExpect(status().isForbidden());
        mvc.perform(post(route).with(adminUser()).param("origen","Pendiente").param("destino","En preparación")).andExpect(status().isForbidden());
        mvc.perform(post(route).with(adminUser()).with(csrf().useInvalidToken()).param("origen","Pendiente").param("destino","En preparación")).andExpect(status().isForbidden());
        mvc.perform(get(route).with(adminUser())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/pedidos").with(adminUser())).andExpect(status().isForbidden());
        mvc.perform(get("/carrito/factura/"+id).with(adminUser())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/pedidos/999999999/estado").with(adminUser()).with(csrf()).param("origen","Pendiente").param("destino","En preparación")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento",Integer.class)).isZero();
    }
    @Test void otherOwnerHasEmptyHistoryAndIndistinguishableMissingDetail() throws Exception {
        long id=buy();
        jdbc.update("INSERT INTO usuario(id_usuario,username,password,nombre,correo,activo) VALUES(3,'orderops_other','unused','Otro Demo','otro-orderops@fitforge.example',true)");
        try {
            var other=user("otro-orderops@fitforge.example").roles("USER");
            var page=mvc.perform(get("/pedidos").with(other)).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
            assertThat(((PedidoOperacionService.Pagina)page.get("resultado")).total()).isZero();
            mvc.perform(get("/pedidos/"+id).with(other)).andExpect(status().isNotFound());
            mvc.perform(get("/pedidos/999999999").with(other)).andExpect(status().isNotFound());
            mvc.perform(get("/carrito/factura/"+id).with(other)).andExpect(status().isNotFound());
            mvc.perform(get("/carrito/factura/999999999").with(other)).andExpect(status().isNotFound());
        }finally{jdbc.update("DELETE FROM usuario WHERE id_usuario=3");}
    }
    PedidoOperacionService.Pagina page(String estado,String q,int number) throws Exception {return asAdmin(()->operaciones.listar(true,estado,q,number));}
    @Test void filtersCountsAndBoundedPaginationComeFromDatabase() throws Exception {
        long id=buy();
        for(int i=0;i<22;i++) jdbc.update("INSERT INTO factura(id_usuario,clave_confirmacion,huella_carrito,fecha,total,estado,destinatario,telefono_entrega,direccion_entrega,nota_entrega,consentimiento_demo) SELECT id_usuario,UUID(),huella_carrito,fecha,total,estado,destinatario,telefono_entrega,direccion_entrega,nota_entrega,consentimiento_demo FROM factura WHERE id_factura=?",id);
        asAdmin(()->operaciones.transicionar(id,"Pendiente","En preparación"));
        assertThat(page("","",0).total()).isEqualTo(23);assertThat(page("","",0).pedidos()).hasSize(20);
        assertThat(page("","",0).siguiente()).isTrue();assertThat(page("","",1).pedidos()).hasSize(3);
        assertThat(page("","",1).anterior()).isTrue();assertThat(page("","",1).siguiente()).isFalse();
        assertThat(page("","",2).pedidos()).isEmpty();
        assertThat(page("En preparación","",0).total()).isEqualTo(1);
        assertThat(page("Pendiente","",0).conteos()).containsEntry("Pendiente",22L).containsEntry("En preparación",1L).containsEntry("Despachado",0L);
        assertThat(page("","cliente@fitforge.example",0).total()).isEqualTo(23);
        assertThat(page("","Persona ficticia",0).total()).isEqualTo(23);
        assertThat(page("",String.valueOf(id),0).total()).isEqualTo(1);
        assertThat(page("","%' OR 1=1 --",0).total()).isZero();
        mvc.perform(get("/admin/pedidos").with(adminUser()).param("pagina","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/pedidos").with(adminUser()).param("pagina","100001")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/pedidos").with(adminUser()).param("estado","Cancelado")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/pedidos").with(adminUser()).param("q","x".repeat(101))).andExpect(status().isBadRequest());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"destinatario,x","telefonoEntrega,abcdefg","direccionEntrega,corta","consentimiento,false"})
    void invalidDeliveryRejectsEntireCheckout(String field,String value) throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/carrito/agregar/1").session(session).with(ownerUser()).with(csrf()));
        String key=(String)mvc.perform(get("/carrito/ver").session(session).with(ownerUser())).andReturn().getModelAndView().getModel().get("claveConfirmacion");
        mvc.perform(delivery(post("/carrito/confirmar-pedido").session(session).with(ownerUser()).with(csrf()).param("clave",key))
            .with(req->{req.setParameter(field,value);return req;})).andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(existencias) FROM producto_color_talla",Integer.class)).isEqualTo(8);
        mvc.perform(delivery(post("/carrito/confirmar-pedido").session(session).with(ownerUser()).with(csrf()).param("clave",key))).andExpect(status().isSeeOther());
    }
    @Test void missingConsentAndMissingFieldsCannotUseInternalDemoDefaults() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/carrito/agregar/1").session(session).with(ownerUser()).with(csrf()));
        String key=(String)mvc.perform(get("/carrito/ver").session(session).with(ownerUser())).andReturn().getModelAndView().getModel().get("claveConfirmacion");
        mvc.perform(post("/carrito/confirmar-pedido").session(session).with(ownerUser()).with(csrf()).param("clave",key))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura",Integer.class)).isZero();
    }
    @Test void auditInsertFailureRollsBackStateChange() throws Exception {
        long id=buy();
        try(var c=java.sql.DriverManager.getConnection(System.getenv("DEMO_DB_URL"),"root",System.getenv("FITFORGE_TEST_ROOT_PASSWORD"));var st=c.createStatement()) {
            st.execute("CREATE TRIGGER orderops_fail_event BEFORE INSERT ON pedido_evento FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled event failure'");
            try {
                assertThatThrownBy(()->asAdmin(()->operaciones.transicionar(id,"Pendiente","En preparación"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThat(jdbc.queryForObject("SELECT estado FROM factura WHERE id_factura=?",String.class,id)).isEqualTo("Pendiente");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento",Integer.class)).isZero();
            }finally{st.execute("DROP TRIGGER orderops_fail_event");}
        }
    }
    @Test void activeRealAdminIdentityIsRequiredEvenWithForgedRole() throws Exception {
        long id=buy();
        mvc.perform(get("/admin/pedidos").with(user("missing@fitforge.example").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/pedidos").with(user("cliente@fitforge.example").roles("ADMIN"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE usuario SET activo=false WHERE id_usuario=1");
        try {mvc.perform(post("/admin/pedidos/"+id+"/estado").with(adminUser()).with(csrf()).param("origen","Pendiente").param("destino","En preparación")).andExpect(status().isForbidden());}
        finally{jdbc.update("UPDATE usuario SET activo=true WHERE id_usuario=1");}
    }
    @Test void persistentFingerprintRejectsChangedDestinationWithoutSessionCache() {
        var key=UUID.randomUUID();var lines=List.of(new PedidoService.Linea(1L,1,new BigDecimal("25.00")));
        var ctx=SecurityContextHolder.createEmptyContext();ctx.setAuthentication(new UsernamePasswordAuthenticationToken("cliente@fitforge.example","unused",List.of(new SimpleGrantedAuthority("ROLE_USER"))));SecurityContextHolder.setContext(ctx);
        try {
            var result=pedidos.confirmar(new PedidoService.Comando(key,lines,EntregaPedido.demo()));
            assertThat(pedidos.confirmar(new PedidoService.Comando(key,lines,EntregaPedido.demo())).idFactura()).isEqualTo(result.idFactura());
            var changed=new EntregaPedido("Otra Persona Demo","000-000-0000","Otra calle ficticia 456","",true);
            assertThatThrownBy(()->pedidos.confirmar(new PedidoService.Comando(key,lines,changed))).isInstanceOf(PedidoException.class).hasMessageContaining("confirmación");
            jdbc.update("UPDATE usuario SET telefono='000-000-9999' WHERE id_usuario=2");
            assertThat(pedidos.obtenerPropio(result.idFactura()).entrega()).isEqualTo(EntregaPedido.demo());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura",Integer.class)).isEqualTo(1);
        }finally{jdbc.update("UPDATE usuario SET telefono=NULL WHERE id_usuario=2");SecurityContextHolder.clearContext();}
    }

    @Test void conflictPageExplainsInvalidTransitionAndLinksBackToQueue() throws Exception {
        long id=buy();mvc.perform(post("/admin/pedidos/"+id+"/estado").with(adminUser()).with(csrf()).param("origen","Pendiente").param("destino","Despachado"))
            .andExpect(status().isConflict()).andExpect(content().string(containsString("Transición inválida")))
            .andExpect(content().string(containsString("Volver a pedidos")));
    }
    @Test void auditDatabaseFailureReturnsRecoverable503WithoutClaimingSuccess() throws Exception {
        long id=buy();
        try(var c=java.sql.DriverManager.getConnection(System.getenv("DEMO_DB_URL"),"root",System.getenv("FITFORGE_TEST_ROOT_PASSWORD"));var st=c.createStatement()) {
            st.execute("CREATE TRIGGER orderops_fail_event BEFORE INSERT ON pedido_evento FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled event failure'");
            try {
                mvc.perform(post("/admin/pedidos/"+id+"/estado").with(adminUser()).with(csrf()).param("origen","Pendiente").param("destino","En preparación"))
                    .andExpect(status().isServiceUnavailable()).andExpect(content().string(containsString("No se pudo verificar el cambio")));
                assertThat(jdbc.queryForObject("SELECT estado FROM factura WHERE id_factura=?",String.class,id)).isEqualTo("Pendiente");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento",Integer.class)).isZero();
            }finally{st.execute("DROP TRIGGER orderops_fail_event");}
        }
    }

    @Test void receiptLoadsNavigationScriptsForOwnerMenu() throws Exception {
        long id=buy();mvc.perform(get("/carrito/factura/"+id).with(ownerUser())).andExpect(status().isOk())
            .andExpect(content().string(containsString("/webjars/bootstrap/5.3.3/js/bootstrap.bundle.min.js")));
    }

    @Test void ownerReceiptShowsMilestonesWithoutAdminIdentity() throws Exception {
        long id=buy();asAdmin(()->operaciones.transicionar(id,"Pendiente","En preparación"));asAdmin(()->operaciones.transicionar(id,"En preparación","Despachado"));
        mvc.perform(get("/carrito/factura/"+id).with(ownerUser())).andExpect(status().isOk())
            .andExpect(content().string(containsString("Historial de estado")))
            .andExpect(content().string(containsString("En preparación")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("admin@fitforge.example"))));
    }

    @Test void clientReadFailureUsesClientReturnLinkAndReadingMessage() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("controlled read failure"))
            .when(operaciones).listar(false,"","",0);
        mvc.perform(get("/pedidos").with(ownerUser())).andExpect(status().isServiceUnavailable())
            .andExpect(content().string(containsString("No se pudieron cargar los pedidos")))
            .andExpect(content().string(containsString("href=\"/pedidos\" class=\"btn btn-primary\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("misma transición"))));
    }
    @Test void knownStockRollbackAllowsCorrectedDestinationWithSameKey() throws Exception {
        var session=new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/carrito/agregar/1").session(session).with(ownerUser()).with(csrf()));
        String key=(String)mvc.perform(get("/carrito/ver").session(session).with(ownerUser())).andReturn().getModelAndView().getModel().get("claveConfirmacion");
        jdbc.update("UPDATE producto_color_talla SET existencias=0 WHERE id_producto_color_talla=1");
        mvc.perform(delivery(post("/carrito/confirmar-pedido").session(session).with(ownerUser()).with(csrf()).param("clave",key)))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factura",Integer.class)).isZero();
        jdbc.update("UPDATE producto_color_talla SET existencias=5 WHERE id_producto_color_talla=1");
        mvc.perform(delivery(post("/carrito/confirmar-pedido").session(session).with(ownerUser()).with(csrf()).param("clave",key))
            .with(req->{req.setParameter("direccionEntrega","Destino corregido ficticio 789");return req;}))
            .andExpect(status().isSeeOther()).andExpect(redirectedUrlPattern("/carrito/factura/*"));
        assertThat(jdbc.queryForObject("SELECT direccion_entrega FROM factura",String.class)).isEqualTo("Destino corregido ficticio 789");
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1",Integer.class)).isEqualTo(4);
    }
    @Test void telephoneWithoutDigitsRejectsCheckout() throws Exception {
        invalidDeliveryRejectsEntireCheckout("telefonoEntrega","-------");
    }
}
