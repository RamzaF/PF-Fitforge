# Entrega 2: accesos y carrito

Base: `ac0bcc4f039a140d8d2c1c8fed632bdd6fa80b08`, rama nueva `portfolio/fitforge-access-cart`.
Solo Tarea 2 del plan. La base Docker ya fue publicada en su propia rama antes de esta entrega; esta entrega no autoriza push, merge ni PR.

## Matriz de acceso

| Método y rutas reales | Anónimo | Cliente ROLE_USER | ROLE_ADMIN |
|---|---|---|---|
| GET `/`, `/home`, `/tienda/{listado,productos,filtrar,buscar}`, `/tienda/`, `/productos`, `/productos/{id}`, `/categoria/{nombre}`, `/nosotros`, `/contacto` | Público | Público | Público; detalle de producto redirige a administración |
| GET `/css/**`, `/js/**`, `/img/**`, `/webjars/**`, `/uploads/**`, `/Uploads/**` | Público | Público | Público |
| GET `/login`, `/register`, `/forgot-password`; POST `/login`, `/register` | Permitido | Permitido | Permitido |
| GET `/admin`, `/admin/productos`, `/admin/productos/{listado,editar/{id},buscar,categoria/{categoria},inventario/{id}}`, `/admin/{ropa-deportiva,suplementos,accesorios}`, `/usuario/nuevo` | Login | 403 | Permitido |
| POST `/admin/productos/{guardar-producto,eliminar,inventario/guardar,inventario/eliminar/{id}}`, `/usuario/guardar`, `/api/imagenes/subir` | Login con CSRF válido | 403 | Permitido con CSRF válido |
| GET `/carrito`, `/carrito/ver`; POST `/carrito/agregar`, `/carrito/agregar/{id}`, `/carrito/actualizar`, `/carrito/eliminar/{id}` | Login | Permitido; POST requiere CSRF | 403 |
| GET/POST `/perfil` | Login | Permitido; POST requiere CSRF | Permitido; POST requiere CSRF |
| POST `/logout` | Permitido con CSRF | Invalida sesión y carrito con CSRF | Invalida sesión con CSRF |
| POST `/forgot-password`, `/contacto/enviar` | Deshabilitado, 503 con CSRF válido | Igual | Igual |
| `/carrito/confirmar-pedido`, `/carrito/factura/**`, `/pedidos/**` | Denegado | 403 | 403 |
| Cualquier otra ruta | Denegada | 403 | 403 |

CSRF está activo globalmente: todo POST sin token o con token inválido se rechaza con 403 antes del controlador, incluso login/registro/logout. Solo los despachos internos `DispatcherType.ERROR` están permitidos para conservar respuestas como 404/503; un GET directo a una ruta desconocida sigue denegado. Los recursos pasan por la cadena de seguridad; se retiró `WebSecurityCustomizer.ignoring`. El proxy conserva el rewrite `/Uploads/*` → `/uploads/*`; ambas rutas están permitidas. Que una ruta esté permitida no garantiza que el recurso o plantilla exista (p. ej. favicon histórico ausente). GET agregar/eliminar carrito y eliminar inventario ya no muta: retorna 405 para el rol autorizado. GET logout no cierra sesión; los botones usan POST.

## Contrato del carrito

La entrada HTTP solo toma ID de variante y cantidad entera. El servicio consulta la variante persistida: nunca usa el precio o stock enviados por el cliente. Valida cantidad positiva, stock actual y suma acumulada sin desbordamiento antes de reemplazar/agregar una línea. Las operaciones inválidas muestran error y conservan las cantidades anteriores y otras líneas. Actualizar usa cantidad absoluta; agregar suma. Eliminar quita una línea de la sesión. Totales y subtotales se calculan con precios actuales del servidor; no constituyen una reserva ni un precio de compra cerrado.

Los carritos pertenecen a la sesión, no son pedidos persistidos. Al cambiar correo de login o contraseña desde el perfil se destruye la sesión y se requiere reautenticación, evitando conservar la identidad anterior. Logout destruye la sesión; dos sesiones del mismo cliente mantienen carritos separados. La lectura devuelve una lista de nuevos Item, no la lista mutable guardada en sesión. No hay garantía de compras concurrentes ni reserva de stock: corresponde a Tarea 3.

Recuperación de contraseña y contacto no tienen soporte de correo demo. Se retiraron sus formularios y mensajes de éxito; los POST se rechazan sin escribir contacto ni enviar correo.

## Límite de pedidos

El controlador histórico de confirmación contiene una implementación incompleta que vaciaba el carrito y anunciaba éxito sin persistir factura/venta. Se conserva como pendiente explícito de Tarea 3, inaccesible por la política de seguridad. La interfaz muestra que los pedidos no están disponibles y no ofrece confirmación. También se bloquean comprobantes, porque todavía no se implementó su autorización por propietario. No hay compra, pago, factura fiscal ni pedido persistente probado en esta entrega.

## Pruebas y entorno

`SecurityIntegrationTest` usa la cadena de filtros real, login JDBC/BCrypt contra MySQL, sesiones MockMvc y CSRF. `CartValidationTest` usa los servicios/repositorios reales y MySQL, incluidas modificaciones de stock/precio con rollback. `SecurityHttpErrorIntegrationTest` inicia Tomcat real dentro del contenedor para verificar que un recurso público ausente conserva 404, sin redirección a login. No hay mocks de persistencia ni base H2.

Usar exclusivamente el proyecto Compose de pruebas `fitforge-access-cart-tests`, creado con volumen propio `fitforge-access-cart-tests_demo-db`; no ejecutar esta suite sobre datos personales ni sobre la demo de revisión. Las pruebas SQL se revierten al terminar cada caso.

```sh
python3 scripts/docker-build.py
docker compose -p fitforge-access-cart-tests up -d --no-build --wait db
docker compose -p fitforge-access-cart-tests run --rm test
docker compose -p fitforge-access-cart-tests run --rm test mvn -o -B -ntp package
docker compose up -d --no-build --wait app web
```

Se preservan los volúmenes demo existentes y el volumen de pruebas. No usar `down --volumes` por defecto. Build con descarga de dependencias solo en Docker; tests y runtime siguen en red interna. Versiones fijadas sin actualizaciones. Evidencia y resultados efectivos: `docs/portfolio/VERIFICATION.md` y el informe externo de esta entrega.
