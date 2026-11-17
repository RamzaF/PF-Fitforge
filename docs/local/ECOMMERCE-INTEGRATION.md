# Candidato de integración local ecommerce

Base media revisada: `0c091a044c6e9186cbaca7daf9847e4f5b4d141a`. Pedidos congelados: `615be88f17952342f4405626814df26cdff8bae9`. Base común: `e174cfc2ed428f77c813df855ae1dff183d47dfd`.

Estado: candidato local para revisión independiente posterior. No implica aprobación del integrado ni publicación.

## Instalación nueva

Usar siempre `compose.yaml` **junto con** `compose.order-ops.yaml`. El primero conserva almacenamiento uploads-data, permisos de usuario DML, tabla movimiento_inventario y red demo interna. El segundo añade únicamente para una BD NUEVA el SQL03 con columnas de destino en factura, tabla pedido_evento y sus constraints/índices. Nunca reutilizar un volumen de ramas previas ni ejecutar SQL03 sobre una BD usada. Los datos demo son ficticios.

Java/Maven/MySQL/Node exclusivamente Docker. Construir con `python3 scripts/docker-build.py --tag <tag-nuevo>` (allowlist contra AppleDouble). El build limita heap/metaspace Maven; no equivale a pruebas. Antes de arrancar revisar memoria y redes con docker info/stats y network inspect. Escoger proyectos, volúmenes, puertos loopback y subredes libres nuevos. El pool automático puede estar agotado: usar un override externo IPAM explícito, sin prune ni cambios de redes ajenas.

El overlay integrado ya incluye los límites siguientes. El override externo añade IPAM con subredes libres verificadas para demo e ingress; puede reiterar estos mismos límites:

```yaml
services:
  db:
    mem_limit: 768m
    memswap_limit: 768m
    cpus: 1.0
  app:
    mem_limit: 768m
    memswap_limit: 768m
    cpus: 2.0
    environment:
      JAVA_TOOL_OPTIONS: "-Xmx256m -XX:MaxMetaspaceSize=192m"
      SERVER_SERVLET_SESSION_COOKIE_NAME: FITFORGE_ECOMMERCE_SESSION
  web:
    mem_limit: 64m
    memswap_limit: 64m
    cpus: 0.5
  test:
    mem_limit: 1152m
    memswap_limit: 1152m
    cpus: 2.0
    environment:
      MAVEN_OPTS: "-Xmx128m -XX:MaxMetaspaceSize=96m"
```

Ejecutar TODO SECUENCIAL: build, DB de tests, suite, package, parada DB de tests, Node, luego demo. Nunca app/demo al mismo tiempo que build/tests. No detener otros proyectos. Dejar detenidos los recursos propios no necesarios, conservando volúmenes.

Para suite y package, usar proyecto nuevo con prefijo `fitforge-orders-tests-`, tag propio y los tres archivos compose (`compose.yaml`, `compose.order-ops.yaml`, override externo). Primero `up -d --no-build --wait db`, luego `run --rm --no-deps test mvn -o -B -ntp '-DargLine=-Xmx384m -XX:MaxMetaspaceSize=256m -Dspring.test.context.cache.maxSize=2' test`, después el mismo comando con `package`. Guardar logs y exit reales de cada ejecución; no sumar conteos entre ramas o ejecuciones. `scripts/order-ops-test.sh` es histórico de la entrega paralela; este protocolo acotado es el vigente del integrado.

Node: contenedor `node:24.22.1-alpine`, memoria128m/heap64, red none, repo read-only; ejecutar `node --max-old-space-size=64 scripts/test-rutinas.js InitializrSpringbootProject/src/main/resources/static/js/rutinas.js`.

## Contrato conciliado

Menú ADMIN mantiene Inventario y Pedidos. Media pública admite GET y HEAD; POST/otros métodos siguen cerrados por reglas de seguridad. CSRF global y comprobaciones de rol/dueño permanecen activas. El esquema y los fixtures limpian pedido_evento antes de factura y movimiento_inventario antes de variantes/productos.

Errores de lectura de pedidos ofrecen mensaje de carga y retorno al historial del cliente o cola administrativa. Una transición fallida mantiene el mensaje conservador de retry. Tras rechazo de negocio conocido con rollback se permite corregir destino conservando la clave; un resultado incierto retiene clave/destino y protección contra cambios. Teléfono exige al menos un dígito además del conjunto y longitudes anteriores.

La lectura vigente de producto/categoría de H2 permanece sin cambios. Preparar/despachar no toca stock: se descuenta una sola vez al confirmar la orden; replay y eventos son idempotentes. No incluye pago, transporte físico, correo, migración de datos ni certificación de corte físico durante COMMIT.

La evidencia propia completa, comandos, logs, exits, SHA y recorrido desde catálogo vacío se registra externamente en `/Users/eduin/.hermes/documentation/portfolio-github/evidence/fitforge-ecommerce/fitforge-integration/RETURN.md`.
