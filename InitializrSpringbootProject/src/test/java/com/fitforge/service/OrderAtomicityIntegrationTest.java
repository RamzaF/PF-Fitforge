package com.fitforge.service;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

// No test transaction: fixtures and order commits are visible on independent connections.
@SpringBootTest(properties="spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1")
class OrderAtomicityIntegrationTest {
    @Autowired PedidoService orders;
    @Autowired ProductoService products;
    @Autowired ProductoColorTallaService variants;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource data;

    @BeforeEach void setup() throws Exception {
        OrderTestDatabase.requireDisposable();
        try(var c=root(); var s=c.createStatement()) { s.execute("DROP TRIGGER IF EXISTS test_fail_detail"); }
        jdbc.update("DELETE FROM pedido_evento"); jdbc.update("DELETE FROM venta"); jdbc.update("DELETE FROM factura");
        jdbc.update("DELETE FROM rol WHERE id_usuario>=3"); jdbc.update("DELETE FROM usuario WHERE id_usuario>=3");
        jdbc.update("UPDATE usuario SET activo=true,correo='cliente@fitforge.example' WHERE id_usuario=2");
        jdbc.update("INSERT INTO usuario(id_usuario,username,password,nombre,correo,activo) VALUES(3,'test_other','unused','Otro ficticio','otro@fitforge.example',true)");
        jdbc.update("INSERT INTO rol(nombre,id_usuario) VALUES('ROLE_USER',3)");
        jdbc.update("UPDATE producto SET activo=true,precio=CASE id_producto WHEN 1 THEN 25 ELSE 15 END,descripcion=CASE id_producto WHEN 1 THEN 'Camiseta deportiva' ELSE 'Guantes de entrenamiento' END");
        jdbc.update("INSERT IGNORE INTO producto_color_talla(id_producto_color_talla,id_producto,id_color,id_talla,existencias) VALUES(1,1,1,1,5)");
        jdbc.update("UPDATE producto_color_talla SET existencias=CASE id_producto_color_talla WHEN 1 THEN 5 WHEN 2 THEN 0 ELSE 3 END");
    }
    @AfterEach void cleanup() throws Exception {
        OrderTestDatabase.requireDisposable();
        try(var c=root();var s=c.createStatement()) { s.execute("DROP TRIGGER IF EXISTS test_fail_detail"); }
        jdbc.update("DELETE FROM pedido_evento"); jdbc.update("DELETE FROM venta");jdbc.update("DELETE FROM factura");
        jdbc.update("DELETE FROM rol WHERE id_usuario>=3");jdbc.update("DELETE FROM usuario WHERE id_usuario>=3");
        jdbc.update("UPDATE usuario SET activo=true,correo='cliente@fitforge.example' WHERE id_usuario=2");
        jdbc.update("UPDATE producto SET activo=true,precio=CASE id_producto WHEN 1 THEN 25 ELSE 15 END,descripcion=CASE id_producto WHEN 1 THEN 'Camiseta deportiva' ELSE 'Guantes de entrenamiento' END");
        jdbc.update("INSERT IGNORE INTO producto_color_talla(id_producto_color_talla,id_producto,id_color,id_talla,existencias) VALUES(1,1,1,1,5)");
        jdbc.update("UPDATE producto_color_talla SET existencias=CASE id_producto_color_talla WHEN 1 THEN 5 WHEN 2 THEN 0 ELSE 3 END");
        SecurityContextHolder.clearContext();
    }
    Connection root() throws SQLException {
        String secret=System.getenv("FITFORGE_TEST_ROOT_PASSWORD");
        if(secret==null) throw new IllegalStateException("Only the disposable orders test runner may provide FITFORGE_TEST_ROOT_PASSWORD");
        return DriverManager.getConnection(System.getenv("DEMO_DB_URL"),"root",secret);
    }
    <T> T as(String email, String role, Supplier<T> action) {
        var ctx=SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(email,"unused",List.of(new SimpleGrantedAuthority(role))));
        SecurityContextHolder.setContext(ctx);try {return action.get();}finally{SecurityContextHolder.clearContext();}
    }
    PedidoService.Linea line(long id,int q,String price) { return new PedidoService.Linea(id,q,new BigDecimal(price)); }
    PedidoService.Comando cmd(PedidoService.Linea... ls) { return new PedidoService.Comando(UUID.randomUUID(),List.of(ls)); }
    PedidoService.Confirmacion buy(PedidoService.Comando c) {return as("cliente@fitforge.example","ROLE_USER",()->orders.confirmar(c));}
    int scalar(String sql) throws SQLException {
        try(var c=data.getConnection();var s=c.createStatement();var rs=s.executeQuery(sql)) {rs.next();return rs.getInt(1);}
    }
    void unchanged() throws SQLException {
        assertThat(scalar("SELECT COUNT(*) FROM factura")).isZero();assertThat(scalar("SELECT COUNT(*) FROM venta")).isZero();
        assertThat(scalar("SELECT SUM(existencias) FROM producto_color_talla")).isEqualTo(8);
    }
    @Test void successAndSnapshotRemainAfterCatalogPriceChanges() throws Exception {
        var result=buy(cmd(line(3,1,"15"),line(1,2,"25")));
        assertThat(result.total()).isEqualByComparingTo("65");
        assertThat(scalar("SELECT COUNT(*) FROM factura WHERE id_usuario=2 AND estado='Pendiente' AND LENGTH(huella_carrito)=64")).isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM venta")).isEqualTo(2);
        assertThat(scalar("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1")).isEqualTo(3);
        jdbc.update("UPDATE producto SET precio=99 WHERE id_producto=1");
        var receipt=as("cliente@fitforge.example","ROLE_USER",()->orders.obtenerPropio(result.idFactura()));
        assertThat(receipt.total()).isEqualByComparingTo("65");
        assertThat(receipt.lineas()).extracting(PedidoService.Detalle::precio).contains(new BigDecimal("25.00"),new BigDecimal("15.00"));
        System.out.println("EVIDENCE committed header=1 lines=2 owner=2 total=65 stock1=3 stock3=2; independent connection verified");
    }
    @Test void emptyInvalidAndOverflowNeverWrite() throws Exception {
        for(var c:List.of(cmd(),cmd(line(1,0,"25")),cmd(line(1,-1,"25")),cmd(line(999999,1,"25")),cmd(line(1,Integer.MAX_VALUE,"25"),line(1,1,"25"))))
            assertThatThrownBy(()->buy(c)).isInstanceOf(PedidoException.class);
        unchanged();
    }
    @ParameterizedTest @ValueSource(strings={"stock","inactive","deleted","fraction","price"})
    void changedAvailabilityOrPriceRejectsWholeOrder(String change) throws Exception {
        switch(change) {
            case "stock" -> jdbc.update("UPDATE producto_color_talla SET existencias=1 WHERE id_producto_color_talla=1");
            case "inactive" -> jdbc.update("UPDATE producto SET activo=false WHERE id_producto=1");
            case "deleted" -> jdbc.update("DELETE FROM producto_color_talla WHERE id_producto_color_talla=1");
            case "fraction" -> jdbc.update("UPDATE producto SET precio=25.001 WHERE id_producto=1");
            case "price" -> jdbc.update("UPDATE producto SET precio=30 WHERE id_producto=1");
        }
        int stock=scalar("SELECT SUM(existencias) FROM producto_color_talla");
        assertThatThrownBy(()->buy(cmd(line(1,2,"25"),line(3,1,"15")))).isInstanceOf(PedidoException.class);
        assertThat(scalar("SELECT COUNT(*) FROM factura")).isZero();assertThat(scalar("SELECT COUNT(*) FROM venta")).isZero();
        assertThat(scalar("SELECT SUM(existencias) FROM producto_color_talla")).isEqualTo(stock);
    }
    @Test void detailFailureAfterFirstDetailAndBothDecrementsRollsEverythingBack() throws Exception {
        try(var c=root();var s=c.createStatement()) {
            s.execute("CREATE TRIGGER test_fail_detail BEFORE INSERT ON venta FOR EACH ROW BEGIN IF NEW.id_producto_color_talla=3 AND (SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1)=3 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled detail failure after debit'; END IF; END");
        }
        var command=cmd(line(1,2,"25"),line(3,1,"15"));
        assertThatThrownBy(()->buy(command)).isInstanceOf(RuntimeException.class).hasStackTraceContaining("controlled detail failure after debit");
        unchanged();
        try(var c=root();var s=c.createStatement()){s.execute("DROP TRIGGER test_fail_detail");}
        assertThat(buy(command).total()).isEqualByComparingTo("65");
        System.out.println("EVIDENCE real MySQL detail SIGNAL after stock1=3: rollback header/details/JDBC stock; same-key retry committed");
    }
    @Test void sequentialReplayAndDifferentPayloadConflict() throws Exception {
        var c=cmd(line(1,2,"25"));var first=buy(c);var second=buy(c);
        assertThat(second.idFactura()).isEqualTo(first.idFactura());assertThat(second.reutilizado()).isTrue();
        assertThatThrownBy(()->buy(new PedidoService.Comando(c.clave(),List.of(line(1,1,"25"))))).isInstanceOf(PedidoException.class);
        assertThat(scalar("SELECT COUNT(*) FROM factura")).isEqualTo(1);
        assertThat(scalar("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1")).isEqualTo(3);
    }
    List<Object> race(Callable<Object> a,Callable<Object> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try {
            List<Future<Object>> futures=new ArrayList<>();
            for(var task:List.of(a,b)) futures.add(pool.submit(()->{ready.countDown();assertThat(go.await(5,TimeUnit.SECONDS)).isTrue();try{return task.call();}catch(Exception e){return e;}}));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void concurrentSameKeyAcrossConnectionsHasOneCommit() throws Exception {
        var c=cmd(line(1,2,"25"));var results=race(()->buy(c),()->buy(c));
        assertThat(results).allMatch(x->x instanceof PedidoService.Confirmacion);
        assertThat(((PedidoService.Confirmacion)results.get(0)).idFactura()).isEqualTo(((PedidoService.Confirmacion)results.get(1)).idFactura());
        assertThat(scalar("SELECT COUNT(*) FROM factura")).isEqualTo(1);assertThat(scalar("SELECT COUNT(*) FROM venta")).isEqualTo(1);
        assertThat(scalar("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1")).isEqualTo(3);
        System.out.println("EVIDENCE simultaneous same owner/key, 2 connections: header=1 detail=1 stock=3");
    }
    @Test void twoOwnersCompeteForLastUnitWithoutNegativeStock() throws Exception {
        jdbc.update("UPDATE producto_color_talla SET existencias=1 WHERE id_producto_color_talla=1");
        var results=race(()->buy(cmd(line(1,1,"25"))),()->as("otro@fitforge.example","ROLE_USER",()->orders.confirmar(cmd(line(1,1,"25")))));
        assertThat(results.stream().filter(x->x instanceof PedidoService.Confirmacion).count()).isEqualTo(1);
        assertThat(results.stream().filter(x->x instanceof PedidoException).count()).isEqualTo(1);
        assertThat(scalar("SELECT COUNT(*) FROM factura")).isEqualTo(1);
        assertThat(scalar("SELECT MIN(existencias) FROM producto_color_talla")).isZero();
        System.out.println("EVIDENCE last unit, owners 2 and 3, independent commits: successes=1 failures=1 stock=0");
    }
    @Test void inverseVariantOrderUsesConsistentLocks() throws Exception {
        var results=race(()->buy(cmd(line(1,1,"25"),line(3,1,"15"))),()->as("otro@fitforge.example","ROLE_USER",()->orders.confirmar(cmd(line(3,1,"15"),line(1,1,"25")))));
        assertThat(results).allMatch(x->x instanceof PedidoService.Confirmacion);
        assertThat(scalar("SELECT COUNT(*) FROM factura")).isEqualTo(2);assertThat(scalar("SELECT COUNT(*) FROM venta")).isEqualTo(4);
    }
    @Test void concurrentStaleAdminProductEditNeverRestoresStock() throws Exception {
        var stale=products.obtenerProductoPorId(1L);stale.setDescripcion("Edición ficticia");
        var results=race(()->buy(cmd(line(1,2,"25"))),()->{products.guardarProducto(stale);return "updated";});
        assertThat(results).noneMatch(x->x instanceof Exception);
        assertThat(scalar("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1")).isEqualTo(3);
    }
    @Test void variantExplicitAdjustmentAndOrderSerializeAndSoldVariantCannotBeDeleted() throws Exception {
        var variant=variants.getProductoColorTalla(1L);variant.setExistencias(4);
        var results=race(()->buy(cmd(line(1,1,"25"))),()->{variants.guardar(variant);return "adjusted";});
        assertThat(results).noneMatch(x->x instanceof Exception);
        assertThat(scalar("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1")).isIn(3,4);
        assertThatThrownBy(()->variants.eliminar(1L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(scalar("SELECT COUNT(*) FROM venta")).isEqualTo(1);
    }
    @Test void lockTimeoutRollsBackAndSameKeyRetrySucceeds() throws Exception {
        var c=cmd(line(1,2,"25"));
        try(var lock=root();var s=lock.createStatement()) {
            lock.setAutoCommit(false);s.executeQuery("SELECT id_producto FROM producto WHERE id_producto=1 FOR UPDATE").close();
            var pool=Executors.newSingleThreadExecutor();
            try { var f=pool.submit(()->{try {buy(c);return (Object)"unexpected success";}catch(Exception e){return (Object)e;}});
                assertThat(f.get(15,TimeUnit.SECONDS)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            } finally {pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);lock.rollback();}
        }
        unchanged();assertThat(buy(c).reutilizado()).isFalse();
        System.out.println("EVIDENCE real row lock timeout -> rollback all; retry same key -> one commit");
    }
    @Test void invalidAmbiguousInactiveAndAdminIdentitiesFailClosed() throws Exception {
        assertThatThrownBy(()->as("missing@fitforge.example","ROLE_USER",()->orders.confirmar(cmd(line(1,1,"25"))))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->as("admin@fitforge.example","ROLE_ADMIN",()->orders.confirmar(cmd(line(1,1,"25"))))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE usuario SET activo=false WHERE id_usuario=2");
        assertThatThrownBy(()->buy(cmd(line(1,1,"25")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE usuario SET activo=true WHERE id_usuario=2");jdbc.update("UPDATE usuario SET correo='cliente@fitforge.example' WHERE id_usuario=3");
        assertThatThrownBy(()->buy(cmd(line(1,1,"25")))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        unchanged();
    }
    @Test void receiptIsOwnerScopedEvenForServiceCalls() throws Exception {
        var id=buy(cmd(line(1,1,"25"))).idFactura();
        assertThatThrownBy(()->as("otro@fitforge.example","ROLE_USER",()->orders.obtenerPropio(id))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("404");
        assertThatThrownBy(()->as("admin@fitforge.example","ROLE_ADMIN",()->orders.obtenerPropio(id))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->orders.obtenerPropio(id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("403");
    }
    @Test void databaseConstraintsEnforceNonnegativeStockAndPersistentUniqueness() throws Exception {
        assertThatThrownBy(()->jdbc.update("UPDATE producto_color_talla SET existencias=-1 WHERE id_producto_color_talla=1")).isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("Check constraint");
        var c=cmd(line(1,1,"25"));buy(c);
        assertThatThrownBy(()->jdbc.update("INSERT INTO factura(id_usuario,fecha,total,estado,clave_confirmacion,huella_carrito,destinatario,telefono_entrega,direccion_entrega,nota_entrega,consentimiento_demo) SELECT id_usuario,fecha,total,estado,clave_confirmacion,huella_carrito,destinatario,telefono_entrega,direccion_entrega,nota_entrega,consentimiento_demo FROM factura LIMIT 1")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test void realDeadlockVictimRollsBackAndSameKeyRecovers() throws Exception {
        var command=cmd(line(1,2,"25"));var pool=Executors.newSingleThreadExecutor();
        try(var blocker=root();var observer=root();var lock=blocker.createStatement();var observe=observer.createStatement()) {
            blocker.setAutoCommit(false);
            lock.executeQuery("SELECT id_producto FROM producto WHERE id_producto=1 FOR UPDATE").close();
            // Give the blocker more undo work so InnoDB normally chooses the small order as victim.
            lock.executeUpdate("UPDATE producto_color_talla SET existencias=existencias+1");
            var future=pool.submit(()->{try{return (Object)buy(command);}catch(Exception e){return (Object)e;}});
            try {
                boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(800);
                while(!waiting && System.nanoTime()<deadline) {
                    try(var rs=observe.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {rs.next();waiting=rs.getInt(1)>0;}
                    Thread.sleep(25); // Condition-based polling of actual DB wait edges.
                }
                assertThat(waiting).isTrue();
                boolean blockerVictim=false;
                try {lock.executeQuery("SELECT id_usuario FROM usuario WHERE id_usuario=2 FOR UPDATE").close();}
                catch(SQLException e) {assertThat(e.getErrorCode()).isEqualTo(1213);blockerVictim=true;}
                blocker.rollback();
                var result=future.get(10,TimeUnit.SECONDS);
                if(!blockerVictim) {assertThat(result).isInstanceOf(org.springframework.dao.DataAccessException.class);unchanged();}
                else assertThat(result).isInstanceOf(PedidoService.Confirmacion.class);
                buy(command);
                assertThat(scalar("SELECT COUNT(*) FROM factura")).isEqualTo(1);assertThat(scalar("SELECT COUNT(*) FROM venta")).isEqualTo(1);
                assertThat(scalar("SELECT existencias FROM producto_color_talla WHERE id_producto_color_talla=1")).isEqualTo(3);
                System.out.println("EVIDENCE actual InnoDB 1213 deadlock; victim rolled back; same-key recovery header=1 stock=3; blockerVictim="+blockerVictim);
            }finally{blocker.rollback();}
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
}
