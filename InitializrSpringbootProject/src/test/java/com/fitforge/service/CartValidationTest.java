package com.fitforge.service;

import com.fitforge.domain.Item;
import com.fitforge.domain.Producto;
import com.fitforge.domain.ProductoColorTalla;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.persistence.EntityManager;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Transactional
class CartValidationTest {
    @Autowired ItemService cart;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @BeforeEach void session() {
        var request = new MockHttpServletRequest();
        request.getSession();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    Item submitted(long id, int quantity) {
        var product = new Producto();
        product.setPrecio(0.01);
        var variant = new ProductoColorTalla();
        variant.setIdProductoColorTalla(id);
        variant.setExistencias(Integer.MAX_VALUE);
        variant.setProducto(product);
        var item = new Item(variant);
        item.setCantidad(quantity);
        return item;
    }

    @ParameterizedTest @ValueSource(ints={0,-1,6,Integer.MAX_VALUE})
    void invalidAddLeavesExistingCartUntouched(int quantity) {
        cart.save(submitted(3,1));
        assertThatThrownBy(() -> cart.save(submitted(1,quantity))).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItem(3L).getCantidad()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints={0,-1,6,Integer.MAX_VALUE})
    void invalidUpdateLeavesQuantityAndOtherLinesUntouched(int quantity) {
        cart.save(submitted(1,2));
        cart.save(submitted(3,1));
        assertThatThrownBy(() -> cart.updateCantidad(1L,quantity)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.getItem(1L).getCantidad()).isEqualTo(2);
        assertThat(cart.getItem(3L).getCantidad()).isEqualTo(1);
    }

    @Test void unknownVariantAndZeroStockAreRejected() {
        assertThatThrownBy(() -> cart.save(submitted(999999,1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.save(submitted(2,1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.updateCantidad(999999L,1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.getItems()).isEmpty();
    }

    @Test void accumulatedQuantityCannotExceedStockOrOverflow() {
        cart.save(submitted(1,4));
        assertThatThrownBy(() -> cart.save(submitted(1,2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cart.save(submitted(1,Integer.MAX_VALUE))).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.getItem(1L).getCantidad()).isEqualTo(4);
    }

    @Test void quantitiesPricesTotalsAndDeleteUseServerData() {
        cart.save(submitted(1,2));
        cart.save(submitted(3,1));
        assertThat(cart.getTotal()).isEqualTo(65.0);
        assertThat(cart.getItem(1L).getSubtotal()).isEqualTo(50.0);
        cart.updateCantidad(1L,5);
        assertThat(cart.getCantidadTotal()).isEqualTo(6);
        assertThat(cart.getTotal()).isEqualTo(140.0);
        cart.delete(3L);
        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getTotal()).isEqualTo(125.0);
    }

    @Test void changedDatabaseStockIsUsedInsteadOfSessionSnapshot() {
        cart.save(submitted(1,2));
        jdbc.update("UPDATE producto_color_talla SET existencias=1 WHERE id_producto_color_talla=1");
        em.clear();
        assertThatThrownBy(() -> cart.updateCantidad(1L,2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cart.getItem(1L).getCantidad()).isEqualTo(2);
    }

    @Test void currentDatabasePriceIsUsedForTotals() {
        cart.save(submitted(1,2));
        jdbc.update("UPDATE producto SET precio=30 WHERE id_producto=1");
        em.clear();
        assertThat(cart.getTotal()).isEqualTo(60.0);
        assertThat(cart.getItems().getFirst().getSubtotal()).isEqualTo(60.0);
    }
}
