# Verificación de Fitforge — Tareas 1 y 2

> Las secciones iniciales conservan el registro histórico de Tarea 1. El estado actual y los resultados de Tarea 2 están al final.

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

## Entrega 2 — accesos y carrito (2026-11-12)

Esta sección actualiza el estado funcional. Las secciones anteriores son el registro histórico de Tarea 1, redactado antes de su push autorizado posterior: `portfolio/fitforge-docker` y su referencia remota local apuntan a `ac0bcc4`. En **esta entrega** no se hizo push, merge ni PR. `main` sigue en `0055983`.

Rama nueva `portfolio/fitforge-access-cart`, creada antes de editar desde `ac0bcc4f039a140d8d2c1c8fed632bdd6fa80b08`. Solo Tarea 2, sin agentes adicionales ni rediseño. El SHA final se obtiene con `git log -1` de esta rama.

| Control | Resultado y evidencia externa |
|---|---|
| TDD de seguridad y carrito | RED real con MySQL saludable: `red.log`, `red-corrected.log`; fallos de CSRF, rutas mutables, cantidades y precios. Errores de diagnóstico de MockMvc separados y corregidos; no confundirlos con fallos de política |
| TDD de identidad de sesión | `session-red.log`: 33 casos, solo falla reautenticación del correo cambiado; corregido invalidando sesión al cambiar correo/password |
| TDD del despacho de errores | `http-error-red.log`: Tomcat real, 1 fallo esperado (404 convertido en login200). Corrección mínima para `DispatcherType.ERROR`; ruta directa desconocida sigue denegada |
| Suite final Docker | PASS: `docker compose -p fitforge-access-cart-tests run --rm test`, exit 0; **34 pruebas, 0 fallos, 0 errores, 0 omitidas**. `test-error-final.log` |
| Package final Docker | PASS: `docker compose -p fitforge-access-cart-tests run --rm test mvn -o -B -ntp package`, exit 0; **34 pruebas**, JAR generado. `package-error-final.log` |
| Desglose | SecurityIntegrationTest 19; SecurityHttpErrorIntegrationTest 1; CartValidationTest 13; contexto existente 1 |
| Persistencia real en pruebas | MySQL 8.4.4 exclusivo `fitforge-access-cart-tests`, volumen nuevo propio; JDBC/JPA reales, transacciones con rollback. Incluye cambios de stock/precio y actualización admin real con flush verificado por JDBC |
| Flujos de navegador | PASS en navegador integrado Orca: login cliente/admin, selección de variante/cantidad, actualización, eliminación, logout y dos sesiones independientes. JSON `browser-*.json` |
| Separación de carritos | A en 127.0.0.1: camisetas2, total50000; B en localhost: vacío, luego guantes1 total15000; A conserva camisetas2, actualizar3 da75000. Logout A exige login; B conserva su carrito. Cookies separadas por hostname, mismo backend/cuenta ficticia |
| Catálogo, recursos e imágenes | PASS: 15 comprobaciones HTTP esperadas en `http-final.json`, 0 fallos. Imágenes PNG en `/uploads` y `/Uploads`, CSS/JS/WebJars 200; favicon ausente conserva404. DOM confirma 4 imágenes cargadas; captura final revisada |
| Textos solicitados por Eduin | Nombres naturales para productos ficticios y aviso breve de pedidos no habilitados. Sin rótulos repetidos de demo en plantillas; documentación técnica conserva el contexto de prueba. Ajustes de textos guardados con UTF-8 explícito en seed |
| Sesiones/formularios | CSRF global y formularios POST Thymeleaf. GET agregar/eliminar carrito e inventario no muta. Perfil ADMIN/USER; cambio de correo/password exige login nuevo |
| Servicios/volúmenes | app/db/web saludables, único ingreso Fitforge `127.0.0.1:18081`; MySQL de pruebas sin puerto publicado. Volúmenes principal, repro y pruebas conservados. `services-final.log` |
| Diff/índice | Revisión propia de fuentes, tests, plantillas, SQL y documentación; `git diff --check` exit0. Advertencias AppleDouble conservadas, sin reparación. Inspección acotada de líneas agregadas sin hallazgos de claves privadas/cloud/destinos JDBC externos; no auditoría histórica |

Versiones observadas, sin actualizaciones: Docker29.9.3, Compose5.7.0, Java build21.0.7, Maven3.9.9, Java runtime21.0.6+7, Spring Boot3.4.2, Spring Security6.4.2, Hibernate6.6.5.Final, MySQL8.4.4. Context7 consultado para la API6.4 (documentación6.4.12, versión disponible más próxima); no se cambió ninguna dependencia ni imagen fijada.

La demo existente tenía precios25000/15000 al verificarla; se conservaron. La base exclusiva de pruebas usa el seed25/15. A pedido de Eduin se actualizaron únicamente nombres/descripciones de los dos productos ficticios en ambas bases, con condiciones por ID/nombre anterior; no se tocaron precios, stock, cuentas ni volúmenes.

