package com.fitforge.service;

import com.fitforge.domain.Item;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ConfirmacionCarritoService {
    private final ItemService items;
    private final PedidoService pedidos;
    public ConfirmacionCarritoService(ItemService items,PedidoService pedidos) { this.items=items;this.pedidos=pedidos; }
    private record Intento(Integer owner,long revision,PedidoService.Comando comando) {}
    private static final String PENDING="pedidoPendiente", COMPLETED="pedidosCompletados", VERIFY="pedidoPorVerificar";
    @SuppressWarnings("unchecked")
    private Map<String,Intento> completados() {
        var session=CarritoSesion.actual(); var map=(Map<String,Intento>)session.getAttribute(COMPLETED);
        if(map==null) { map=new LinkedHashMap<>();session.setAttribute(COMPLETED,map); } return map;
    }
    public String preparar(List<Item> view) {
        synchronized(CarritoSesion.mutex()) {
            int owner=pedidos.propietario(); var session=CarritoSesion.actual();
            var retained=(Intento)session.getAttribute(PENDING);
            if(Boolean.TRUE.equals(session.getAttribute(VERIFY)) && retained!=null && retained.owner()==owner && retained.revision()==CarritoSesion.revision())
                return retained.comando().clave().toString();
            session.removeAttribute(VERIFY);
            if(view.isEmpty()) { session.removeAttribute(PENDING);return null; }
            var lines=new ArrayList<PedidoService.Linea>();
            for(var item:view) {
                // Invalid lines stay visible and confirmation remains closed for the entire cart.
                if(!item.isDisponible() || item.getCantidad()<1 || item.getProductoColorTalla().getExistencias()<item.getCantidad()) { session.removeAttribute(PENDING);return null; }
                try { lines.add(new PedidoService.Linea(item.getProductoColorTalla().getIdProductoColorTalla(),item.getCantidad(),Dinero.desdeDouble(item.getProductoColorTalla().getProducto().getPrecio()))); }
                catch(PedidoException e) { session.removeAttribute(PENDING);return null; }
            }
            lines.sort(Comparator.comparing(PedidoService.Linea::variante));
            var old=(Intento)session.getAttribute(PENDING);
            if(old!=null && old.owner()==owner && old.revision()==CarritoSesion.revision() && old.comando().lineas().equals(lines)) return old.comando().clave().toString();
            var next=new Intento(owner,CarritoSesion.revision(),new PedidoService.Comando(UUID.randomUUID(),lines));
            session.setAttribute(PENDING,next);return next.comando().clave().toString();
        }
    }
    public boolean requiereConsulta() {
        synchronized(CarritoSesion.mutex()) { return Boolean.TRUE.equals(CarritoSesion.actual().getAttribute(VERIFY)); }
    }
    public PedidoService.Confirmacion confirmar(String key) {
        synchronized(CarritoSesion.mutex()) {
            int owner=pedidos.propietario(); var session=CarritoSesion.actual();
            var completed=completados().get(key);
            if(completed!=null && completed.owner()==owner) return pedidos.confirmar(completed.comando());
            var pending=(Intento)session.getAttribute(PENDING);
            if(pending==null || pending.owner()!=owner || pending.revision()!=CarritoSesion.revision() || !pending.comando().clave().toString().equals(key))
                throw new PedidoException("CONFIRMACION_CONFLICTIVA","Revisá el carrito antes de confirmar de nuevo.");
            PedidoService.Confirmacion result;
            try { result=pedidos.confirmar(pending.comando()); }
            catch(PedidoException e) {
                session.removeAttribute(VERIFY);
                if(e.codigo().equals("PRECIO_CAMBIADO")) session.removeAttribute(PENDING);
                throw e;
            } catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException | jakarta.persistence.PersistenceException e) {
                // Do not rotate a key when the caller cannot establish the commit result.
                session.setAttribute(VERIFY,true); throw e;
            }
            // The transactional proxy has committed before this point. Replay never clears a newer cart.
            items.clear(); session.removeAttribute(PENDING);session.removeAttribute(VERIFY);completados().put(key,pending);
            while(completados().size()>16) completados().remove(completados().keySet().iterator().next());
            return result;
        }
    }
}
