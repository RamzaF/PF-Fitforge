package com.fitforge.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoInventarioDao {
    private final JdbcTemplate jdbc;
    public PedidoInventarioDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record ProductoActual(long id, double precio, boolean activo) {}
    public record VarianteActual(long id, long producto, int stock, boolean activo) {}
    public Long productoDe(Long variante) {
        var rows = jdbc.query("SELECT id_producto FROM producto_color_talla WHERE id_producto_color_talla=?",
            (rs,n) -> rs.getLong(1), variante);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public ProductoActual bloquearProducto(Long id) {
        var rows = jdbc.query("SELECT id_producto, precio, activo FROM producto WHERE id_producto=? FOR UPDATE",
            (rs,n) -> new ProductoActual(rs.getLong(1),rs.getDouble(2),rs.getBoolean(3)), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public boolean categoriaActivaDeProducto(Long idProducto) {
        var rows = jdbc.query(
            "SELECT c.activo FROM producto p JOIN categoria c ON c.id_categoria=p.id_categoria WHERE p.id_producto=?",
            (rs, n) -> rs.getBoolean(1), idProducto);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.getFirst());
    }
    public VarianteActual bloquearVariante(Long id) {
        var rows = jdbc.query("SELECT id_producto_color_talla, id_producto, existencias, activo FROM producto_color_talla WHERE id_producto_color_talla=? FOR UPDATE",
            (rs,n) -> new VarianteActual(rs.getLong(1),rs.getLong(2),rs.getInt(3),rs.getBoolean(4)), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }
    public boolean descontar(Long id, int cantidad) {
        return jdbc.update("UPDATE producto_color_talla SET existencias=existencias-? WHERE id_producto_color_talla=? AND existencias>=? AND activo=true",cantidad,id,cantidad)==1;
    }
}
