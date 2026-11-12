package com.fitforge.service;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DineroTest {
    @Test void rejectsFractionsNonfiniteNegativeAndNonrepresentableTotals() {
        for(double d:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-0.01,25.001})
            assertThatThrownBy(()->Dinero.desdeDouble(d)).isInstanceOf(PedidoException.class);
        assertThatThrownBy(()->Dinero.paraDouble(new BigDecimal("9007199254740991.01"))).isInstanceOf(PedidoException.class);
        assertThat(Dinero.paraDouble(new BigDecimal("65.00"))).isEqualTo(65.0);
    }
}
