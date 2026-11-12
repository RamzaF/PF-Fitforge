# Verificación de entorno Docker — Tarea 1

Fecha: 2026-11-12. Rama: `portfolio/fitforge-docker`. Base: `0055983ee0659efd6314caf63a2ed9f3b09deddc`.
Solo controles previos necesarios y arranque reproducible. Tareas 2–5 fuera de alcance. Este documento pertenece al commit de entorno; consultar `git log -1` para su SHA.

## Resultados observados

| Control | Resultado | Evidencia |
| --- | --- | --- |
| Docker CLI/servidor, contexto y socket | PASS: 29.9.3, desktop-linux, socket del usuario; sin DOCKER_HOST/CONTEXT | preflight.json; nueva respuesta al retomar 1.06 s con timeout 15 s |
| Base Git y trabajo concurrente | PASS: main/base 0055983, cambios originales solo AppleDouble, rama dedicada | git-status.txt y ledger externo |
| Compose | PASS: 5.7.0; `docker compose config --quiet` exit 0 | final-runtime-checks.json |
| Build con contexto seguro | PASS: `python3 scripts/docker-build.py` exit 0 | build-verified-resources.log |
| Recursos en jar | PASS: plantilla real y PNG presentes; assertions durante build | versions-artifact.txt |
| Suite existente | PASS: `docker compose run --rm test`, exit 0; 1 test, 0 fallos/errores/omitidos | mvn-test-final.log |
| Package completo | PASS: `docker compose run --rm test mvn -o -B -ntp package`, exit 0; incluye 1 test | mvn-package-final.log |
| Segunda BD nueva | PASS: proyecto fitforge-demo-repro, volumen/red nuevos, mismo seed e imágenes, package/test exit 0 | repro-package.log; repro-up-ready.log |
| Catálogo local | PASS: HTTP 200 con ambos productos ficticios en puertos 18081 y 18082 | http-catalog.json |
| Navegador real | PASS: catálogo en Chrome nativo, nombres demo, imágenes, banner y logo visibles | observación de UI de esta sesión; browser-check.md |
| Recursos locales del catálogo | PASS: CSS/JS/WebJars e imágenes 200; favicon original ausente 404 | http-assets.json |
| Usuarios nuevos | PASS: admin/cliente ficticios autentican y redirigen a loopback | demo-login.json; no pruebas de credenciales históricas |
| BD limitada | PASS: SELECT/INSERT/UPDATE/DELETE solo en fitforge_demo.*; sin puerto al host | db-check.txt |
| Aislamiento backend | PASS: app/tests/BD solo red interna; conexión a db permitida, IP externa bloqueada | network-probe.txt; final-runtime-checks.json |
| SMTP | PASS del aislamiento: loopback cerrado, sin credenciales ni destino real; conexión rechazada | perfil local y network-probe.txt |
| Servicios ajenos | Sin acciones de esta entrega; cuatro se detuvieron concurrentemente con exit 0/OOM=false; dos siguen activos | preflight.json, foreign-final-state.txt y final-runtime-checks.json |
| Diff e índice | PASS: revisión manual y comprobación contra credenciales históricas sin imprimirlas | source-review.json |
| Checkout, carrito, CSRF/permisos, pedidos persistidos, concurrencia | NO EJECUTADO: tareas funcionales posteriores | No inferir funcionamiento por el único test de contexto |
| Revisión independiente | PENDIENTE de Hermes/Yimena; revisión propia del diff realizada | No se iniciaron agentes ni se publicó nada |

## Versiones fijadas

- Spring Boot 3.4.2, Hibernate 6.6.5.Final y Surefire 3.2.5 del proyecto existente.
- Maven 3.9.9 / Temurin JDK 21.0.7 observados dentro de la imagen de build fijada por digest.
- Runtime Temurin JRE 21.0.6+7 fijado por versión y digest.
- MySQL 8.4.4 y Nginx 1.28.0-alpine fijados por versión y digest en Compose.
- JUnit Platform 1.11.4 según dependencias reales; proveedor de Surefire precargado durante build para tests offline.