Permisos y endpoints: [matriz completa](../local/ACCESS-CART.md). Catálogo público; administración/usuarios/subida ADMIN; carrito USER; perfil ambos; confirmación, comprobantes y pedidos bloqueados hasta Tarea3. Recuperación/contacto deshabilitados sin envío, escritura ni éxito ficticio. El controlador histórico de confirmación incompleta queda identificado como pendiente e inaccesible; no hay pedido persistente, compra ni pago probado.

Pendientes: Tareas3–5, almacenamiento de subidas en runtime readonly, favicon ausente, error JS preexistente `null.addEventListener` observado en home/carrito (no bloqueó los formularios probados), advertencias open-in-view/Mockito y expresiones Thymeleaf históricas, coautoría/derechos de assets y revisión de secretos históricos. Registro/perfil completo no se recorrieron en navegador; cambio de identidad sí está cubierto por integración. No se probó compra concurrente ni persistencia de pedidos; no pertenecen a esta entrega. No se conectó BD/SMTP externo ni se instalaron herramientas globales.

Evidencia durable de Entrega2:
`/Users/eduin/.hermes/documentation/portfolio-github/evidence/fitforge-access-cart/`.
Revisión independiente pendiente de Hermes/Yimena; entrega detenida para revisión después del commit local.


## Entrega 3 — pedidos persistentes (2026-11-12)

Rama `portfolio/fitforge-orders` desde `d90e510416a08d478113900dcb7548e5f4bba238`. La evidencia de las secciones anteriores permanece histórica. Contrato, comandos y límites actuales: [ORDERS.md](../local/ORDERS.md).

Evidencia externa: `/Users/eduin/.hermes/documentation/portfolio-github/evidence/fitforge-orders/`. Incluye RED HTTP (7 fallos, 0 errores), intentos fallidos intermedios, pruebas MySQL con commits independientes, rollback JDBC/JPA por trigger real, concurrencia, timeout/deadlock, control de dueño/CSRF/métodos y pruebas de entrega de resultado después del commit. Los resultados finales de suite/package/navegador y recursos se registran al cerrar la entrega en RETURN.md; no reutilizar cifras de Entrega 2 para certificar pedidos.

No push, merge, PR ni publicación. Revisión independiente reservada a Hermes/Yimena. Commit incierto por pérdida física de conexión durante commit: NO EJECUTADO; la pérdida posterior de entrega del resultado es una prueba diferente y se identifica así.

### Resultado final de Entrega 3

| Verificación ejecutada | Resultado |
| --- | --- |
| `python3 scripts/docker-build.py --tag orders-20261112` | exit 0; contexto allowlist, imágenes separadas de `local` |
| Docker runner `mvn -o -B -ntp package` | exit 0; 70 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Imagen final, Compose tests nuevo, `mvn -o -B -ntp package` | exit 0; 70 pruebas, 0 fallos, 0 errores, 0 omitidas |
| MySQL real | 8.4.4; aislamiento READ COMMITTED observado durante espera del pedido |
| Concurrencia | misma clave entre conexiones, dos POST misma sesión, última unidad dos dueños, orden inverso, edición de catálogo/inventario |
| Fallos reales | trigger de detalle tras descuentos: rollback; timeout y deadlock InnoDB 1213: rollback y reintento misma clave |
| Entrega de resultado perdida tras commit real | inyección de test, replay conserva clave aun con cambio de precio |
| Tomcat HTTP real | comprobante ajeno/ausente 404, admin 403, anónimo login; ERROR dispatch sin detalle filtrado |
| Navegador Orca | total viejo 65 rechazado tras cambio 25→30; cero pedidos hasta reconfirmar 75; pedido #1 dueño 2, dos detalles; recarga y nueva sesión recuperan comprobante |
| BD demo después de navegador | cabecera 1 / detalles 2 / stocks 3,0,2; total 75 |
| BD de pruebas al cerrar | cabeceras 0 / detalles 0 / triggers 0 / stocks 5,0,3 |

Java build/test 21.0.7, Maven 3.9.9, Spring Boot 3.4.2, Hibernate 6.6.5.Final; runtime fijado Temurin 21.0.6_7. Sin instalaciones/actualizaciones host. La prueba del decorador no simula una pérdida física de conexión en commit, que permanece NO EJECUTADA. Los logs conservan avisos preexistentes de API deprecated, OSIV y carga dinámica de Mockito/ByteBuddy del soporte de pruebas; no hay mocks de persistencia.

Demo conservada: `fitforge-orders-demo-20261112`, <http://127.0.0.1:18084/carrito/factura/1>. Datos exclusivamente ficticios. Entornos previos conservados; ninguna prueba ejecutada sobre ellos. No se avanzó a Tareas 4–5 ni se integró/publicó.
