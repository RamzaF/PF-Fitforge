package com.fitforge.domain;

import java.io.Serializable;
import java.sql.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "factura")
public class Factura implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura")
    private Long idFactura;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "clave_confirmacion", nullable = false, length = 36)
    private String claveConfirmacion;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "huella_carrito", nullable = false, length = 64)
    private String huellaCarrito;

    @Column(name = "fecha")
    private Date fecha;

    @Column(name = "total")
    private Double total;

    @Column(name = "estado")
    private String estado;
}
