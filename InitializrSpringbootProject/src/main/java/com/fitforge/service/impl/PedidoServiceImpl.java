package com.fitforge.service.impl;

import com.fitforge.service.*;
import com.fitforge.dao.*;
import com.fitforge.domain.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoServiceImpl implements PedidoService {
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final PedidoInventarioDao inventario;
    public PedidoServiceImpl(JdbcTemplate jdbc, EntityManager em, PedidoInventarioDao inventario) {
        this.jdbc=jdbc; this.em=em; this.inventario=inventario;
    }
    @Override public Integer propietario() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_USER"))
            || auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var rows=jdbc.query("SELECT id_usuario, activo FROM usuario WHERE correo=?",(rs,n)->new Object[]{rs.getInt(1),rs.getBoolean(2)},auth.getName());
        if(rows.size()!=1 || !Boolean.TRUE.equals(rows.getFirst()[1])) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return (Integer)rows.getFirst()[0];
    }
    private PedidoException error(String code,String message) { return new PedidoException(code,message); }
    private List<Linea> normalizar(Comando c) {
        if(c==null || c.clave()==null || c.lineas()==null || c.lineas().isEmpty()) throw error("CARRITO_VACIO","El carrito está vacío.");
        var lines=new TreeMap<Long,Linea>();
        for(var l:c.lineas()) {
            if(l==null || l.variante()==null || l.variante()<1 || l.cantidad()<1) throw error("CANTIDAD_INVALIDA","Revisá las cantidades del carrito.");
            Dinero.paraDouble(l.precioAceptado());
            var old=lines.get(l.variante());
            long quantity=(long)l.cantidad()+(old==null?0:old.cantidad());
            if(quantity>Integer.MAX_VALUE || (old!=null && old.precioAceptado().compareTo(l.precioAceptado())!=0))
                throw error("CANTIDAD_INVALIDA","Revisá las cantidades del carrito.");
            lines.put(l.variante(),new Linea(l.variante(),(int)quantity,l.precioAceptado().setScale(2)));
        }
        return List.copyOf(lines.values());
    }
    private String huella(List<Linea> lines) {
        var text=new StringBuilder("v2");
        for(var l:lines) text.append('|').append(l.variante()).append(':').append(l.cantidad()).append(':').append(l.precioAceptado().toPlainString());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Override
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED,timeout=10,rollbackFor=Exception.class)
    public Confirmacion confirmar(Comando c) {
        int owner=propietario(); var lines=normalizar(c); String fingerprint=huella(lines);
        // Serialize this owner's keys across connections. READ COMMITTED and current reads avoid missing-key gap locks.
        var current=jdbc.query("SELECT activo, correo FROM usuario WHERE id_usuario=? FOR UPDATE",(rs,n)->new Object[]{rs.getBoolean(1),rs.getString(2)},owner);
        if(current.isEmpty() || !Boolean.TRUE.equals(current.getFirst()[0]) || !current.getFirst()[1].equals(SecurityContextHolder.getContext().getAuthentication().getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var previous=jdbc.query("SELECT id_factura,total,huella_carrito FROM factura WHERE id_usuario=? AND clave_confirmacion=? FOR UPDATE",
            (rs,n)->new Object[]{rs.getLong(1),rs.getDouble(2),rs.getString(3)},owner,c.clave().toString());
        if(!previous.isEmpty()) {
            var row=previous.getFirst();
            if(!fingerprint.equals(row[2])) throw error("CONFIRMACION_CONFLICTIVA","La confirmación no corresponde a ese carrito.");
            return new Confirmacion((Long)row[0],Dinero.desdeDouble((Double)row[1]),true);
        }
        var productIds=new TreeSet<Long>(); var associations=new HashMap<Long,Long>();
        for(var l:lines) {
            Long pid=inventario.productoDe(l.variante());
            if(pid==null) throw error("VARIANTE_NO_DISPONIBLE","Hay líneas no disponibles. Revisá el carrito.");
            productIds.add(pid); associations.put(l.variante(),pid);
        }
        var products=new HashMap<Long,PedidoInventarioDao.ProductoActual>();
        for(Long pid:productIds) products.put(pid,inventario.bloquearProducto(pid));
        var prices=new HashMap<Long,BigDecimal>(); BigDecimal total=new BigDecimal("0.00");
        for(var l:lines) {
            var v=inventario.bloquearVariante(l.variante()); var p=products.get(associations.get(l.variante()));
            if(v==null || p==null || v.producto()!=p.id() || !p.activo()) throw error("VARIANTE_NO_DISPONIBLE","Hay líneas no disponibles. Revisá el carrito.");
            if(v.stock()<l.cantidad()) throw error("STOCK_INSUFICIENTE","El stock cambió. Revisá las cantidades del carrito.");
            var price=Dinero.desdeDouble(p.precio());
            if(price.compareTo(l.precioAceptado())!=0) throw error("PRECIO_CAMBIADO","El precio cambió. Revisá el total actual y confirmá de nuevo.");
            prices.put(l.variante(),price); total=total.add(price.multiply(BigDecimal.valueOf(l.cantidad())));
        }
        double storedTotal=Dinero.paraDouble(total);
        var factura=new Factura(); factura.setIdUsuario(owner); factura.setClaveConfirmacion(c.clave().toString());
        factura.setHuellaCarrito(fingerprint); factura.setFecha(Date.valueOf(LocalDate.now())); factura.setEstado("Pendiente"); factura.setTotal(storedTotal);
        em.persist(factura);
        for(var l:lines) {
            if(!inventario.descontar(l.variante(),l.cantidad())) throw error("STOCK_INSUFICIENTE","El stock cambió. Revisá el carrito.");
            var venta=new Venta(); venta.setFactura(factura); venta.setProductoColorTalla(em.getReference(ProductoColorTalla.class,l.variante()));
            venta.setCantidad(l.cantidad()); venta.setPrecio(Dinero.paraDouble(prices.get(l.variante()))); em.persist(venta);
        }
        em.flush();
        return new Confirmacion(factura.getIdFactura(),total,false);
    }
    @Override
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public Comprobante obtenerPropio(Long id) {
        int owner=propietario();
        var headers=jdbc.query("SELECT id_factura,fecha,estado,total FROM factura WHERE id_factura=? AND id_usuario=?",
            (rs,n)->new Comprobante(rs.getLong(1),rs.getDate(2).toLocalDate(),rs.getString(3),Dinero.desdeDouble(rs.getDouble(4)),List.of()),id,owner);
        if(headers.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var lines=jdbc.query("SELECT p.descripcion,c.nombre_color,t.nombre_talla,v.cantidad,v.precio FROM venta v JOIN producto_color_talla pct ON v.id_producto_color_talla=pct.id_producto_color_talla JOIN producto p ON p.id_producto=pct.id_producto LEFT JOIN color c ON c.id_color=pct.id_color LEFT JOIN talla t ON t.id_talla=pct.id_talla WHERE v.id_factura=? ORDER BY v.id_venta",
            (rs,n)->{ var price=Dinero.desdeDouble(rs.getDouble(5)); int quantity=rs.getInt(4); return new Detalle(rs.getString(1),Objects.requireNonNullElse(rs.getString(2),"N/A"),Objects.requireNonNullElse(rs.getString(3),"N/A"),quantity,price,price.multiply(BigDecimal.valueOf(quantity))); },id);
        var h=headers.getFirst();return new Comprobante(h.idFactura(),h.fecha(),h.estado(),h.total(),List.copyOf(lines));
    }
}
