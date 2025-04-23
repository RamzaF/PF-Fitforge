package com.fitforge.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fitforge.dao.FacturaDao;
import com.fitforge.domain.Factura;
import com.fitforge.service.FacturaService;

@Service
public class FacturaServiceImpl implements FacturaService {
    @Autowired
    private FacturaDao facturaDao;

    @Override
    public void guardar(Factura factura) {
        facturaDao.save(factura);
    }

    @Override
    public List<Factura> listar() {
        return facturaDao.findAll();
    }

    @Override
    public Factura buscarPorId(Long idFactura) {
        return facturaDao.findById(idFactura).orElse(null);
    }
}
