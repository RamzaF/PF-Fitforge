package com.fitforge.service;

import java.time.LocalDateTime;
import java.util.List;

public interface InventarioService {

    enum TipoMovimiento {
        ALTA,
        INGRESO,
        AJUSTE
    }

    record VarianteInventarioView(
            long idVariante,
            long idProducto,
            String productoDescripcion,
            String categoriaDescripcion,
            String colorNombre,
            String tallaNombre,
            int existencias,
            boolean varianteActiva,
            boolean productoActivo,
            String rutaImagen) {}

    record MovimientoView(
            long idMovimiento,
            long idVariante,
            long idProducto,
            String productoDescripcion,
            String colorNombre,
            String tallaNombre,
            String tipo,
            int cantidadAnterior,
            int cantidadDelta,
            int cantidadNueva,
            String motivo,
            String adminNombre,
            String adminCorreo,
            LocalDateTime fechaCreacion) {}

    MovimientoView registrarMovimiento(Long idVariante, TipoMovimiento tipo, int cantidad, String motivo);

    MovimientoView registrarMovimiento(Long idVariante, Long idProductoEsperado, TipoMovimiento tipo, int cantidad, String motivo);

    void registrarAltaInicialSiAplica(Long idVariante, int existencias);

    List<VarianteInventarioView> listarInventarioGeneral(String termino, String filtroStock);

    List<VarianteInventarioView> listarInventarioDeProducto(Long idProducto);

    List<MovimientoView> listarMovimientosRecientes(int limite);

    List<MovimientoView> listarMovimientosDeProducto(Long idProducto, int limite);
}
