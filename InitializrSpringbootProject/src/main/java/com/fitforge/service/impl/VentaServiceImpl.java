package com.fitforge.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fitforge.dao.VentaDao;
import com.fitforge.domain.Venta;
import com.fitforge.service.VentaService;

@Service
public class VentaServiceImpl implements VentaService {
    @Autowired
    private VentaDao ventaDao;

    @Override
    public void guardar(Venta venta) {
        ventaDao.save(venta);
    }

    @Override
    public List<Venta> listar() {
        return ventaDao.findAll();
    }

    @Override
    public Venta buscarPorId(Long idVenta) {
        return ventaDao.findById(idVenta).orElse(null);
    }
}
