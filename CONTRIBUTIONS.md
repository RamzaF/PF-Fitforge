# Origen, contribuciones y créditos

## Etapa académica colaborativa · 2025

FitForge nació en 2025 como proyecto académico de la carrera de Ingeniería en Desarrollo de Software en la Universidad Fidélitas. Coordiné y organicé este trabajo junto con mis compañeros de equipo para diseñar y construir la primera versión de la tienda deportiva sobre Java y Spring Boot.

Durante el ciclo académico desarrollamos de forma conjunta el modelo inicial de base de datos, el catálogo de productos con variantes de talla y color, las vistas renderizadas con Thymeleaf y Bootstrap, la autenticación de usuarios, las pantallas base de administración y los primeros controladores de carrito y carga de imágenes.

El cierre de esa entrega universitaria corresponde en el historial de Git al commit `0055983ee0659efd6314caf63a2ed9f3b09deddc`. El repositorio conserva los commits y autores registrados durante el curso, reconociendo esa base como un logro compartido del equipo.

## Recuperación y evolución técnica · 2026

En 2026 retomé el proyecto para llevar la aplicación desde el prototipo académico hasta un flujo de comercio electrónico consistente y fácil de ejecutar en cualquier entorno local, manteniendo la arquitectura MVC original y el trabajo previo de mis compañeros.

Planifiqué y ejecuté esta evolución por entregas incrementales, apoyándome en herramientas de desarrollo asistido por IA y validando cada cambio con pruebas automatizadas sobre MySQL en Docker y recorridos funcionales en navegador. En esta etapa definí el alcance técnico e integré las siguientes mejoras:

- **Entorno Docker reproducible y aislado:** configuración de contenedores con imágenes fijadas por digest (`compose.yaml` y `compose.order-ops.yaml`), empaquetado sin dependencias en el host, proxy Nginx expuesto solo en loopback, red interna y cuenta de base de datos con permisos DML acotados.
- **Confirmación de pedidos transaccional e idempotente:** validación en servidor de stock, estado activo y precios vigentes bajo bloqueo en MySQL; descuento de inventario y guardado de cabecera y líneas en una sola transacción; control de envíos duplicados mediante clave y huella del intento; y restricción de acceso a comprobantes por propietario autenticado (`a40eb6a`).
- **Consistencia de imágenes de productos:** validación de formato, firma binaria y dimensiones para archivos PNG y JPEG, almacenamiento con identificadores UUID en un volumen persistente (`/media/`), lectura de imagen vigente bajo bloqueo de fila y limpieza compensatoria ante rollback confirmado.
- **Operación de inventario y pedidos:** registro administrativo de ingresos y ajustes de stock con motivo obligatorio y bitácora de movimientos, además de la bandeja de pedidos con filtros, paginación y transiciones auditadas (`Pendiente` → `En preparación` → `Despachado`).
- **Refinamiento de interfaz y navegación (`8c73ecea`):** rediseño del catálogo con tarjetas enfocadas en imagen, precio y disponibilidad; ficha de producto con selección interactiva de variantes; barra superior unificada para todos los módulos administrativos (productos, categorías, colores, tallas, inventario y pedidos); y adaptación responsiva verificada en escritorio y móvil.
- **Ampliación de pruebas automatizadas:** desarrollo de la suite de integración sobre MySQL real en contenedor hasta alcanzar `151` pruebas automatizadas sin fallos, complementadas con la prueba de contrato JavaScript para filtros y manejo de foco.

## Reconocimiento al equipo y recursos

El [historial del repositorio](https://github.com/RamzaF/PF-Fitforge/commits/main/) mantiene intactos los registros del equipo que participó en la etapa universitaria de 2025. La evolución realizada en 2026 se documenta como una ampliación posterior sobre esa base compartida, sin atribuirme de forma exclusiva los módulos construidos originalmente en grupo.

La situación de las fotografías de productos, recursos web de terceros y activos empleados en la demostración local se detalla en [docs/portfolio/ASSETS.md](docs/portfolio/ASSETS.md).
