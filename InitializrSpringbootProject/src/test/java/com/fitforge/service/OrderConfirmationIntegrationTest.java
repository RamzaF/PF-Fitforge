package com.fitforge.service;

import java.util.List;
import com.fitforge.domain.Item;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class OrderConfirmationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void baseline() {
        OrderTestDatabase.requireDisposable();
        jdbc.update("DELETE FROM venta");
        jdbc.update("DELETE FROM factura");
        jdbc.update("UPDATE producto SET activo=true, precio=CASE id_producto WHEN 1 THEN 25 ELSE 15 END, descripcion=CASE id_producto WHEN 1 THEN 'Camiseta deportiva' ELSE 'Guantes de entrenamiento' END");
        jdbc.update("UPDATE producto_color_talla SET existencias=CASE id_producto_color_talla WHEN 1 THEN 5 WHEN 2 THEN 0 ELSE 3 END");
    }
    @AfterEach void restore() { baseline(); }
    MockHttpSession login() throws Exception {
        return (MockHttpSession)mvc.perform(formLogin().user("cliente@fitforge.example").password("FitForge-demo-2026!"))
            .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
    }
    String review(MockHttpSession session) throws Exception {
        return (String)mvc.perform(get("/carrito/ver").session(session)).andExpect(status().isOk())
            .andReturn().getModelAndView().getModel().get("claveConfirmacion");
    }
    void add(MockHttpSession s, long id, int quantity) throws Exception {
        mvc.perform(post("/carrito/agregar/"+id).session(s).with(csrf()).param("cantidad", ""+quantity)).andExpect(status().is3xxRedirection());
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }

    @Test void committedOrderSurvivesReloadAndFreshSessionWithServerPrices() throws Exception {
        var s=login(); add(s,1,2); add(s,3,1); var key=review(s);
        assertThat(key).as("server confirmation token").isNotBlank();
        var result=mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key)
            .param("precio","0.01").param("total","0.01").param("idUsuario","1"))
            .andExpect(status().isSeeOther()).andReturn();
        assertThat(count("factura")).isEqualTo(1); assertThat(count("venta")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT total FROM factura",Double.class)).isEqualTo(65);
        assertThat(jdbc.queryForObject("SELECT id_usuario FROM factura",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1",Integer.class)).isEqualTo(3);
        assertThat((List<?>)s.getAttribute("carrito")).isNullOrEmpty();
        String url=result.getResponse().getRedirectedUrl();
        mvc.perform(get(url).session(s)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Pedido registrado")));
        mvc.perform(get(url).session(login())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("65.00")));
    }
    @Test void changedPriceRequiresNewReviewAndTokenBeforeAnyOrder() throws Exception {
        var s=login(); add(s,1,2); String old=review(s);
        assertThat(old).isNotBlank(); jdbc.update("UPDATE producto SET precio=30 WHERE id_producto=1");
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",old))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(count("factura")).isZero(); assertThat((List<?>)s.getAttribute("carrito")).hasSize(1);
        String current=review(s); assertThat(current).isNotEqualTo(old);
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",old)).andExpect(status().is3xxRedirection());
        assertThat(count("factura")).isZero();
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",current)).andExpect(status().isSeeOther());
        assertThat(jdbc.queryForObject("SELECT total FROM factura",Double.class)).isEqualTo(60);
    }
    @Test void replayReturnsOriginalAndPreservesNewCart() throws Exception {
        var s=login(); add(s,1,1); var key=review(s); assertThat(key).isNotBlank();
        var first=mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key)).andExpect(status().isSeeOther()).andReturn();
        add(s,3,1); var newer=review(s); assertThat(newer).isNotEqualTo(key);
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key))
            .andExpect(status().isSeeOther()).andExpect(redirectedUrl(first.getResponse().getRedirectedUrl()));
        assertThat(count("factura")).isEqualTo(1); assertThat((List<?>)s.getAttribute("carrito")).hasSize(1);
    }
    @Test void missingReceiptIs404RatherThanExposedOrForbiddenForOwner() throws Exception {
        mvc.perform(get("/carrito/factura/999999").session(login())).andExpect(status().isNotFound());
    }

    @Test void invalidLinesAreNeverSilentlyDiscarded() throws Exception {
        var s=login(); add(s,1,1); add(s,3,1); String key=review(s); assertThat(key).isNotBlank();
        jdbc.update("UPDATE producto SET activo=false WHERE id_producto=1");
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(count("factura")).isZero(); assertThat(count("venta")).isZero();
        assertThat((List<?>)s.getAttribute("carrito")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT SUM(existencias) FROM producto_color_talla",Integer.class)).isEqualTo(8);
    }
    @Test void staleRevisionCannotPurchaseChangedCart() throws Exception {
        var s=login(); add(s,1,1); String old=review(s); assertThat(old).isNotBlank(); add(s,3,1);
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",old))
            .andExpect(redirectedUrl("/carrito/ver")).andExpect(flash().attributeExists("error"));
        assertThat(count("factura")).isZero(); assertThat((List<?>)s.getAttribute("carrito")).hasSize(2);
    }
    @Test void productEditWithStaleVariantsCannotRestorePurchasedStock() throws Exception {
        var admin=(MockHttpSession)mvc.perform(formLogin().user("admin@fitforge.example").password("FitForge-demo-2026!"))
            .andReturn().getRequest().getSession(false);
        // Mimic a detached admin form graph captured before another transaction consumes stock.
        var old=products.obtenerProductoPorId(1L);
        jdbc.update("UPDATE producto_color_talla SET existencias=2 WHERE id_producto_color_talla=1");
        old.setDescripcion("Edición ficticia"); products.guardarProducto(old);
        assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1",Integer.class)).isEqualTo(2);
    }
    @Autowired ProductoService products;

    @Test void csrfMethodsResidualRoutesAndErrorDispatchCannotExposeOrder() throws Exception {
        var s=login();add(s,1,1);String key=review(s);
        mvc.perform(post("/carrito/confirmar-pedido").session(s).param("clave",key)).andExpect(status().isForbidden());
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf().useInvalidToken()).param("clave",key)).andExpect(status().isForbidden());
        mvc.perform(get("/carrito/confirmar-pedido").session(s)).andExpect(status().isForbidden());
        mvc.perform(post("/carrito/factura/1").session(s).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/pedidos/1").session(s)).andExpect(status().isForbidden());
        assertThat(count("factura")).isZero();
        String url=mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key))
            .andExpect(status().isSeeOther()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(url)).andExpect(redirectedUrl("http://localhost/login"));
        var admin=(MockHttpSession)mvc.perform(formLogin().user("admin@fitforge.example").password("FitForge-demo-2026!"))
            .andReturn().getRequest().getSession(false);
        mvc.perform(get(url).session(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/carrito/confirmar-pedido").session(admin).with(csrf()).param("clave",key)).andExpect(status().isForbidden());
        mvc.perform(get(url).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("otro@fitforge.example").roles("USER")))
            .andExpect(status().isForbidden()); // Unknown identity fails closed before any receipt lookup.
        jdbc.update("INSERT INTO usuario(id_usuario,username,password,nombre,correo,activo) SELECT 3,'otro',password,'Otro ficticio','otro@fitforge.example',true FROM usuario WHERE id_usuario=2");
        try {
            mvc.perform(get(url).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("otro@fitforge.example").roles("USER")))
                .andExpect(status().isNotFound());
            mvc.perform(get(url).with(request->{request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);return request;})
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("otro@fitforge.example").roles("USER")))
                .andExpect(status().isNotFound());
        } finally {jdbc.update("DELETE FROM usuario WHERE id_usuario=3");}
    }
    @Test void realDetailFailureLeavesCartAndTokenForRetryWithoutSuccess() throws Exception {
        var s=login();add(s,1,2);add(s,3,1);String key=review(s);
        try(var c=java.sql.DriverManager.getConnection(System.getenv("DEMO_DB_URL"),"root",System.getenv("FITFORGE_TEST_ROOT_PASSWORD"));var statement=c.createStatement()) {
            statement.execute("CREATE TRIGGER test_http_fail_detail BEFORE INSERT ON venta FOR EACH ROW BEGIN IF NEW.id_producto_color_talla=3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled HTTP detail failure'; END IF; END");
            try {
                mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key)).andExpect(status().isServiceUnavailable());
                assertThat(count("factura")).isZero();assertThat(count("venta")).isZero();
                assertThat((List<?>)s.getAttribute("carrito")).hasSize(2);assertThat(review(s)).isEqualTo(key);
                assertThat(jdbc.queryForObject("SELECT SUM(existencias) FROM producto_color_talla",Integer.class)).isEqualTo(8);
            } finally {statement.execute("DROP TRIGGER test_http_fail_detail");}
        }
        mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key)).andExpect(status().isSeeOther());
        assertThat(count("factura")).isEqualTo(1);
    }
    @Test void twoConcurrentPostsOnOneSessionReturnSameCommittedReceipt() throws Exception {
        var s=login();add(s,1,2);var key=review(s);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
        try {
            var task=(java.util.concurrent.Callable<String>)()->{ready.countDown();assertThat(go.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/carrito/confirmar-pedido").session(s).with(csrf()).param("clave",key))
                    .andExpect(status().isSeeOther()).andReturn().getResponse().getRedirectedUrl();};
            var a=pool.submit(task);var b=pool.submit(task);assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(a.get(20,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(count("factura")).isEqualTo(1);assertThat(count("venta")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1",Integer.class)).isEqualTo(3);
        } finally {pool.shutdownNow();pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS);}
    }
    @Test void twoTabsShareTokenButTwoSessionsKeepSeparateCarts() throws Exception {
        var one=login();var two=login();add(one,1,1);add(two,3,1);
        var first=review(one);assertThat(review(one)).isEqualTo(first);var second=review(two);assertThat(second).isNotEqualTo(first);
        mvc.perform(post("/carrito/confirmar-pedido").session(one).with(csrf()).param("clave",first)).andExpect(status().isSeeOther());
        assertThat((List<?>)two.getAttribute("carrito")).hasSize(1);
        mvc.perform(post("/carrito/confirmar-pedido").session(two).with(csrf()).param("clave",second)).andExpect(status().isSeeOther());
        assertThat(count("factura")).isEqualTo(2);
    }

    @Test void confirmingSessionSerializesMutationAndClearsOnlyCommittedRevision() throws Exception {
        var session=login();add(session,1,2);String key=review(session);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var mutationThread=new java.util.concurrent.atomic.AtomicReference<Thread>();
        try(var lock=java.sql.DriverManager.getConnection(System.getenv("DEMO_DB_URL"),"root",System.getenv("FITFORGE_TEST_ROOT_PASSWORD"));
            var observer=java.sql.DriverManager.getConnection(System.getenv("DEMO_DB_URL"),"root",System.getenv("FITFORGE_TEST_ROOT_PASSWORD"));
            var statement=lock.createStatement();var observations=observer.createStatement()) {
            observations.executeUpdate("UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME='events_transactions_current'");
            lock.setAutoCommit(false);statement.executeQuery("SELECT id_producto FROM producto WHERE id_producto=1 FOR UPDATE").close();
            try {
                var confirm=pool.submit(()->mvc.perform(post("/carrito/confirmar-pedido").session(session).with(csrf()).param("clave",key))
                    .andExpect(status().isSeeOther()).andReturn());
                String isolation=null;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
                while(isolation==null && System.nanoTime()<deadline) {
                    try(var rs=observations.executeQuery("SELECT et.ISOLATION_LEVEL FROM performance_schema.events_transactions_current et JOIN performance_schema.threads th ON th.THREAD_ID=et.THREAD_ID WHERE th.PROCESSLIST_INFO LIKE 'SELECT id_producto, precio, activo%'")) {
                        if(rs.next()) isolation=rs.getString(1);
                    }
                    Thread.sleep(150); // Poll an observed condition; never assume a lock from elapsed time.
                }
                if(isolation==null) {
                    try(var rs=observations.executeQuery("SELECT trx_id,trx_query,trx_isolation_level,trx_state FROM information_schema.innodb_trx")) {
                        while(rs.next()) System.out.println("DIAGNOSTIC trx="+rs.getString(1)+" query="+rs.getString(2)+" isolation="+rs.getString(3)+" state="+rs.getString(4));
                    }
                    try(var rs=observations.executeQuery("SELECT ID,STATE,INFO FROM information_schema.processlist WHERE COMMAND <> 'Sleep'")) {
                        while(rs.next()) System.out.println("DIAGNOSTIC process="+rs.getString(1)+" state="+rs.getString(2)+" query="+rs.getString(3));
                    }
                    Thread.getAllStackTraces().forEach((thread,stack)->{if(thread.getName().contains("pool-")) {System.out.println("DIAGNOSTIC thread="+thread.getName()+" state="+thread.getState());for(var frame:stack)System.out.println(frame);}});
                    System.out.println("DIAGNOSTIC confirmDone="+confirm.isDone());
                    if(confirm.isDone()) System.out.println("DIAGNOSTIC result="+confirm.get().getResponse().getStatus());
                }
                assertThat(isolation).as("effective blocked order transaction isolation").isEqualTo("READ COMMITTED");
                assertThat((List<?>)session.getAttribute("carrito")).hasSize(1);
                assertThat(count("factura")).isZero();
                var add=pool.submit(()->{mutationThread.set(Thread.currentThread());add(session,3,1);return true;});
                deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                while((mutationThread.get()==null || mutationThread.get().getState()!=Thread.State.BLOCKED) && System.nanoTime()<deadline) Thread.yield();
                assertThat(mutationThread.get()).isNotNull();assertThat(mutationThread.get().getState()).isEqualTo(Thread.State.BLOCKED);
                lock.rollback();confirm.get(15,java.util.concurrent.TimeUnit.SECONDS);assertThat(add.get(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var cart=(List<Item>)session.getAttribute("carrito");assertThat(cart).hasSize(1);
                assertThat(cart.getFirst().getProductoColorTalla().getIdProductoColorTalla()).isEqualTo(3L);
                assertThat(count("factura")).isEqualTo(1);
                System.out.println("EVIDENCE HTTP transaction observed READ COMMITTED; cart present before commit; same-session mutation blocked on mutex and preserved after commit");
            }finally{lock.rollback();}
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
    }
}
