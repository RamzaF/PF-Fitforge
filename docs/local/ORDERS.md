# Confirmación de pedidos y consistencia

El carrito es de sesión; el pedido confirmado se persiste en MySQL. La confirmación toma el dueño desde el principal autenticado y valida cuenta, rol, variantes activas, precio vigente, stock y destino ficticio consentido.

Cabecera, líneas y descuento de existencias participan en una transacción. Los escritores de catálogo e inventario comparten un protocolo de bloqueo para evitar comprobar un estado y escribir sobre otro. JPA y JDBC deben mantener su participación transaccional; las pruebas de rollback verifican el comportamiento con MySQL real.

## Idempotencia y resultado incierto

La clave del intento y su huella incluyen dueño, líneas y destino normalizado. Repetir la misma solicitud recupera el pedido original. Un contenido diferente bajo la misma clave se rechaza, sin reescribir el pedido ni limpiar un carrito nuevo.

Si cambia el precio, se exige reconfirmar. Tras rollback de negocio conocido, se permite corregir destino según el estado retenido del intento. Ante fallo con resultado incierto se conserva clave y destino para consultar antes de reenviar. No se presenta como rollback un resultado desconocido.

Los tests de pérdida de entrega del resultado ocurren después de un commit real. **No se ejecutó un corte físico de conexión durante COMMIT**; son escenarios distintos.

## Dinero y stock

El modelo heredado conserva DOUBLE. Se validan valores finitos, no negativos, escala de dos decimales y representación de centavos mediante comprobaciones con BigDecimal; no se afirma precisión monetaria general de DOUBLE. El stock no se reserva en carrito. Variantes con historial no se eliminan; un ajuste absoluto administrativo sigue siendo una instrucción explícita.

## Imágenes

PNG y JPG/JPEG hasta 5 MB, con límites multipart, magic bytes, decodificación y dimensiones. Archivos nuevos con UUID bajo `/media/`; GET/HEAD públicos y cabeceras restrictivas. Se escribe un temporal antes del movimiento al destino y se compensa ante rollback confirmado. La lectura de imagen vigente se hace bajo lock del producto.

No existe 2PC entre MySQL y archivos: si el resultado es incierto o falla la limpieza, se conserva el archivo potencialmente referenciado. Pueden quedar huérfanos; los temporales se depuran al reiniciar sin eliminar recursos históricos. Los permisos de esos recursos se revisan [por separado](../portfolio/ASSETS.md).

## Documentación relacionada

[Operación de pedidos](ORDER-OPERATIONS.md) · [Instalación integrada](SETUP.md) · [Verificación](../portfolio/VERIFICATION.md)
