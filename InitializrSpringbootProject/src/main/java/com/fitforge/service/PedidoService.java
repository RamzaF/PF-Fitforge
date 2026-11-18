package com.fitforge.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PedidoService {
    // Prices here are the server-issued cart snapshot, never bound from HTTP fields.
    record Linea(Long variante, int cantidad, BigDecimal precioAceptado) {}
    record Comando(UUID clave, List<Linea> lineas, EntregaPedido entrega) {
        public Comando(UUID clave,List<Linea> lineas) {this(clave,lineas,EntregaPedido.demo());}
        public Comando { if (lineas != null) lineas = List.copyOf(lineas); }
    }
    record Confirmacion(Long idFactura, BigDecimal total, boolean reutilizado) {}
    record Detalle(String producto, String color, String talla, int cantidad,
                   BigDecimal precio, BigDecimal subtotal) {}
    record Hito(java.time.LocalDateTime fecha,String origen,String destino) {}
    record Comprobante(Long idFactura, LocalDate fecha, String estado, BigDecimal total, List<Detalle> lineas, EntregaPedido entrega, List<Hito> hitos) {}
    Confirmacion confirmar(Comando comando);
    Comprobante obtenerPropio(Long idFactura);
    Integer propietario();
}
