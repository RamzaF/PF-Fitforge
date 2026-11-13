package com.fitforge.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fitforge.domain.Item;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.service.ItemService;
import com.fitforge.service.CarritoSesion;
import com.fitforge.service.ProductoColorTallaService;
import jakarta.servlet.http.HttpSession;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired private ProductoColorTallaService productoColorTallaService;
    @Autowired private HttpSession session;

    @SuppressWarnings("unchecked")
    private List<Item> getSessionItems() {
        List<Item> items = (List<Item>) session.getAttribute("carrito");
        if (items == null) {
            items = new ArrayList<>();
            session.setAttribute("carrito", items);
        }
        return items;
    }

    private ProductoColorTalla serverVariant(Long id) {
        if (id == null) throw new IllegalArgumentException("Seleccioná una variante válida.");
        var variant = productoColorTallaService.getProductoColorTalla(id);
        if (variant == null || !variant.isActivo() || variant.getProducto() == null
                || !variant.getProducto().isActivo()
                || (variant.getProducto().getCategoria() != null && !variant.getProducto().getCategoria().isActivo())) {
            throw new IllegalArgumentException("La variante no está disponible.");
        }
        return variant;
    }

    private void validateQuantity(ProductoColorTalla variant, long quantity) {
        if (quantity < 1 || variant.getExistencias() == null || quantity > variant.getExistencias()) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero y no superar el stock disponible.");
        }
    }

    private Item sessionItem(Long id) {
        return getSessionItems().stream()
            .filter(i -> i.getProductoColorTalla().getIdProductoColorTalla().equals(id))
            .findFirst().orElse(null);
    }

    // Return current server prices and defensive items, never the writable session list.
    @Override public List<Item> getItems() {
        synchronized (CarritoSesion.mutex()) {
            return getSessionItems().stream().map(stored -> {
                Long id = stored.getProductoColorTalla().getIdProductoColorTalla();
                var variant = productoColorTallaService.getProductoColorTalla(id);
                // Keep unavailable lines removable without trusting their stale session price.
                if (variant == null) {
                    variant = new ProductoColorTalla();
                    variant.setIdProductoColorTalla(id);
                }
                var item = new Item(variant);
                item.setCantidad(stored.getCantidad());
                return item;
            }).toList();
        }
    }

    @Override public Item getItem(Long id) {
        synchronized (CarritoSesion.mutex()) {
            return getItems().stream()
                .filter(i -> i.getProductoColorTalla().getIdProductoColorTalla().equals(id))
                .findFirst().orElse(null);
        }
    }

    @Override public void save(Item submitted) {
        synchronized (CarritoSesion.mutex()) {
            if (submitted == null || submitted.getProductoColorTalla() == null) {
                throw new IllegalArgumentException("Seleccioná una variante válida.");
        }
        var variant = serverVariant(submitted.getProductoColorTalla().getIdProductoColorTalla());
        validateQuantity(variant, submitted.getCantidad());
        var existing = sessionItem(variant.getIdProductoColorTalla());
        long quantity = (long) submitted.getCantidad() + (existing == null ? 0 : existing.getCantidad());
        validateQuantity(variant, quantity);
        // All validation precedes mutation; prices and stock come from persistence.
        var replacement = new Item(variant);
        replacement.setCantidad((int) quantity);
        var items = getSessionItems();
        if (existing == null) items.add(replacement);
        else items.set(items.indexOf(existing), replacement);
        session.setAttribute("carrito", items);

        CarritoSesion.modificado();

        }
    }

    @Override public void updateCantidad(Long id, int quantity) {
        synchronized (CarritoSesion.mutex()) {
            var variant = serverVariant(id);
            validateQuantity(variant, quantity);
            var existing = sessionItem(id);
            if (existing == null) throw new IllegalArgumentException("La variante no está en el carrito.");
            var replacement = new Item(variant);
            replacement.setCantidad(quantity);
            var items = getSessionItems();
            items.set(items.indexOf(existing), replacement);
            session.setAttribute("carrito", items);

            CarritoSesion.modificado();
        }
    }

    @Override public void delete(Long id) {
        synchronized (CarritoSesion.mutex()) {
            var items = getSessionItems();
            items.removeIf(i -> i.getProductoColorTalla().getIdProductoColorTalla().equals(id));
            session.setAttribute("carrito", items);

            CarritoSesion.modificado();
        }
    }

    @Override public void clear() {
        synchronized (CarritoSesion.mutex()) {
            session.removeAttribute("carrito");
            CarritoSesion.modificado();
        }
    }
    @Override public double getTotal() {
        synchronized (CarritoSesion.mutex()) {
            return getItems().stream().mapToDouble(Item::getSubtotal).sum();
        }
    }
    @Override public int getCantidadTotal() {
        synchronized (CarritoSesion.mutex()) {
            return getSessionItems().stream().mapToInt(Item::getCantidad).sum();
        }
    }
}
