# Contrato del entorno integrado

Guía de ejecución vigente: [SETUP](SETUP.md). Requiere ambos manifiestos, proyecto exclusivo, tag propio y base nueva. El overlay añade columnas de destino, eventos de pedidos y límites de recursos; el esquema principal conserva movimientos de inventario y almacenamiento de imágenes.

## Invariantes

- Inventario y Pedidos están disponibles en administración; Mis pedidos pertenece al cliente.
- Media pública admite GET y HEAD; otros métodos siguen cerrados. CSRF y verificaciones de rol/dueño permanecen activas.
- Pedido, líneas y descuento se confirman juntos. Preparación y despacho no vuelven a descontar existencias.
- Tras un rechazo de negocio con rollback conocido se permite corregir el destino conservando la clave según el contrato; ante resultado incierto se conserva clave y destino para consultar antes de repetir.
- El teléfono ficticio requiere al menos un dígito, además de longitud y caracteres permitidos.
- La edición de producto consulta imagen y categoría vigentes bajo el protocolo de locks; no debe restaurar una imagen anterior por leer un snapshot desactualizado.
- Los fixtures limpian eventos antes de pedidos y movimientos antes de variantes/productos.

Build, suite, package, Node y demo se ejecutan secuencialmente. Las pruebas finales usan imágenes construidas con las fuentes revisadas, sin montajes que sustituyan esas fuentes. No ejecutar fixtures sobre una demo.

La [verificación](../portfolio/VERIFICATION.md) distingue integración, refinamiento UI y preparación documental. No incluye pago, transportista, correo real, migraciones previas ni certificación de corte físico durante COMMIT.
