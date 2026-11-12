# Entrega 3 — pedido interno

Base aprobada: `d90e510416a08d478113900dcb7548e5f4bba238`. Rama local `portfolio/fitforge-orders`. Reutiliza `Factura` como cabecera y `Venta` como detalle. No incluye pago, envío, factura fiscal, reservas, cancelaciones ni devoluciones.

## Contrato implementado

`PedidoService.confirmar(Comando)` devuelve ID persistido, total `BigDecimal` y marca de reutilización. Comando inmutable: UUID y líneas (variante, cantidad, precio aceptado del snapshot servidor). HTTP solo recibe `clave` y CSRF; el navegador no elige dueño, líneas ni importes. El servicio vuelve a leer precio, disponibilidad y stock bajo bloqueo. Cualquier cambio de precio rechaza todo, conserva carrito y exige revisar el total con una clave nueva. Una línea inválida impide confirmar el carrito completo.

El principal debe tener USER y no ADMIN. Correo debe resolver exactamente una cuenta activa; otro caso devuelve 403. Dueño obligatorio en cabecera. Comprobante consultado por ID y dueño, con el mismo 404 para ajeno y ausente; también se exige identidad en el servicio. Funciona con recarga y otra sesión de la misma cuenta. Los precios de detalle y total salen de BD; nombres/color/talla siguen siendo referencias actuales del catálogo, con N/A para opciones ausentes.

Una única transacción de negocio `REQUIRES_NEW`, `READ_COMMITTED`, timeout 10 s y rollback para excepciones. JdbcTemplate y EntityManager comparten DataSource/JpaTransactionManager. Orden: dueño → productos por PK ascendente → variantes por PK ascendente. Se revalida asociación, precio, activo y cantidad, y se descuenta con UPDATE condicionado a stock suficiente. Cabecera, detalles, clave y stock se comprometen juntos; el controlador recibe resultado después del commit y solo entonces limpia el carrito.

Se usa READ_COMMITTED porque la prueba real mostró que buscar una clave ausente FOR UPDATE bajo REPEATABLE_READ introduce gap locks y deadlocks entre compradores. El bloqueo de la fila de dueño serializa claves de esa cuenta entre conexiones y el índice único `(id_usuario, clave_confirmacion)` respalda la deduplicación. Huella SHA-256 v2 incluye IDs, cantidades y precios aceptados normalizados. Replay conflictivo se rechaza. Esto no promete exactamente una ejecución ni deduplica claves distintas.

Carrito, revisión y confirmación comparten mutex de la sesión real, en una sola JVM. Dos pestañas de la misma revisión comparten clave. Un replay completado no limpia un carrito nuevo. Se conservan hasta 16 intentos completados por sesión; tokens desconocidos/expulsados se rechazan. Cambiar carrito invalida el pendiente. Tras error de infraestructura/entrega de resultado se retiene la clave y se ofrece reintentar la confirmación anterior, sin sustituirla al refrescar por un precio nuevo. Si no hubo commit y el precio cambió, ese reintento se rechaza y exige reconfirmación.

Edición de catálogo actualiza solo campos escalares bajo lock del producto: no hace merge de colecciones de stock. Desactivación e inventario usan el mismo orden producto→variante. Un ajuste absoluto de inventario sigue siendo una instrucción administrativa explícita, que puede ejecutarse antes o después de un pedido. Variantes vendidas no se borran. TiendaService delega sus escritores al mismo protocolo.

Dinero: se conserva DOUBLE existente; se valida finito/no negativo, escala exacta de dos decimales con BigDecimal y conversión de vuelta sin perder centavos. Se rechazan fracciones/total no representable. No se afirma precisión monetaria general de DOUBLE.

## Entorno nuevo exclusivamente

El esquema añade dueño NOT NULL, clave, huella, unicidad y CHECKs. **No hay migración ni ALTER de volúmenes anteriores.** Esta rama requiere una BD demo nueva. No arrancar esta imagen contra las BD de Entregas 1–2.

Build allowlist:

```sh
python3 scripts/docker-build.py --tag orders-20261112
FITFORGE_IMAGE_TAG=orders-20261112 docker compose -p fitforge-orders-tests-20261112 up -d --no-build --wait db
FITFORGE_IMAGE_TAG=orders-20261112 docker compose -p fitforge-orders-tests-20261112 run --rm test
FITFORGE_IMAGE_TAG=orders-20261112 docker compose -p fitforge-orders-tests-20261112 run --rm test mvn -o -B -ntp package
FITFORGE_IMAGE_TAG=orders-20261112 FITFORGE_PORT=18084 docker compose -p fitforge-orders-demo-20261112 up -d --no-build --wait db app web
```

Los nombres anteriores identifican los recursos usados en esta entrega. Para reproducir desde cero elegir sufijos nuevos que no existan, manteniendo el prefijo `fitforge-orders-tests-` para pruebas y un proyecto distinto para revisión. No ejecutar pruebas sobre el proyecto demo: sus fixtures borran pedidos de prueba. El guard exige nombre de proyecto de pruebas, indicador explícito, URL Docker interna y clave root exclusiva de esa BD. Solo el servicio test recibe root para crear/eliminar triggers de fallo; la app mantiene usuario DML limitado.

App/BD/tests permanecen en red interna sin salida; solo proxy publica loopback. SMTP cerrado. Java/Maven/MySQL solo Docker, dependencias precargadas durante build; Maven de pruebas/package offline. Las cuentas ficticias de SETUP siguen vigentes.

## Límites de verificación

Ver resultados concretos en VERIFICATION y evidencia externa de esta entrega. Pérdida de entrega del resultado después de un commit real se prueba con decorador de test, sin simular repositorios. **Pérdida física de conexión durante commit / commit incierto de red: NO EJECUTADA.** No equivale a un fallo de flush ni a esa prueba de entrega posterior. Pérdida de sesión/proceso no recupera carrito; un comprobante conocido se recupera autenticando al dueño. No incluye carrito compartido entre instancias ni historial inmutable completo de catálogo.

Fuentes técnicas: [Spring 6.2 JDBC/JPA](https://docs.spring.io/spring-framework/reference/6.2/data-access/orm/jpa.html), [transacciones declarativas](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html), [MySQL 8.4 locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html). Context7 se usó para contrastar participación transaccional; las pruebas usan las versiones fijadas del proyecto, sin upgrades.
