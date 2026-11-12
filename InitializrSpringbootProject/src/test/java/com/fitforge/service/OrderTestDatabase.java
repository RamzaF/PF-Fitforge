package com.fitforge.service;

/** Destructive fixtures must be explicitly confined to the separate orders test project. */
public final class OrderTestDatabase {
    private OrderTestDatabase() {}
    public static void requireDisposable() {
        if (!"1".equals(System.getenv("FITFORGE_ORDERS_TESTS"))
                || !System.getenv().getOrDefault("FITFORGE_TEST_PROJECT", "").startsWith("fitforge-orders-tests-")
                || System.getenv("FITFORGE_TEST_ROOT_PASSWORD") == null
                || !System.getenv().getOrDefault("DEMO_DB_URL", "").startsWith("jdbc:mysql://db:3306/fitforge_demo?")) {
            throw new IllegalStateException("Orders fixtures require the disposable orders Compose test service; never run against a review demo.");
        }
    }
}
