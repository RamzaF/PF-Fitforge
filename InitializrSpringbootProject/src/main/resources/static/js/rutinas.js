document.addEventListener('DOMContentLoaded', function () {
    const filtroModal = document.getElementById('filtroModal');

    // Mover el foco al cerrar el modal para resolver el problema de accesibilidad
    filtroModal?.addEventListener('hidden.bs.modal', function () {
        const botonAbrirModal = document.getElementById('botonAbrirModal');
        if (botonAbrirModal) {
            botonAbrirModal.focus();
        }
    });

    // Evento para el botón "Aplicar Filtros"
    document.getElementById('aplicarFiltros')?.addEventListener('click', function () {
        // Recolectar filtros seleccionados
        const categorias = Array.from(document.querySelectorAll('input[name="categorias"]:checked'))
            .map(input => input.value);
        const colores = Array.from(document.querySelectorAll('input[name="colores"]:checked'))
            .map(input => input.value);
        const tallas = Array.from(document.querySelectorAll('input[name="tallas"]:checked'))
            .map(input => input.value);
        const precioMin = document.getElementById('precioMin')?.value || 0; // Valor por defecto si está vacío
        const precioMax = document.getElementById('precioMax')?.value || 999999; // Valor por defecto si está vacío

        // Construir URL con parámetros
        let url = '/tienda/filtrar?';
        if (categorias.length > 0) url += 'categorias=' + categorias.join(',') + '&';
        if (colores.length > 0) url += 'colores=' + colores.join(',') + '&';
        if (tallas.length > 0) url += 'tallas=' + tallas.join(',') + '&';
        url += 'precioMin=' + precioMin + '&precioMax=' + precioMax;

        // Registro para depuración
        console.log('Filtros aplicados:', { categorias, colores, tallas, precioMin, precioMax });

        // Redirigir a la URL con filtros
        window.location.href = url;
    });
});