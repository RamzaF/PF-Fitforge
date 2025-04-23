package com.fitforge.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitforge.domain.Venta;

public interface VentaDao extends JpaRepository<Venta, Long> {
}
