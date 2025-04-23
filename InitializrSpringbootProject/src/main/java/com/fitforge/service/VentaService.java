package com.fitforge.service;

import java.util.List;

import com.fitforge.domain.Venta;

public interface VentaService {
    void guardar(Venta venta);
    List<Venta> listar();
    Venta buscarPorId(Long idVenta);
}
