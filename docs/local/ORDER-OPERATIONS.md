# Operación de pedidos (rama paralela)

Base catálogo e174cfc2ed428f77c813df855ae1dff183d47dfd. No incluye media/stock de Yimena.

## Integración posterior

Aplicar `order-operations.sql` solo al inicializar una base **nueva**, después del schema y seed compartidos. Overlay `compose.order-ops.yaml` monta ese SQL. No ejecutar sobre volúmenes anteriores. `pedido_evento` conserva FKs explícitas, sin borrado en cascada; fixtures limpian eventos antes de pedidos. Revisar orden de scripts cuando se combine con migraciones de media/stock.

Puntos comunes: `SecurityConfig.java` habilita GET `/pedidos` y `/pedidos/*` para USER; mantiene el resto cerrado. `layout/plantilla.html` añade enlace admin y habilita Mis Pedidos solo para USER sin ADMIN. Checkout: `CarritoController`, `ConfirmacionCarritoService`, `PedidoService`/implementación y `Factura` incorporan entrega. Preservar cambios de Yimena en servicios de stock, configuración y layout durante la integración; no reemplazar archivos completos.

No modificar protocolo/DAO de inventario; transiciones operativas bloquean únicamente factura y no escriben stock. No push/merge/PR ni integración de ramas en esta entrega.

## Contrato de confirmación y operación

HTTP exige destinatario ficticio (3–80), teléfono ficticio (7–20, dígitos y signos `+() -`), dirección ficticia (10–240), nota opcional (máximo 240), y consentimiento explícito. Espacios normalizados de forma determinista, controles rechazados. No se toma dueño desde HTTP. `EntregaPedido` se copia a Factura y se incluye en SHA-256 v3 con longitudes de campos para evitar ambigüedad. La sobrecarga interna de Comando con dos argumentos conserva exclusivamente un destino demo explícito para compatibilidad de llamadas internas; no hay fallback HTTP.

Reenvío idéntico devuelve el pedido original. Destino alterado con la misma clave produce conflicto sin reescribir datos ni limpiar un carrito nuevo. Tras resultado incierto se retiene destino/clave originales para consultar el commit; reconfirmación de precio rota la clave como antes. Direcciones del pedido no dependen de posteriores ediciones de perfil. Pedidos v2 de volúmenes anteriores no se migran ni se reinterpretan.

`GET /admin/pedidos`: filtros `estado`, `q` (ID exacto o subcadena literal de correo/destinatario, máximo 100), `pagina` de 0 a 100000; 20 filas/página. Conteos por estado y total filtrado consultados en MySQL. `GET /admin/pedidos/{id}` muestra snapshot, dueño y eventos. `POST /admin/pedidos/{id}/estado` exige CSRF y arista explícita `origen`/`destino`. El servicio verifica principal ADMIN y cuenta/rol persistido activo. Transacción READ COMMITTED, lock de factura, evento único por arista; repeticiones incluso después de avanzar son no-op. Auditoría y estado revierten juntos si falla el INSERT. Error 409 tiene explicación y retorno; fallo BD 503 invita a consultar/reintentar sin afirmar éxito. Preparar/despachar nunca escribe inventario.

`GET /pedidos` consulta solo el dueño autenticado; `GET /pedidos/{id}` valida dueño antes de redirigir al comprobante. IDs ajenos y ausentes devuelven 404. El comprobante muestra hitos de estado y fecha desde la auditoría después de validar dueño, sin exponer identidad del administrador. ADMIN usa exclusivamente su detalle separado y no abre comprobantes cliente.

## Reproducción Docker propia

No usar volúmenes antiguos. Desde este worktree:

```sh
python3 scripts/docker-build.py --tag fitforge-order-ops-final-20261116
FITFORGE_IMAGE_TAG=fitforge-order-ops-final-20261116 docker compose -f compose.yaml -f compose.order-ops.yaml -p fitforge-orders-tests-orderops-20261116-v2 up -d --no-build --wait db
FITFORGE_IMAGE_TAG=fitforge-order-ops-final-20261116 docker compose -f compose.yaml -f compose.order-ops.yaml -p fitforge-orders-tests-orderops-20261116-v2 run --rm test mvn -o -B -ntp package
FITFORGE_IMAGE_TAG=fitforge-order-ops-final-20261116 FITFORGE_PORT=18091 docker compose -f compose.yaml -f compose.order-ops.yaml -f compose.order-ops-demo.yaml -p fitforge-order-ops-demo-20261116 up -d --no-build --wait app web
```

