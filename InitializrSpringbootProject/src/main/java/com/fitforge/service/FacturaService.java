package com.fitforge.service;

import java.util.List;

import com.fitforge.domain.Factura;

public interface FacturaService {
    void guardar(Factura factura);
    List<Factura> listar();
    Factura buscarPorId(Long idFactura);
}
