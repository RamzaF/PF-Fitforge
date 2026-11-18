# Acceso y carrito

La cadena Spring Security aplica autenticación JDBC/BCrypt, CSRF global y reglas por método/ruta. Los recursos públicos pasan por esa cadena. Cliente y administración tienen rutas separadas; un permiso de ruta no garantiza que el recurso exista.

El carrito pertenece a la sesión. HTTP recibe ID de variante y cantidad; precio, disponibilidad y stock se consultan en servidor. Agregar acumula cantidad, actualizar establece una cantidad absoluta y eliminar quita la línea. Entradas inválidas conservan el estado válido previo. Las líneas no disponibles no se presentan como una compra válida ni se descartan silenciosamente.

El carrito no reserva stock. La confirmación vuelve a validar catálogo y precio; un cambio de precio requiere reconfirmación. Véase [pedidos](ORDERS.md) para transacción e idempotencia.

Logout invalida sesión y carrito. El cambio de credenciales del perfil requiere reautenticación. Dos sesiones del mismo cliente mantienen carritos separados; no hay carrito compartido entre instancias ni recuperación del carrito tras pérdida de sesión.

Las operaciones de carrito y logout usan POST con CSRF. Cliente consulta únicamente sus comprobantes; administración usa el detalle administrativo. Contacto y recuperación por correo no tienen envío real en la demo.

La política efectiva está en `SecurityConfig.java`; la reproducción actual se documenta en [SETUP](SETUP.md), sin usar comandos de ramas anteriores.