En una repetición independiente, cambiar los nombres de proyecto, tag y puerto por otros libres; no recrear demos existentes. El guard de fixtures requiere `fitforge-orders-tests-` y no debe desactivarse. `scripts/order-ops-test.sh` es el bucle de desarrollo, monta fuentes de este worktree como solo lectura en la imagen base propia; la evidencia final usa imagen inmutable sin ese montaje. El contexto de build de allowlist excluye Git, dumps, secretos, AppleDouble y xattrs.

Demo: <http://127.0.0.1:18091/admin/pedidos> y <http://127.0.0.1:18091/pedidos>. Cuentas ficticias propias `admin-orderops@fitforge.example`, `cliente-orderops@fitforge.example`, `otro-orderops@fitforge.example`; clave pública solo demo `FitForge-demo-2026!`. `order-ops-demo.sql` crea estas cuentas únicamente con el overlay demo, nunca en la BD de pruebas. Datos semilla del catálogo se mantienen en la BD exclusiva. Cookie exclusiva `FITFORGE_ORDER_OPS_SESSION` evita colisiones con otras demos locales en puertos distintos.

## Verificación y límites

Suite base: 99/99. Suite final: 119/119 (20 nuevos casos), package offline, MySQL real, sin mocks de persistencia. Logs RED/GREEN, versiones, pruebas de UI, comparación de persistencia y RETURN en `/Users/eduin/.hermes/documentation/portfolio-github/evidence/fitforge-ecommerce/fitforge-order-operations/`.

Puntos comunes adicionales: `carrito/factura.html` carga scripts compartidos para que funcione el menú; layout limita el ancho visual del correo (el nombre completo sigue en DOM). No rediseño general. En pruebas previas se añaden campos demo explícitos HTTP y limpieza de eventos; el caso de unicidad copia también los nuevos campos obligatorios. La antigua aserción de `/pedidos/1` cambia de 403 a 404 según el contrato actual.

Sin pagos, correo, carrier ni envío físico. No cancelaciones, devoluciones ni reintegro. No carga de datos reales, host installs, push, merge, PR ni integración de la rama media/stock. No se probó interrupción física durante COMMIT; la prueba de pérdida de resultado ocurre después de un commit real. No migración de bases existentes. Revisión independiente e integración funcional con Yimena pendientes de Hermes.

### Mapa de integración

| Área | Archivos compartidos que requieren conciliación |
| --- | --- |
| Autorización | `src/main/java/com/fitforge/config/SecurityConfig.java`: regla GET de historial antes del deny residual |
| Navegación | `templates/layout/plantilla.html`: enlace Pedidos ADMIN, Mis Pedidos USER, ancho del correo |
| Checkout | `CarritoController`, `ConfirmacionCarritoService`, `PedidoService`, `impl/PedidoServiceImpl`: entrega y huella v3; no modificar locks ni DAO de stock |
| Cabecera persistida | `domain/Factura.java`: cinco campos de entrega/consentimiento |
| Comprobante | `templates/carrito/factura.html`: destino, hitos y scripts compartidos |
| Fixtures | `OrderTestDatabase`, pruebas previas de catálogo/seguridad/pedidos: entrega explícita, limpieza de auditoría, 404 y unicidad adaptados al nuevo esquema |
| Inicialización | `order-operations.sql`, overlay propio. `compose.yaml` y `schema.sql` base no modificados |

Todos los paths Java/templates se resuelven bajo `InitializrSpringbootProject/`. Servicios/controladores/vistas nuevos son del dominio de pedidos y pueden aplicarse como cambios propios. No se editan uploads, productos, variantes, movimientos ni protocolo de inventario. Los nombres concretos del evento son `id_usuario` (actor ADMIN), `fecha`, `origen`, `destino`; equivalen a los campos semánticos del diseño. No hay observación de transición adicional; la nota de entrega pertenece al snapshot del pedido.
