package com.fitforge.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitforge.domain.Factura;

public interface FacturaDao extends JpaRepository<Factura, Long> {
}
