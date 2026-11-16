> Esta rama añade operación de pedidos en paralelo a media/stock. Guía vigente: [ORDER-OPERATIONS.md](ORDER-OPERATIONS.md). Las URLs y comandos anteriores que siguen son evidencia histórica; para esta entrega usar el overlay y proyecto OrderOps.

# FitForge local: solo Docker

**Estado actual: Entrega 3.** Seguir primero [ORDERS.md](ORDERS.md) para build, pruebas y arranque en proyectos y volúmenes nuevos. URL de revisión: <http://127.0.0.1:18084/tienda/listado>. No usar las imágenes nuevas con los volúmenes anteriores: no se migran.

Las instrucciones de Entregas 1–2 que siguen son históricas y corresponden a sus ramas/imágenes. Entorno Docker de Tarea 1 y accesos/carrito de Tarea 2. Aplicación académica existente; no usar como tienda real.
Requisitos del host: Docker Engine/Desktop, Compose y Python 3 estándar para transmitir el contexto. No requiere Java, Maven ni MySQL instalados en macOS. No instala paquetes en el host.

## Versiones

Spring Boot 3.4.2 y Java 21 según `pom.xml`. Imágenes fijadas por versión y digest en Dockerfile/Compose: Maven 3.9.9 con Temurin 21, runtime Temurin 21.0.6_7, MySQL 8.4.4 y Nginx 1.28.0-alpine para entrada local. Docker 29.9.3 y Compose 5.7.0 usados en la verificación. El digest también fija el JDK concreto de la imagen Maven; consultar VERIFICATION para las versiones observadas.

## Build, pruebas y arranque

Desde la raíz del repo, en la rama `portfolio/fitforge-access-cart` (base Docker `ac0bcc4`):

```sh
python3 scripts/docker-build.py
docker compose config --quiet
docker compose up -d --no-build db
docker compose -p fitforge-access-cart-tests up -d --no-build --wait db
docker compose -p fitforge-access-cart-tests run --rm test
docker compose -p fitforge-access-cart-tests run --rm test mvn -o -B -ntp package
docker compose up -d --no-build --wait app web
docker compose ps
```

El script transmite únicamente Dockerfile, .dockerignore, pom y fuentes/recursos de tipos revisados. Construye ambas imágenes locales y excluye atributos extendidos, AppleDouble, enlaces simbólicos, Git, configuración del host, logs y dumps. Usar este script en este volumen externo: `docker compose build` falla por los atributos de AppleDouble. No borrar ni reparar esos archivos.

El build puede descargar dependencias; tests y ejecución usan la red interna aislada. La primera compilación empaqueta con tests omitidos; eso **no** prueba la aplicación. La suite funcional y package se ejecutan contra MySQL en el proyecto exclusivo `fitforge-access-cart-tests`, con volumen propio; no reutilizar datos personales ni la demo de revisión como destino de pruebas. Si fallan, conservar el error y no asumir que el entorno está verificado.

Abrir <http://127.0.0.1:18081/tienda/listado>. Cambiar el puerto con `FITFORGE_PORT=18083 docker compose up -d --no-build --wait app web` si estuviera ocupado; siempre se publica en loopback.

## Datos y aislamiento

Se crea `fitforge-demo_demo-db` al primer arranque. MySQL ejecuta exclusivamente `schema.sql` y `seed-demo.sql` de esta carpeta. No importar `DBActualizado v2.txt`: contiene destinos/cuentas históricos y operaciones destructivas. La inicialización solo ocurre en un volumen vacío; editar el seed no cambia una BD existente.

Dos productos ficticios, dos categorías y tres variantes (incluida una sin stock). Usuarios `admin@fitforge.example` y `cliente@fitforge.example`, contraseña **exclusivamente ficticia** `FitForge-demo-2026!`. Hashes BCrypt generados dentro del contenedor Java; para repetir la demo se usa el seed versionado. No reutilizar estas claves para ningún otro entorno.

