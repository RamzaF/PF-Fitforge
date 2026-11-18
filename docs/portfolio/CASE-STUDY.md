# Caso de estudio · FitForge

## Del catálogo académico a un flujo persistente

El proyecto académico de 2025 reunía catálogo deportivo, variantes, administración y carrito. La recuperación de 2026 buscó hacer reproducible el entorno y conectar la confirmación de pedidos con inventario y consulta posterior, conservando el origen compartido.

El reto técnico era mantener coherentes cuatro estados: carrito de sesión, stock por talla/color, pedido persistido e imagen del producto. Un mensaje de éxito sin persistencia no basta para representar una confirmación.

## Decisiones y compromisos

| Problema | Decisión implementada | Límite |
| --- | --- | --- |
| Dos compradores compiten por stock | Bloqueo de filas, validación vigente y descuento dentro de la transacción del pedido | No reserva de inventario mientras se navega |
| Se reenvía una confirmación | Clave y huella por dueño; repetición idéntica recupera el pedido | Cambiar líneas, precio o destino requiere el protocolo de reconfirmación |
| Cambia el precio durante la compra | El servidor rechaza la confirmación anterior y exige aceptar el precio vigente | El carrito no garantiza precio hasta confirmar |
| Falla una escritura parcial | Cabecera, líneas y descuento revierten juntos en MySQL | Corte físico durante COMMIT no ejecutado |
| Dos administradores editan imágenes | Lectura vigente bajo lock; archivo temporal, movimiento y compensación conservadora | No hay 2PC archivo/BD; pueden quedar huérfanos |
| Se prepara o despacha un pedido | Estado y evento de auditoría en una transacción; repetición sin doble efecto | No envío físico, cancelación ni devolución |
| Se comparte un comprobante | Validación del dueño autenticado; 404 para ajeno o ausente | No implica auditoría de seguridad exhaustiva |

Se mantuvo un monolito MVC con Thymeleaf y servicios por dominio para reutilizar la aplicación existente. JPA y JDBC participan en las operaciones transaccionales según el contrato implementado. Docker aporta un entorno con MySQL real y evita depender de instalaciones Java/Maven/MySQL del host.

La interfaz concentra el catálogo en imagen, precio y disponibilidad; el detalle expone las variantes. Administración reúne catálogo, inventario y pedidos. Las [capturas](screenshots/catalogo.png) proceden de la aplicación real; la aceptación visual de la base fue declarada por Eduin.

## Evidencia y resultado

La integración `a40eb6a` registró 150 pruebas por ejecución de suite y package. La base UI `8c73ecea` registró 151 por ejecución. Son resultados separados, contrastados con sus logs; no se suman ni representan mediciones de rendimiento o impacto comercial. El recorrido de navegador y la comparación tras recrear contenedores verifican persistencia en los escenarios registrados.

La [verificación](VERIFICATION.md) conserva límites y resultados por base. No existe evidencia de clientes reales, ventas comerciales o una operación productiva. El dinero conserva un DOUBLE heredado con controles de escala y representación; una evolución para uso real requeriría revisar el modelo monetario y las migraciones.

## Evolución y responsabilidad compartida

Eduin coordinó la recuperación de 2026 mediante entregas incrementales y paralelas, con desarrollo asistido por IA, revisión independiente y validación real. Definió el alcance, supervisó la integración y aceptó manualmente la interfaz. Esa aceptación se distingue de las pruebas automatizadas y de una autorización de publicación; no se atribuye codificación manual exclusiva ni autonomía total.

[Contribuciones y créditos](../../CONTRIBUTIONS.md) separa el origen académico de la recuperación posterior y evita resolver aliases sin confirmación. Antes de publicar siguen pendientes historia sensible, permisos de participantes y procedencia de recursos. No se atribuye a una empresa la propiedad del trabajo universitario.
