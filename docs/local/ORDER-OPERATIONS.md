# Operación de pedidos

## Destino y autorización

La confirmación exige destinatario ficticio de 3–80 caracteres, teléfono de 7–20 con al menos un dígito, dirección de 10–240, nota opcional hasta 240 y consentimiento. Se normalizan espacios y se rechazan controles. El destino queda en el pedido y no cambia con posteriores ediciones del perfil.

`GET /pedidos` consulta el dueño autenticado; `/pedidos/{id}` valida propiedad antes de redirigir al comprobante. Ajeno y ausente devuelven 404. El cliente ve hitos sin identidad del administrador. ADMIN usa su detalle separado.

## Administración y auditoría

`GET /admin/pedidos` permite filtros por estado, búsqueda acotada y paginación de 20 filas. Los conteos provienen de MySQL. El detalle muestra dueño, destino e historial de eventos.

`POST /admin/pedidos/{id}/estado` exige CSRF, rol y cuenta ADMIN activa. Transiciones: Pendiente → En preparación → Despachado. Se bloquea factura; estado y evento se confirman juntos. Una repetición ya aplicada no genera doble efecto. Preparar o despachar no modifica stock.

Un conflicto devuelve explicación y retorno; un fallo de BD conserva un mensaje de consulta/reintento sin afirmar éxito. Los errores de lectura ofrecen volver al historial o a la cola administrativa según el contexto. No se afirma que una prueba de fallo inyectado sea una caída física de MySQL.

## Esquema y entorno

`order-operations.sql` se aplica solo al inicializar una BD nueva, después de schema y seed. Conserva claves foráneas sin borrado en cascada. Usar [SETUP](SETUP.md); no ejecutar ese SQL sobre un volumen anterior ni reutilizar recetas de ramas previas.

El overlay opcional `compose.order-ops-demo.yaml` añade identidades ficticias alternativas. La instalación principal no lo necesita. Las pruebas usan el seed base y un proyecto exclusivo con el prefijo requerido por sus guards.

Sin pagos, correo, transportista, cancelaciones, devoluciones ni reintegros. Sin migración de bases previas. Los resultados y el límite de COMMIT se conservan en [VERIFICATION](../portfolio/VERIFICATION.md).
