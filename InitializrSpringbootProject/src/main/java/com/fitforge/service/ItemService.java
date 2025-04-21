/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */

package com.fitforge.service;

/**
 *
 * @author Eduin M
 */

import com.fitforge.domain.Item;

import java.util.List;


public interface ItemService {
    List<Item> getItems();
    Item getItem(Long idProductoColorTalla);
    void save(Item item);
    void delete(Long idProductoColorTalla);
    void updateCantidad(Long idProductoColorTalla, int cantidad);
    void clear();
    double getTotal();
    int getCantidadTotal();
}