package com.fitforge.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import static org.assertj.core.api.Assertions.assertThat;

// A real servlet container is needed: MockMvc does not perform Tomcat ERROR dispatches.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHttpErrorIntegrationTest {
    @Autowired TestRestTemplate http;

    @Test void missingPublicResourceKeeps404InsteadOfRedirectingToLogin() {
        var response = http.getForEntity("/uploads/missing-resource.png", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired com.fitforge.service.PedidoService orders;

    java.net.http.HttpClient client() {
        return java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL))
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
    }
    java.net.http.HttpResponse<String> get(java.net.http.HttpClient c,String path) throws Exception {
        return c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
    void login(java.net.http.HttpClient c,String name) throws Exception {
        var page=get(c,"/login");
        var token=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        assertThat(token.find()).isTrue();
        var payload="username="+java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8)+"&password=FitForge-demo-2026%21&_csrf="+java.net.URLEncoder.encode(token.group(1),java.nio.charset.StandardCharsets.UTF_8);
        var result=c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/login"))
            .header("Content-Type","application/x-www-form-urlencoded").POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(302);
        assertThat(result.headers().firstValue("location").orElse("")).doesNotContain("error");
    }
    @Test void actualTomcatErrorDispatchKeepsOtherOwnersReceiptHidden() throws Exception {
        com.fitforge.service.OrderTestDatabase.requireDisposable();
        jdbc.update("INSERT INTO usuario(id_usuario,username,password,nombre,correo,activo) SELECT 88,'error_other',password,'Otro ficticio','error-other@fitforge.example',true FROM usuario WHERE id_usuario=2");
        jdbc.update("INSERT INTO rol(nombre,id_usuario) VALUES('ROLE_USER',88)");
        Long id=null;
        try {
            var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("cliente@fitforge.example","unused",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))) ;
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            id=orders.confirmar(new com.fitforge.service.PedidoService.Comando(java.util.UUID.randomUUID(),java.util.List.of(new com.fitforge.service.PedidoService.Linea(1L,1,new java.math.BigDecimal("25.00"))))).idFactura();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            String path="/carrito/factura/"+id;
            var owner=client();login(owner,"cliente@fitforge.example");assertThat(get(owner,path).body()).contains("Pedido registrado");
            var other=client();login(other,"error-other@fitforge.example");var hidden=get(other,path);
            assertThat(hidden.statusCode()).isEqualTo(404);assertThat(hidden.body()).doesNotContain("Pedido registrado","Camiseta deportiva");
            var absent=get(other,"/carrito/factura/999999");assertThat(absent.statusCode()).isEqualTo(404);assertThat(absent.body()).doesNotContain("Pedido registrado");
            var admin=client();login(admin,"admin@fitforge.example");assertThat(get(admin,path).statusCode()).isEqualTo(403);
            assertThat(get(client(),path).statusCode()).isEqualTo(302);
        }finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            if(id!=null) {jdbc.update("DELETE FROM pedido_evento WHERE id_factura=?",id);jdbc.update("DELETE FROM venta WHERE id_factura=?",id);jdbc.update("DELETE FROM factura WHERE id_factura=?",id);jdbc.update("UPDATE producto_color_talla SET existencias=existencias+1 WHERE id_producto_color_talla=1");}
            jdbc.update("DELETE FROM rol WHERE id_usuario=88");jdbc.update("DELETE FROM usuario WHERE id_usuario=88");
        }
    }
}