Las claves MySQL de Compose/SQL son públicas y exclusivas de la demo. El usuario `fitforge_app` tiene solo SELECT/INSERT/UPDATE/DELETE sobre `fitforge_demo.*`. La aplicación no recibe la clave root. Hibernate valida el esquema, no lo crea ni modifica. Spring no carga `.env` automáticamente; Compose entrega explícitamente las variables y activa `local`.

App, tests y MySQL solo están en la red `internal: true` del proyecto. SMTP apunta al puerto cerrado 1 de loopback dentro del contenedor, con timeout de 1 segundo: cualquier envío falla localmente. No hay SMTP real ni receptor público. No se montan Docker socket ni carpetas ajenas. La app y el proxy usan UID sin privilegios, raíz de solo lectura y /tmp temporal.

Docker 29 omite los puertos publicados de un contenedor conectado solo a una red interna. Por eso `web` (Nginx) tiene también una red de ingreso propia y publica exclusivamente `127.0.0.1:18081`. Su upstream es fijo `app:8080`; no es proxy abierto, no recibe credenciales BD/SMTP, tiene todas las capacidades eliminadas y forwarding IP deshabilitado. Solo este proxy dispone de una red no interna; app, MySQL y tests siguen sin ruta externa. La salud HTTP del proxy comprueba el catálogo real antes de que `up --wait` termine.

El navegador sí usa recursos existentes externos: jsDelivr, Google Fonts (y su CDN gstatic) y una imagen de respaldo de Unsplash. No son conexiones salientes del backend. No se cambiaron diseño ni recursos de terceros; los derechos de distribución quedan pendientes para etapas posteriores.

## Segunda instalación independiente

Elegir un nombre de proyecto nuevo; verificar que no exista antes de ejecutarlo. No eliminar el primer volumen:

```sh
docker volume ls --filter label=com.docker.compose.project=fitforge-demo-repro
FITFORGE_PORT=18082 docker compose -p fitforge-demo-repro up -d --no-build db
FITFORGE_PORT=18082 docker compose -p fitforge-demo-repro run --rm test
FITFORGE_PORT=18082 docker compose -p fitforge-demo-repro run --rm test mvn -o -B -ntp package
FITFORGE_PORT=18082 docker compose -p fitforge-demo-repro up -d --no-build --wait app web
```

Abrir <http://127.0.0.1:18082/tienda/listado>. Ambos proyectos usan las mismas imágenes construidas; redes y volúmenes son independientes. Parar la repetición con `docker compose -p fitforge-demo-repro down`; conserva sus datos.

## Parada y eliminación opcional

```sh
docker compose stop
# Volver a levantar sin reconstruir:
docker compose up -d --no-build
# Eliminar solo contenedores y red de esta demo; conserva el volumen:
docker compose down
```

**Destructivo, opcional:** `docker compose down --volumes` borra definitivamente la BD de **este** proyecto demo. No es necesario para repetir la instalación. Para la segunda demo, el comando equivalente es `docker compose -p fitforge-demo-repro down --volumes`. Nunca usar prune ni borrar volúmenes ajenos.

## Límites

Tarea 2 restringe administración a ADMIN y carrito a USER, habilita CSRF y valida cantidades/stock/precios en servidor. Consultar [matriz de acceso y contrato](ACCESS-CART.md). Recuperación de contraseña y contacto están deshabilitados honestamente, sin envío real. Confirmación de pedido, comprobantes y `/pedidos/**` están bloqueados hasta Tarea 3; no anunciar ventas/pagos reales. Tareas 3–5 no se ejecutan. La subida de archivos del código original necesita rediseñar su almacenamiento para ejecución como jar; no está validada y la raíz de la app es de solo lectura. El catálogo y sus imágenes estáticas son el objetivo de esta entrega. El favicon referenciado por el template original no existe y devuelve 404. El proxy traduce /Uploads a /uploads para servir las rutas históricas en Linux. En esta entrega no hay push, merge, PR, publicación ni licencia nueva. La rama Docker base sí se publicó anteriormente; el registro de Tarea 1 describe el estado previo a ese push.
