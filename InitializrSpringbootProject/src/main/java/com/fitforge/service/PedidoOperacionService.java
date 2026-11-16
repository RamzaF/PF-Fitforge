package com.fitforge.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoOperacionService {
    public static final List<String> ESTADOS=List.of("Pendiente","En preparación","Despachado");
    public record Resumen(long id, int propietario, String correo, LocalDate fecha, String estado, BigDecimal total) {}
    public record Pagina(List<Resumen> pedidos, long total, int pagina, Map<String,Long> conteos) {
        public boolean anterior() {return pagina>0;}
        public boolean siguiente() {return ((long)pagina+1)*20<total;}
    }
    public record Evento(long id, String actor, java.time.LocalDateTime fecha, String origen, String destino) {}
    public record Detalle(Resumen pedido, List<PedidoService.Detalle> lineas, List<Evento> eventos, EntregaPedido entrega) {}
    private final JdbcTemplate jdbc;
    private final PedidoService pedidos;
    public PedidoOperacionService(JdbcTemplate jdbc, PedidoService pedidos) {this.jdbc=jdbc;this.pedidos=pedidos;}
    private int admin() {
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null || !a.isAuthenticated() || a.getAuthorities().stream().noneMatch(r->r.getAuthority().equals("ROLE_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var ids=jdbc.query("SELECT u.id_usuario FROM usuario u JOIN rol r ON r.id_usuario=u.id_usuario WHERE u.correo=? AND u.activo=true AND r.nombre='ROLE_ADMIN'",(rs,n)->rs.getInt(1),a.getName());
        if(ids.size()!=1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ids.getFirst();
    }
    private static final String HEADER="SELECT f.id_factura,f.id_usuario,u.correo,f.fecha,f.estado,f.total FROM factura f JOIN usuario u ON u.id_usuario=f.id_usuario";
    private Resumen fila(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
        return new Resumen(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getDate(4).toLocalDate(),rs.getString(5),Dinero.desdeDouble(rs.getDouble(6)));
    }
    @Transactional(readOnly=true)
    public Pagina listar(boolean administrativo, String estado, String busqueda, int pagina) {
        Integer owner=null;
        if(administrativo) admin(); else owner=pedidos.propietario();
        if(pagina<0 || pagina>100000 || (estado!=null && !estado.isBlank() && !ESTADOS.contains(estado)) || (busqueda!=null && busqueda.length()>100))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filtro inválido");
        String where=" WHERE 1=1";var args=new ArrayList<Object>();
        if(owner!=null) {where+=" AND f.id_usuario=?";args.add(owner);}
        if(estado!=null && !estado.isBlank()) {where+=" AND f.estado=?";args.add(estado);}
        if(busqueda!=null && !busqueda.isBlank()) {
            where+=" AND (CAST(f.id_factura AS CHAR)=? OR LOCATE(?,u.correo)>0 OR LOCATE(?,f.destinatario)>0)";
            args.add(busqueda.strip());args.add(busqueda.strip());args.add(busqueda.strip());
        }
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM factura f JOIN usuario u ON u.id_usuario=f.id_usuario"+where,Long.class,args.toArray());
        var counts=new LinkedHashMap<String,Long>();ESTADOS.forEach(e->counts.put(e,0L));
        String countWhere=owner==null?"":" WHERE id_usuario=?";
        jdbc.query("SELECT estado,COUNT(*) FROM factura"+countWhere+" GROUP BY estado",rs->{counts.put(rs.getString(1),rs.getLong(2));},owner==null?new Object[]{}:new Object[]{owner});
        args.add(20);args.add(pagina*20);
        return new Pagina(List.copyOf(jdbc.query(HEADER+where+" ORDER BY f.id_factura DESC LIMIT ? OFFSET ?",this::fila,args.toArray())),total,pagina,Collections.unmodifiableMap(counts));
    }
    @Transactional(readOnly=true)
    public Detalle detalleAdmin(long id) {
        admin();var headers=jdbc.query(HEADER+" WHERE f.id_factura=?",this::fila,id);
        if(headers.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var lines=jdbc.query("SELECT p.descripcion,c.nombre_color,t.nombre_talla,v.cantidad,v.precio FROM venta v JOIN producto_color_talla pct ON pct.id_producto_color_talla=v.id_producto_color_talla JOIN producto p ON p.id_producto=pct.id_producto LEFT JOIN color c ON c.id_color=pct.id_color LEFT JOIN talla t ON t.id_talla=pct.id_talla WHERE v.id_factura=? ORDER BY v.id_venta",(rs,n)->{
            var price=Dinero.desdeDouble(rs.getDouble(5));int q=rs.getInt(4);
            return new PedidoService.Detalle(rs.getString(1),Objects.requireNonNullElse(rs.getString(2),"N/A"),Objects.requireNonNullElse(rs.getString(3),"N/A"),q,price,price.multiply(BigDecimal.valueOf(q)));
        },id);
        return new Detalle(headers.getFirst(),List.copyOf(lines),jdbc.query("SELECT e.id_evento,u.correo,e.fecha,e.origen,e.destino FROM pedido_evento e JOIN usuario u ON u.id_usuario=e.id_usuario WHERE e.id_factura=? ORDER BY e.id_evento",(rs,n)->new Evento(rs.getLong(1),rs.getString(2),rs.getTimestamp(3).toLocalDateTime(),rs.getString(4),rs.getString(5)),id),jdbc.queryForObject("SELECT destinatario,telefono_entrega,direccion_entrega,nota_entrega,consentimiento_demo FROM factura WHERE id_factura=?",(rs,n)->new EntregaPedido(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5)),id));
    }

    @Transactional(timeout=10,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public boolean transicionar(long id,String origen,String destino) {
        int actor=admin();
        boolean valida=("Pendiente".equals(origen) && "En preparación".equals(destino)) || ("En preparación".equals(origen) && "Despachado".equals(destino));
        if(!valida) throw new ResponseStatusException(HttpStatus.CONFLICT,"Transición inválida. Revisá el estado actual.");
        var actual=jdbc.query("SELECT estado FROM factura WHERE id_factura=? FOR UPDATE",(rs,n)->rs.getString(1),id);
        if(actual.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM pedido_evento WHERE id_factura=? AND origen=? AND destino=?",Integer.class,id,origen,destino)>0) return false;
        if(!origen.equals(actual.getFirst())) throw new ResponseStatusException(HttpStatus.CONFLICT,"El pedido cambió. Recargá su detalle.");
        jdbc.update("UPDATE factura SET estado=? WHERE id_factura=?",destino,id);
        jdbc.update("INSERT INTO pedido_evento(id_factura,id_usuario,origen,destino) VALUES(?,?,?,?)",id,actor,origen,destino);
        return true;
    }
}
