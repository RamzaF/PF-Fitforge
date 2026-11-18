package com.fitforge.service;

public class PedidoException extends RuntimeException {
    private final String codigo;
    public PedidoException(String codigo, String mensaje) { super(mensaje); this.codigo = codigo; }
    public String codigo() { return codigo; }
}
