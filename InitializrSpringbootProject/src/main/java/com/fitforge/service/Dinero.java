package com.fitforge.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Dinero {
    private Dinero() {}
    public static BigDecimal desdeDouble(double value) {
        if (!Double.isFinite(value) || value < 0) throw invalid();
        try { return BigDecimal.valueOf(value).setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw invalid(); }
    }
    public static double paraDouble(BigDecimal value) {
        if (value == null || value.signum() < 0) throw invalid();
        double result = value.doubleValue();
        if (desdeDouble(result).compareTo(value) != 0) throw invalid();
        return result;
    }
    private static PedidoException invalid() {
        return new PedidoException("PRECIO_INVALIDO", "El importe no se puede registrar con precisión de centavos.");
    }
}
