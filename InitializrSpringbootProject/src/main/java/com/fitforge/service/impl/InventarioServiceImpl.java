package com.fitforge.service.impl;

import com.fitforge.dao.PedidoInventarioDao;
import com.fitforge.domain.MovimientoInventario;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.service.InventarioService;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventarioServiceImpl implements InventarioService {

    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final PedidoInventarioDao inventario;

    public InventarioServiceImpl(JdbcTemplate jdbc, EntityManager em, PedidoInventarioDao inventario) {
        this.jdbc = jdbc;
        this.em = em;
        this.inventario = inventario;
    }

    private Integer resolverAdminObligatorio() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var rows = jdbc.query(
                "SELECT id_usuario, activo FROM usuario WHERE correo = ?",
                (rs, n) -> new Object[]{rs.getInt(1), rs.getBoolean(2)},
                auth.getName());
        if (rows.size() != 1 || !Boolean.TRUE.equals(rows.getFirst()[1])) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return (Integer) rows.getFirst()[0];
    }

    private Integer resolverAdminSiPresente() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return null;
        }
        var rows = jdbc.query(
                "SELECT id_usuario, activo FROM usuario WHERE correo = ?",
                (rs, n) -> new Object[]{rs.getInt(1), rs.getBoolean(2)},
                auth.getName());
        if (rows.size() != 1 || !Boolean.TRUE.equals(rows.getFirst()[1])) {
            return null;
        }
        return (Integer) rows.getFirst()[0];
    }

    private String validarMotivo(String motivo) {
        if (motivo == null) {
            throw new IllegalArgumentException("El motivo del movimiento es obligatorio.");
        }
        String limpio = motivo.trim().replaceAll("\\s+", " ");
        if (limpio.length() < 3 || limpio.length() > 200) {
            throw new IllegalArgumentException("El motivo debe tener entre 3 y 200 caracteres.");
        }
        return limpio;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 10, rollbackFor = Exception.class)
    public MovimientoView registrarMovimiento(Long idVariante, TipoMovimiento tipo, int cantidad, String motivo) {
        int idAdmin = resolverAdminObligatorio();
        if (idVariante == null || idVariante < 1) {
            throw new IllegalArgumentException("Debe seleccionar una variante válida.");
        }
        if (tipo == null || tipo == TipoMovimiento.ALTA) {
            throw new IllegalArgumentException("Tipo de movimiento inválido.");
        }
        String motivoLimpio = validarMotivo(motivo);
        if (tipo == TipoMovimiento.INGRESO && cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a ingresar debe ser un entero mayor a 0.");
        }
        if (tipo == TipoMovimiento.AJUSTE && cantidad < 0) {
            throw new IllegalArgumentException("El stock ajustado no puede ser negativo.");
        }

        Long pid = inventario.productoDe(idVariante);
        if (pid == null) {
            throw new IllegalArgumentException("La variante solicitada no existe.");
        }
        // Canonical lock order: producto -> variante (matches PedidoServiceImpl to prevent deadlocks).
        var prod = inventario.bloquearProducto(pid);
        var varActual = inventario.bloquearVariante(idVariante);
        if (prod == null || varActual == null || varActual.producto() != pid) {
            throw new IllegalArgumentException("La variante solicitada no existe.");
        }
        if (!varActual.activo()) {
            throw new IllegalArgumentException(
                    "No se puede modificar el stock de una variante inactiva. Activála primero.");
        }

        int anterior = varActual.stock();
        int nueva;
        if (tipo == TipoMovimiento.INGRESO) {
            long suma = (long) anterior + cantidad;
            if (suma > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("El stock resultante excede el límite permitido.");
            }
            nueva = (int) suma;
        } else {
            nueva = cantidad;
        }
        int delta = nueva - anterior;

        em.flush();
        jdbc.update("UPDATE producto_color_talla SET existencias = ? WHERE id_producto_color_talla = ?",
                nueva, idVariante);
        ProductoColorTalla managed = em.find(ProductoColorTalla.class, idVariante);
        if (managed != null) {
            em.refresh(managed);
        }

        LocalDateTime ahora = LocalDateTime.now();
        MovimientoInventario mov = new MovimientoInventario();
        mov.setIdProductoColorTalla(idVariante);
        mov.setIdUsuarioAdmin(idAdmin);
        mov.setTipo(tipo.name());
        mov.setCantidadAnterior(anterior);
        mov.setCantidadDelta(delta);
        mov.setCantidadNueva(nueva);
        mov.setMotivo(motivoLimpio);
        mov.setFechaCreacion(ahora);
        em.persist(mov);
        em.flush();

        var detalles = jdbc.query(
                "SELECT m.id_movimiento, m.id_producto_color_talla, p.id_producto, p.descripcion, "
                + "COALESCE(c.nombre_color, 'N/A'), COALESCE(t.nombre_talla, 'N/A'), "
                + "m.tipo, m.cantidad_anterior, m.cantidad_delta, m.cantidad_nueva, m.motivo, "
                + "u.nombre, u.correo, m.fecha_creacion "
                + "FROM movimiento_inventario m "
                + "JOIN producto_color_talla pct ON pct.id_producto_color_talla = m.id_producto_color_talla "
                + "JOIN producto p ON p.id_producto = pct.id_producto "
                + "LEFT JOIN color c ON c.id_color = pct.id_color "
                + "LEFT JOIN talla t ON t.id_talla = pct.id_talla "
                + "JOIN usuario u ON u.id_usuario = m.id_usuario_admin "
                + "WHERE m.id_movimiento = ?",
                (rs, n) -> mapMovimiento(rs),
                mov.getIdMovimiento());
        return detalles.getFirst();
    }

    @Override
    @Transactional
    public void registrarAltaInicialSiAplica(Long idVariante, int existencias) {
        if (idVariante == null || existencias <= 0) {
            return;
        }
        Integer idAdmin = resolverAdminSiPresente();
        if (idAdmin == null) {
            return;
        }
        MovimientoInventario mov = new MovimientoInventario();
        mov.setIdProductoColorTalla(idVariante);
        mov.setIdUsuarioAdmin(idAdmin);
        mov.setTipo(TipoMovimiento.ALTA.name());
        mov.setCantidadAnterior(0);
        mov.setCantidadDelta(existencias);
        mov.setCantidadNueva(existencias);
        mov.setMotivo("Alta inicial de variante");
        mov.setFechaCreacion(LocalDateTime.now());
        em.persist(mov);
        em.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VarianteInventarioView> listarInventarioGeneral(String termino, String filtroStock) {
        StringBuilder sql = new StringBuilder(
                "SELECT pct.id_producto_color_talla, p.id_producto, p.descripcion, "
                + "COALESCE(cat.descripcion, 'Sin categoría'), "
                + "COALESCE(c.nombre_color, 'Sin color'), "
                + "COALESCE(t.nombre_talla, 'Única'), "
                + "pct.existencias, pct.activo, p.activo, p.imagen "
                + "FROM producto_color_talla pct "
                + "JOIN producto p ON p.id_producto = pct.id_producto "
                + "LEFT JOIN categoria cat ON cat.id_categoria = p.id_categoria "
                + "LEFT JOIN color c ON c.id_color = pct.id_color "
                + "LEFT JOIN talla t ON t.id_talla = pct.id_talla "
                + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (termino != null && !termino.isBlank()) {
            String like = "%" + termino.trim().toLowerCase(Locale.ROOT) + "%";
            sql.append("AND (LOWER(p.descripcion) LIKE ? OR LOWER(COALESCE(cat.descripcion,'')) LIKE ? "
                    + "OR LOWER(COALESCE(c.nombre_color,'')) LIKE ? OR LOWER(COALESCE(t.nombre_talla,'')) LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if ("agotado".equalsIgnoreCase(filtroStock)) {
            sql.append("AND pct.existencias = 0 ");
        } else if ("bajo".equalsIgnoreCase(filtroStock)) {
            sql.append("AND pct.existencias > 0 AND pct.existencias <= 5 ");
        }
        sql.append("ORDER BY p.descripcion ASC, pct.id_producto_color_talla ASC");
        return jdbc.query(sql.toString(), (rs, n) -> mapVariante(rs), params.toArray());
    }

    @Override
    @Transactional(readOnly = true)
    public List<VarianteInventarioView> listarInventarioDeProducto(Long idProducto) {
        if (idProducto == null) {
            return List.of();
        }
        return jdbc.query(
                "SELECT pct.id_producto_color_talla, p.id_producto, p.descripcion, "
                + "COALESCE(cat.descripcion, 'Sin categoría'), "
                + "COALESCE(c.nombre_color, 'Sin color'), "
                + "COALESCE(t.nombre_talla, 'Única'), "
                + "pct.existencias, pct.activo, p.activo, p.imagen "
                + "FROM producto_color_talla pct "
                + "JOIN producto p ON p.id_producto = pct.id_producto "
                + "LEFT JOIN categoria cat ON cat.id_categoria = p.id_categoria "
                + "LEFT JOIN color c ON c.id_color = pct.id_color "
                + "LEFT JOIN talla t ON t.id_talla = pct.id_talla "
                + "WHERE p.id_producto = ? "
                + "ORDER BY pct.id_producto_color_talla ASC",
                (rs, n) -> mapVariante(rs),
                idProducto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimientoView> listarMovimientosRecientes(int limite) {
        int safeLimit = Math.max(1, Math.min(limite, 200));
        return jdbc.query(
                "SELECT m.id_movimiento, m.id_producto_color_talla, p.id_producto, p.descripcion, "
                + "COALESCE(c.nombre_color, 'N/A'), COALESCE(t.nombre_talla, 'N/A'), "
                + "m.tipo, m.cantidad_anterior, m.cantidad_delta, m.cantidad_nueva, m.motivo, "
                + "u.nombre, u.correo, m.fecha_creacion "
                + "FROM movimiento_inventario m "
                + "JOIN producto_color_talla pct ON pct.id_producto_color_talla = m.id_producto_color_talla "
                + "JOIN producto p ON p.id_producto = pct.id_producto "
                + "LEFT JOIN color c ON c.id_color = pct.id_color "
                + "LEFT JOIN talla t ON t.id_talla = pct.id_talla "
                + "JOIN usuario u ON u.id_usuario = m.id_usuario_admin "
                + "ORDER BY m.id_movimiento DESC LIMIT ?",
                (rs, n) -> mapMovimiento(rs),
                safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimientoView> listarMovimientosDeProducto(Long idProducto, int limite) {
        if (idProducto == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limite, 200));
        return jdbc.query(
                "SELECT m.id_movimiento, m.id_producto_color_talla, p.id_producto, p.descripcion, "
                + "COALESCE(c.nombre_color, 'N/A'), COALESCE(t.nombre_talla, 'N/A'), "
                + "m.tipo, m.cantidad_anterior, m.cantidad_delta, m.cantidad_nueva, m.motivo, "
                + "u.nombre, u.correo, m.fecha_creacion "
                + "FROM movimiento_inventario m "
                + "JOIN producto_color_talla pct ON pct.id_producto_color_talla = m.id_producto_color_talla "
                + "JOIN producto p ON p.id_producto = pct.id_producto "
                + "LEFT JOIN color c ON c.id_color = pct.id_color "
                + "LEFT JOIN talla t ON t.id_talla = pct.id_talla "
                + "JOIN usuario u ON u.id_usuario = m.id_usuario_admin "
                + "WHERE p.id_producto = ? "
                + "ORDER BY m.id_movimiento DESC LIMIT ?",
                (rs, n) -> mapMovimiento(rs),
                idProducto, safeLimit);
    }

    private VarianteInventarioView mapVariante(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VarianteInventarioView(
                rs.getLong(1),
                rs.getLong(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getInt(7),
                rs.getBoolean(8),
                rs.getBoolean(9),
                rs.getString(10));
    }

    private MovimientoView mapMovimiento(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(14);
        return new MovimientoView(
                rs.getLong(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getInt(8),
                rs.getInt(9),
                rs.getInt(10),
                rs.getString(11),
                rs.getString(12),
                rs.getString(13),
                ts != null ? ts.toLocalDateTime() : LocalDateTime.now());
    }
}