## Ajustes y fallos conservados

El primer `docker compose build` falló al leer xattrs de `._.dockerignore`. Se preservaron todos los AppleDouble y `.git`; el usuario autorizó el contexto tar. El script usa una lista permitida y metadatos tar propios, excluyendo secretos, Git, dumps, logs, enlaces simbólicos y AppleDouble. Las fechas de recursos se normalizan dentro del contenedor: fecha cero hizo que Maven omitiera copias aunque su log las contara. Una assertion del build falló antes de corregirlo y ahora verifica los archivos reales.

El primer test offline falló por el proveedor JUnit no descargado. Se precarga exclusivamente durante build. Después, el test de contexto y el runtime detectaron `factura.estado` ENUM frente a String Java. Solo el esquema demo usa ahora VARCHAR(255); el primer volumen recibió ese ALTER y el segundo se creó directamente con el SQL corregido. IDs BIGINT y FK de venta a variante se alinean con las entidades actuales. `factura.id_usuario` queda nullable por la relación ausente del modelo; no se certifica persistencia de pedidos.

Docker 29 omitió el puerto publicado al estar la app solo en red interna. Se añadió Nginx como entrada local, con upstream fijo a app y sin credenciales BD/SMTP. Solo el proxy se conecta además a una red de ingreso no interna; app/BD/tests siguen internos. El proxy no enruta IP, corre sin capacidades ni privilegios y con raíz de solo lectura. Sus rutas temporales se mueven a /tmp. La conectividad de ingreso del proxy es la excepción explícita al aislamiento; no se afirma que todo contenedor carezca de ruta exterior. Context7 y documentación Docker/Moby consultados, sin actualizar herramientas.

Los templates originales solicitan `/Uploads` y los recursos están en `/uploads`. La configuración del proxy demo traduce ese prefijo para compatibilidad con Linux. No se modificaron templates, CSS ni diseño. Se retiraron las cuentas históricas en memoria y su ejemplo comentado; el mecanismo existente de autenticación BD recibe los hashes nuevos del seed.

## Estado de entrega y límites

Principal activo: MySQL, Java y Nginx; URL <http://127.0.0.1:18081/tienda/listado>. La repetición se detuvo con `down` sin `--volumes`: ambos volúmenes demo quedan preservados. No se tocó ninguna red/volumen ajeno. Al cerrar, Serviweb y tres contenedores Flux CRM están detenidos (exit 0, OOMKilled=false, finalización 17:26:25–32 UTC); los dos contenedores Expediente siguen activos. No se atribuye causa al cambio concurrente y no se los reinició.

El catálogo referencia un favicon que no existe: 404 pendiente. El navegador usa jsDelivr, Google Fonts/gstatic e imágenes Unsplash según templates existentes; el backend no depende de ellos para arrancar. Los derechos de assets/coautoría y el historial requieren revisión antes de publicación. Las credenciales históricas fueron retiradas del código actual de ejecución; no se rotaron, probaron ni sanearon del historial.

Persisten advertencias originales de Spring Security sobre recursos ignorados, open-in-view y Mockito/agent Java. El test único solo carga el contexto: no es una suite funcional. Recuperación de contraseña, confirmación de compra y subida de imágenes tienen limitaciones previas; subida no soportada con la raíz de solo lectura. No hubo cambios de reglas de negocio, instalación en macOS, push, merge, cambio de visibilidad o licencia. La alerta Tienda-IC-2025 no se reabrió en este alcance.

Evidencia durable y logs, fuera del repo:
`/Users/eduin/.hermes/documentation/portfolio-github/evidence/fitforge-docker/2026-11-12-resume/`.
Ver [SETUP](../local/SETUP.md) para comandos exactos, parada y eliminación opcional de datos demo.
