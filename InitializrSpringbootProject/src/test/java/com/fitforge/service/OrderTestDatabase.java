package com.fitforge.service;

/** Destructive fixtures must be explicitly confined to the separate orders test project. */
public final class OrderTestDatabase {
    private OrderTestDatabase() {}
    public static org.springframework.test.web.servlet.request.RequestPostProcessor entregaDemo() {
        return request->{
            request.setParameter("destinatario","Persona ficticia");request.setParameter("telefonoEntrega","000-000-0000");
            request.setParameter("direccionEntrega","Calle Demo 123, Ciudad Ficticia");request.setParameter("notaEntrega","Solo demostración");
            request.setParameter("consentimiento","true");return request;
        };
    }
    public static void requireDisposable() {
        if (!"1".equals(System.getenv("FITFORGE_ORDERS_TESTS"))
                || !System.getenv().getOrDefault("FITFORGE_TEST_PROJECT", "").startsWith("fitforge-orders-tests-")
                || System.getenv("FITFORGE_TEST_ROOT_PASSWORD") == null
                || !System.getenv().getOrDefault("DEMO_DB_URL", "").startsWith("jdbc:mysql://db:3306/fitforge_demo?")) {
            throw new IllegalStateException("Orders fixtures require the disposable orders Compose test service; never run against a review demo.");
        }
    }
}
