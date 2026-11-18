# Instalación y pruebas locales con Docker

Demo ficticia; no usar datos personales ni credenciales reales. No realiza cobros, envíos ni correo real. El comprobante no es una factura fiscal.

## Requisitos y elección de recursos

Docker Engine/Desktop con Compose y Python 3 estándar. Java, Maven, MySQL y Node se ejecutan solo en Docker. Las imágenes se fijan por digest en los manifiestos; el build inicial puede descargar dependencias dentro del contenedor. El navegador carga algunas fuentes y recursos externos.

Usar siempre `compose.yaml` + `compose.order-ops.yaml`. El overlay contiene límites de memoria y el tercer SQL de inicialización. **Solo base nueva**: el inicio aplica schema, seed y operación de pedidos, en ese orden. No existe migración de datos de versiones anteriores. No conectar la imagen a volúmenes previos.

Antes de ejecutar, revisar recursos:

```sh
docker info --format '{{.MemTotal}}'
docker stats --no-stream
docker compose ls
docker network ls
docker volume ls
```

Elegir un tag, proyecto y puerto nuevos; no sobreescribir imágenes o contenedores de otra demo. En un motor de aproximadamente 7.75 GiB, ejecutar secuencialmente build → pruebas → detener BD de pruebas → demo. No detener otros proyectos ni usar prune, reinicios globales o borrado de volúmenes para liberar recursos.

## Build y arranque integrado

Desde la raíz del repositorio:

```sh
export FITFORGE_IMAGE_TAG=portfolio-local-20261118
export COMPOSE_PROJECT_NAME=fitforge-portfolio-local-20261118
export FITFORGE_PORT=18128
docker volume ls --filter label=com.docker.compose.project="$COMPOSE_PROJECT_NAME"
python3 scripts/docker-build.py --tag "$FITFORGE_IMAGE_TAG"
docker compose -f compose.yaml -f compose.order-ops.yaml config --quiet
docker compose -f compose.yaml -f compose.order-ops.yaml up -d --no-build --wait db app web
docker compose -f compose.yaml -f compose.order-ops.yaml ps
```

El primer comando de volúmenes debe mostrar que el proyecto escogido no tiene datos anteriores. Si existe, escoger otro nombre antes del arranque. Verificar que el puerto no esté ocupado. Abrir <http://127.0.0.1:18128/tienda/listado>.

El script construye app y test mediante una lista permitida de archivos. Excluye Git, diagnósticos, dumps, archivos del host y metadatos AppleDouble; rechaza enlaces simbólicos en fuentes. El empaquetado del build omite pruebas y **no equivale a una suite aprobada**.

El esquema nuevo incorpora catálogo, variantes, movimientos de inventario, cabecera/detalle de pedido y eventos. Hibernate valida el esquema sin modificarlo. Los SQL de inicialización solo se ejecutan sobre volumen vacío.

## Datos de ejemplo y aislamiento

Usuarios `admin@fitforge.example` y `cliente@fitforge.example`; contraseña pública solo demo `FitForge-demo-2026!`. El seed trae dos productos, dos categorías y tres variantes, una sin existencias. Las capturas incluyen cambios posteriores hechos en la demo; no representan exactamente el seed inicial.

Las claves de MySQL versionadas son públicas y exclusivas de esta demo. La app usa una cuenta con permisos SELECT/INSERT/UPDATE/DELETE, sin root. El servicio de pruebas recibe root únicamente para fixtures y triggers de fallo en su base desechable. SMTP apunta a loopback, puerto cerrado y timeout acotado. Contacto y recuperación por correo no ofrecen un envío real.

La app y BD usan red interna; Nginx añade una red de entrada y publica solo `127.0.0.1`. El proxy no recibe credenciales BD. La app y el proxy usan raíz de solo lectura y directorio temporal. `uploads-data` conserva las imágenes nuevas en un volumen del proyecto, separado del archivo JAR.

Los límites del overlay son BD 768 MiB, app 768 MiB, test 1152 MiB y proxy 64 MiB. Maven de pruebas usa 128 MiB; Surefire, 384 MiB y cache de contextos de tamaño 2. No ejecutar la demo a la vez que build/suites en el motor acotado.

Si el pool automático de redes está agotado, inspeccionar subredes existentes y añadir un override **externo** con IPAM para subredes libres. No copiar ciegamente rangos usados en otra máquina ni modificar redes ajenas. Aplicar el mismo override en todos los comandos de ese proyecto.

## Pruebas contra MySQL real

Detener primero la demo propia, conservando volúmenes. Elegir un proyecto de pruebas nuevo con el prefijo obligatorio `fitforge-orders-tests-`. Los fixtures son destructivos dentro de esa base: jamás apuntarlos a una demo o base personal.

```sh
export COMPOSE_PROJECT_NAME=fitforge-orders-tests-portfolio-20261118
docker volume ls --filter label=com.docker.compose.project="$COMPOSE_PROJECT_NAME"
docker compose -f compose.yaml -f compose.order-ops.yaml config --quiet
docker compose -f compose.yaml -f compose.order-ops.yaml up -d --no-build --wait db
docker compose -f compose.yaml -f compose.order-ops.yaml run --rm --no-deps test mvn -o -B -ntp '-DargLine=-Xmx384m -XX:MaxMetaspaceSize=256m -Dspring.test.context.cache.maxSize=2' test
docker compose -f compose.yaml -f compose.order-ops.yaml run --rm --no-deps test mvn -o -B -ntp '-DargLine=-Xmx384m -XX:MaxMetaspaceSize=256m -Dspring.test.context.cache.maxSize=2' package
docker compose -f compose.yaml -f compose.order-ops.yaml stop db
```

Conservar el tag construido previamente. Ejecutar suite y package por separado, secuencialmente; comprobar sus salidas. Las pruebas usan MySQL, no la base de datos H2. Las referencias al caso de concurrencia denominado H2 son un identificador de regresión.

Contrato JavaScript sin red y con repo de solo lectura:

```sh
docker run --rm --network none --memory 128m --memory-swap 128m -v "$PWD:/repo:ro" -w /repo node:24.22.1-alpine node --max-old-space-size=64 scripts/test-rutinas.js InitializrSpringbootProject/src/main/resources/static/js/rutinas.js
```

El [protocolo integrado](ECOMMERCE-INTEGRATION.md) resume los contratos. Los resultados efectivos se registran en [VERIFICATION](../portfolio/VERIFICATION.md), separados de estos comandos de reproducción.

## Detener y reanudar

Seleccionar de nuevo el nombre del proyecto demo y el mismo tag/puerto. Detener conserva datos:

```sh
export COMPOSE_PROJECT_NAME=fitforge-portfolio-local-20261118
docker compose -f compose.yaml -f compose.order-ops.yaml stop
docker compose -f compose.yaml -f compose.order-ops.yaml up -d --no-build --wait db app web
```

No usar `down --volumes` como rutina. Cambiar el seed no actualiza una BD ya inicializada. Si hay un error de esquema, no importar un dump histórico: conservar los datos y revisar la incompatibilidad.
