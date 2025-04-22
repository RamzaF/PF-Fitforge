package com.fitforge.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fitforge.domain.Item;
import com.fitforge.service.ItemService;
import com.fitforge.service.ProductoColorTallaService;

import jakarta.servlet.http.HttpSession;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ProductoColorTallaService productoColorTallaService;

    @Autowired
    private HttpSession session;

    private List<Item> getSessionItems() {
        List<Item> items = (List<Item>) session.getAttribute("carrito");
        if (items == null) {
            items = new ArrayList<>();
            session.setAttribute("carrito", items);
        }
        return items;
    }

    @Override
    public List<Item> getItems() {
        return getSessionItems();
    }

    @Override
    public Item getItem(Long idProductoColorTalla) {
        return getSessionItems().stream()
                .filter(i -> i.getProductoColorTalla().getIdProductoColorTalla().equals(idProductoColorTalla))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void save(Item item) {
        List<Item> items = getSessionItems();
        Item existente = getItem(item.getProductoColorTalla().getIdProductoColorTalla());
        if (existente == null) {
            items.add(item);
        } else {
            int nuevaCantidad = existente.getCantidad() + item.getCantidad();
            if (nuevaCantidad <= item.getProductoColorTalla().getExistencias()) {
                existente.setCantidad(nuevaCantidad);
            }
        }
        session.setAttribute("carrito", items);
    }

    @Override
    public void delete(Long idProductoColorTalla) {
        List<Item> items = getSessionItems();
        items.removeIf(i -> i.getProductoColorTalla().getIdProductoColorTalla().equals(idProductoColorTalla));
        session.setAttribute("carrito", items);
    }

    @Override
    public void updateCantidad(Long idProductoColorTalla, int cantidad) {
        Item existente = getItem(idProductoColorTalla);
        if (existente != null && cantidad > 0 && cantidad <= existente.getProductoColorTalla().getExistencias()) {
            existente.setCantidad(cantidad);
        }
    }

    @Override
    public void clear() {
        session.removeAttribute("carrito");
    }

    @Override
    public double getTotal() {
        return getSessionItems().stream()
                .mapToDouble(Item::getSubtotal)
                .sum();
    }

    @Override
    public int getCantidadTotal() {
        return getSessionItems().stream()
                .mapToInt(Item::getCantidad)
                .sum();
    }
}

