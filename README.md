# FitForge

### Del catálogo deportivo a la gestión completa del pedido

Tienda web con variantes de talla y color, inventario auditable y pedidos persistentes. Desarrollada con **Java y Spring Boot**, con interfaz Thymeleaf y ejecución local mediante Docker.

![Java 21](https://img.shields.io/badge/Java-21-E76F00?style=flat-square) ![Spring Boot 3.4.2](https://img.shields.io/badge/Spring_Boot-3.4.2-6DB33F?style=flat-square&logo=springboot&logoColor=white) ![Thymeleaf](https://img.shields.io/badge/Thymeleaf-005F0F?style=flat-square&logo=thymeleaf&logoColor=white) ![MySQL 8.4.4](https://img.shields.io/badge/MySQL-8.4.4-4479A1?style=flat-square&logo=mysql&logoColor=white) ![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker&logoColor=white)

**[Ejecutar localmente](#ejecución-local)** · **[Arquitectura](#arquitectura-y-decisiones-técnicas)** · **[Pruebas](#pruebas-y-validación)** · **[Mi contribución](#origen-del-proyecto-y-mi-contribución)** · **[Caso de estudio](docs/portfolio/CASE-STUDY.md)**

[![Catálogo de FitForge: búsqueda de productos, categorías, precios y disponibilidad](docs/portfolio/screenshots/catalogo.png)](docs/portfolio/screenshots/catalogo.png)

> **Demo de portafolio.** Utiliza datos ficticios y permite recorrer la compra y la operación administrativa. No procesa pagos, no emite facturas fiscales ni realiza envíos físicos.

## Una tienda, dos recorridos

| Para el cliente | Para la administración |
| --- | --- |
| Explorar productos y filtrar por categoría, talla, color y precio | Crear y editar productos, categorías, colores, tallas y variantes |
| Elegir una variante con disponibilidad real y agregarla al carrito | Subir y reemplazar imágenes de productos |
| Confirmar un pedido con precios y cantidades validados en el servidor | Registrar ingresos y ajustes de stock con motivo y responsable |
| Consultar su comprobante e historial privado de pedidos | Consultar, preparar y despachar pedidos con registro de eventos |

**El recorrido completo:**

```text
Catálogo → Variante → Carrito → Pedido confirmado → En preparación → Despachado
```

El inventario se descuenta al confirmar el pedido, no al prepararlo o despacharlo.

### Administración y vista móvil

[![Panel administrativo de FitForge con acceso a productos, categorías, colores, tallas, inventario y pedidos](docs/portfolio/screenshots/administracion.png)](docs/portfolio/screenshots/administracion.png)

[Ver ficha de producto en móvil](docs/portfolio/screenshots/producto-movil.png): selección de talla y color, existencias por variante y agregado al carrito.

## Arquitectura y decisiones técnicas

FitForge conserva una arquitectura **monolítica MVC con renderizado en servidor**. Los controladores delegan las reglas de negocio en servicios que coordinan JPA y JDBC sobre MySQL; Thymeleaf presenta las vistas y JavaScript complementa la interacción.

```text
Navegador → Nginx → Spring MVC / Spring Security
                              │
                       Servicios de negocio
                         │             │
                     JPA / JDBC     Imágenes
                         │          volumen Docker
                       MySQL
```

| Problema | Decisión implementada |
| --- | --- |
| Compras simultáneas sobre las últimas unidades | Bloqueo de filas y validación de stock vigente; pedido, líneas y descuento dentro de una transacción |
| Reenvío del formulario de compra | Clave de idempotencia y huella del intento para recuperar el pedido sin duplicarlo |
| Cambio de precio antes de confirmar | Comparación en servidor y reconfirmación explícita del comprador |
| Ediciones simultáneas de imágenes | Lectura vigente bajo bloqueo y limpieza compensatoria ante rollback confirmado |
| Consulta de pedidos ajenos | Verificación de propietario, separación de roles y protección CSRF |
| Reinicio de la aplicación | Persistencia de base de datos e imágenes en volúmenes separados de los contenedores |

<details>
<summary><strong>Más sobre seguridad, imágenes y aislamiento</strong></summary>

- **Autenticación:** contraseñas con BCrypt y permisos diferenciados para `USER` y `ADMIN`.
- **Imágenes:** PNG y JPEG de hasta 5 MB, con validación de firma binaria, decodificación y dimensiones; nombres UUID y escritura temporal previa.
- **Consistencia de archivos:** MySQL y el almacenamiento no comparten una transacción distribuida. Ante un resultado incierto se conserva el archivo para evitar referencias rotas.
- **Auditoría:** movimientos de inventario con actor y motivo; transiciones de pedido con marca de tiempo.
- **Contenedores:** aplicación sin privilegios de root y con raíz de solo lectura; cuenta MySQL limitada a operaciones DML. Backend y base de datos en red interna; Nginx publica únicamente en `127.0.0.1`.

[Decisiones y compromisos](docs/portfolio/CASE-STUDY.md) · [Contrato de pedidos](docs/local/ORDERS.md)

</details>

## Stack tecnológico

| Backend | Datos | Interfaz | Entorno |
| --- | --- | --- | --- |
| Java 21 | MySQL 8.4.4 | Thymeleaf | Docker Compose |
| Spring Boot 3.4.2 | Spring Data JPA / Hibernate | Bootstrap 5.3.3 | Maven 3.9.9 |
| Spring MVC / Security | Spring JDBC | JavaScript / CSS | Nginx 1.28.0-alpine |

Las versiones y dependencias están definidas en [pom.xml](InitializrSpringbootProject/pom.xml), [Dockerfile](Dockerfile) y [compose.yaml](compose.yaml). Las imágenes base de Java, MySQL y Nginx están fijadas por digest. El navegador carga algunos recursos externos, por lo que la demo no es completamente autónoma sin conexión.

## Pruebas y validación

**151 pruebas aprobadas en la suite y 151 durante el empaquetado**, en ejecuciones separadas sobre MySQL real en Docker. Es el resultado documentado del refinamiento de interfaz y de la preparación de entrega; no un indicador de CI en tiempo real.

La validación cubre permisos, CSRF, acceso a pedidos ajenos, cantidades y stock, confirmación transaccional, rollback, idempotencia, concurrencia e imágenes. También se verificó el contrato JavaScript de filtros y manejo de foco.

En navegador se recorrió la operación desde un **catálogo vacío**: crear producto e imagen, ingresar stock, comprar, preparar y despachar. Se comprobó la persistencia de datos e imágenes tras recrear los contenedores.

[Consultar resultados, escenarios y límites de las pruebas →](docs/portfolio/VERIFICATION.md)

## Ejecución local

Necesitás **Docker con Compose y Python 3 estándar**. Java, Maven, MySQL y Node se ejecutan en contenedores, sin instalarlos en el equipo.

Desde la raíz del repositorio, elegí nombres y puerto libres:

```sh
export FITFORGE_IMAGE_TAG=portfolio-local-20261118
export COMPOSE_PROJECT_NAME=fitforge-portfolio-local-20261118
export FITFORGE_PORT=18128

python3 scripts/docker-build.py --tag "$FITFORGE_IMAGE_TAG"

docker compose -f compose.yaml -f compose.order-ops.yaml config --quiet
docker compose -f compose.yaml -f compose.order-ops.yaml up -d --no-build --wait db app web
```

Abrí **http://127.0.0.1:18128/tienda/listado**.

| Cuenta de demostración | Correo |
| --- | --- |
| Administración | `admin@fitforge.example` |
| Cliente | `cliente@fitforge.example` |

Contraseña común de demostración: `FitForge-demo-2026!`. Usá únicamente datos ficticios y no reutilicés esta contraseña fuera de la demo.

Para detener los contenedores conservando los datos:

```sh
docker compose -f compose.yaml -f compose.order-ops.yaml stop
```

> La instalación requiere **ambos manifiestos Compose y un volumen de base de datos nuevo**. No hay migración automática de volúmenes anteriores. Con memoria limitada, ejecutá build, pruebas y demo de forma secuencial; las pruebas deben utilizar su propia base desechable.

[Guía completa de instalación y pruebas →](docs/local/SETUP.md)

## Origen del proyecto y mi contribución

Coordiné y organicé este proyecto junto con mis compañeros en 2025 como parte de la carrera de **Ingeniería en Desarrollo de Software en la Universidad Fidélitas**. En esa etapa construimos en equipo la base de la tienda en Spring Boot: catálogo, variantes de talla y color, carrito, control de acceso y primeras pantallas administrativas.

En 2026 retomé FitForge para completar la operación de la tienda y mejorar su interfaz, manteniendo la base original y los aportes del equipo. Organicé la evolución mediante entregas incrementales, revisión independiente y pruebas sobre MySQL y navegador.

En esta etapa definí el alcance y coordiné la integración del entorno Docker, los pedidos persistentes, la gestión de imágenes e inventario y la operación administrativa. El refinamiento visual completó la navegación de cliente y administración.

[Contribuciones y reconocimiento al equipo](CONTRIBUTIONS.md) · [Caso de estudio técnico](docs/portfolio/CASE-STUDY.md)

## Alcance de la demostración

- No incluye pagos reales, facturación electrónica, transportistas, SMTP real ni flujos de cancelación o devolución.
- El carrito permanece en sesión y no reserva existencias antes de confirmar. El esquema monetario heredado utiliza `DOUBLE`, acotado mediante validaciones de dos decimales con `BigDecimal`.
- El historial conserva los aportes originales del equipo. La procedencia y el estado de los recursos gráficos se documentan en [ASSETS.md](docs/portfolio/ASSETS.md).
