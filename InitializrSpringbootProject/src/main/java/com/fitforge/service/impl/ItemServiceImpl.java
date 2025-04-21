package com.fitforge.service.impl;

import com.fitforge.domain.Item;
import com.fitforge.domain.ProductoColorTalla;
import com.fitforge.service.ItemService;
import com.fitforge.service.ProductoColorTallaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ItemServiceImpl implements ItemService {

    private final List<Item> items = new ArrayList<>();

    @Autowired
    private ProductoColorTallaService productoColorTallaService;

    @Override
    public List<Item> getItems() {
        return items;
    }

    @Override
    public Item getItem(Long idProductoColorTalla) {
        return items.stream()
                .filter(i -> i.getProductoColorTalla().getIdProductoColorTalla().equals(idProductoColorTalla))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void save(Item item) {
        var existente = getItem(item.getProductoColorTalla().getIdProductoColorTalla());
        if (existente == null) {
            items.add(item);
        } else {
            if (existente.getCantidad() < item.getProductoColorTalla().getExistencias()) {
                existente.setCantidad(existente.getCantidad() + item.getCantidad());
            }
        }
    }

    @Override
    public void delete(Long idProductoColorTalla) {
        items.removeIf(i -> i.getProductoColorTalla().getIdProductoColorTalla().equals(idProductoColorTalla));
    }

    @Override
    public void updateCantidad(Long idProductoColorTalla, int cantidad) {
        var existente = getItem(idProductoColorTalla);
        if (existente != null) {
            existente.setCantidad(cantidad);
        }
    }

    @Override
    public void clear() {
        items.clear();
    }

    @Override
    public double getTotal() {
        return items.stream().mapToDouble(Item::getSubtotal).sum();
    }

    @Override
    public int getCantidadTotal() {
        return items.stream().mapToInt(Item::getCantidad).sum();
    }
}
