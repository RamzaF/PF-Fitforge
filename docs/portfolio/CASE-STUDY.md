# Caso de estudio · FitForge

## Del catálogo académico a un flujo de compra persistente

La versión universitaria de FitForge (2025), desarrollada en la Universidad Fidélitas como parte de la carrera de Ingeniería en Desarrollo de Software, contaba con catálogo deportivo, variantes por talla y color, autenticación y carrito de compras. Al retomar el proyecto en 2026, el objetivo técnico fue doble: lograr que toda la aplicación pudiera levantarse de forma reproducible en contenedores sin configurar herramientas en la máquina local, y cerrar el ciclo completo entre la confirmación del carrito, el descuento de inventario y el seguimiento administrativo del pedido.

El problema central de ingeniería consistió en mantener coherentes cuatro estados bajo solicitudes concurrentes: el carrito en sesión HTTP, las existencias por variante en MySQL, el pedido persistido con su historial de eventos y las imágenes de producto almacenadas en disco.

## Decisiones de diseño y compromisos

Se conservó la arquitectura monolítica MVC con Spring Boot, Thymeleaf, JPA y JDBC para aprovechar la base construida por el equipo y concentrar el esfuerzo en la integridad transaccional, el aislamiento de contenedores y la claridad de uso.

| Problema técnico | Solución implementada | Alcance y compromiso asumido |
| --- | --- | --- |
| Dos compradores intentan adquirir el último stock disponible | Bloqueo pesimista de filas (`FOR UPDATE`) en orden determinista, validación de existencias vigentes y descuento dentro de la transacción del pedido | El carrito en sesión no reserva unidades mientras se navega; el stock se compromete al confirmar |
| El usuario reenvía por error el formulario de confirmación | Clave de idempotencia y huella calculada por cliente, líneas y destino; repetir el mismo envío recupera el pedido original sin duplicar descuentos | Si cambian las líneas, el destino o el precio, se solicita una nueva confirmación |
| Un administrador modifica el precio mientras un cliente tiene el producto en carrito | El servidor compara el importe en sesión con el precio vigente bajo bloqueo y exige confirmar explícitamente el nuevo monto | El carrito refleja el precio al momento de agregar, pero prevalece el valor vigente al cerrar la compra |
| Falla una escritura a mitad del registro de la compra | Cabecera, líneas de detalle y descuento de inventario comparten transacción en MySQL y revierten en bloque | Cubre rollbacks de negocio y fallos de entrega posteriores al commit; no modela un corte físico del motor durante `COMMIT` |
| Dos administradores actualizan la imagen o datos del mismo producto | Lectura de imagen y categoría vigentes bajo bloqueo; escritura previa en archivo temporal, movimiento al volumen `/media/` y compensación ante rollback confirmado | No existe commit distribuido (2PC) entre MySQL y archivos; ante un fallo incierto se conserva el archivo para no romper referencias |
| El pedido avanza a preparación o despacho en administración | Transición de estado (`Pendiente` → `En preparación` → `Despachado`) y registro en `pedido_evento` dentro de una misma transacción, sin alterar el stock ya descontado | Flujo interno de tienda sin conexión a transportistas ni módulo de cancelaciones o devoluciones |
| Un usuario intenta acceder al comprobante de otro comprador | Verificación del propietario autenticado en `/pedidos` y `/carrito/factura/{id}`, devolviendo `404` ante pedidos ajenos o inexistentes | Control de acceso sobre el flujo de pedidos de la demostración |

En la capa visual (`8c73ecea`) se aplicó divulgación progresiva para reducir el ruido en pantalla: las tarjetas del catálogo muestran únicamente imagen, categoría, nombre, precio en colones (`₡`) y disponibilidad, mientras que la descripción y la selección interactiva de tallas y colores se concentran en la ficha del producto. En el panel administrativo se integró una barra superior común para navegar entre productos, categorías, colores, tallas, inventario con bitácora y pedidos (véanse las capturas de [catálogo](screenshots/catalogo.png) y [administración](screenshots/administracion.png)).

## Verificación y resultados técnicos

Cada etapa de la evolución se validó sobre contenedores Docker con MySQL `8.4.4` real, sin sustituir la base de datos por motores en memoria:

- **Integración funcional (`a40eb6a`):** `150` pruebas automatizadas aprobadas en `mvn test` y `150` en `mvn package`, además de `2` pruebas sobre socket HTTP real para verificar concurrencia en el reemplazo de imágenes de producto.
- **Refinamiento de interfaz (`8c73ecea`):** `151` pruebas automatizadas aprobadas por ejecución de suite y de empaquetado, junto con la validación en Node (`24.22.1-alpine`) del contrato JavaScript de filtros y foco (`scripts/test-rutinas.js`).
- **Persistencia en navegador y contenedores:** recorrido completo de usuario y administrador en navegador real y comprobación de que los registros de nueve tablas en MySQL y los archivos de imagen en `/media/` mantienen su integridad byte a byte tras recrear los contenedores (`--force-recreate`).

El detalle de las ejecuciones y los límites del modelo heredado (como el uso de `DOUBLE` acotado con validaciones de dos decimales mediante `BigDecimal` y la operación exclusiva con datos ficticios) se recogen en [VERIFICATION.md](VERIFICATION.md) y en el [contrato de pedidos](../local/ORDERS.md).

## Mi rol en el proyecto

Coordiné y organicé la etapa inicial de 2025 junto con mis compañeros de la carrera de Ingeniería en Desarrollo de Software en la Universidad Fidélitas, donde levantamos la estructura base del catálogo, las variantes y la administración de la tienda.

En 2026 retomé el proyecto para diseñar e integrar su evolución técnica mediante entregas incrementales, utilizando herramientas de desarrollo asistido por IA combinadas con pruebas automatizadas sobre MySQL y verificación funcional en navegador. Me encargué de definir los contratos de consistencia transaccional e idempotencia, configurar el entorno Docker aislado, completar los módulos de inventario auditable y operación de pedidos, y renovar la interfaz de cliente y administración.

La distinción entre el desarrollo académico compartido y las mejoras incorporadas en 2026 se documenta en [CONTRIBUTIONS.md](../../CONTRIBUTIONS.md).
