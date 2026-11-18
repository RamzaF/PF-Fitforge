# Verificación y límites del candidato

Fecha de preparación: 18 de noviembre de 2026. Base UI exacta: `8c73ecea759c5e593d52de9053c503305a7d76dc`. La aceptación visual manual de esta base fue declarada por Eduin. Este documento no autoriza publicar ni integrar a main.

## Resultados por base

| Base / ejecución | Resultado respaldado | Procedencia y alcance |
| --- | --- | --- |
| Integración `a40eb6a` · 17/11/2026 | Suite: 150; package: 150; cero fallos/errores/omitidas en cada una | Logs previos contrastados; MySQL real en Docker; no es una ejecución del candidato UI |
| Integración `a40eb6a` | 2 casos adicionales por socket HTTP para regresión de lectura vigente de imagen | Evidencia externa previa; no se suma al total de suite ni se afirma repetida en esta preparación |
| UI `8c73ecea` · 17/11/2026 | Suite: 151; package: 151; cero fallos/errores/omitidas en cada una | Logs y salidas previas contrastadas; imagen de esa base |
| UI `8c73ecea` | Contrato JavaScript satisfactorio; recorrido de navegador escritorio/móvil registrado | Capturas reales y comparación de datos/imagen tras recreación, según evidencia previa revisada |
| Preparación local · 18/11/2026 | Build Docker: salida 0 | Tag propio `release-prep-20261118`; build omite tests y se informa por separado |
| Preparación local · 18/11/2026 | Suite: 151; package: 151; cero fallos/errores/omitidas en cada una; salida 0 | Imagen nueva, MySQL real, offline, sin montaje de fuentes; test renombrado con aserciones idénticas |
| Preparación local · 18/11/2026 | Contrato JavaScript satisfactorio; salida 0 | Node 24.22.1-alpine en Docker, sin red y repo de solo lectura |
| Preparación local · 18/11/2026 | 175 archivos de fuentes/recursos de la imagen iguales al worktree por SHA-256 | Correspondencia de fuentes de la imagen de pruebas, sin inferir resultados de otro build |

Los totales se refieren a cada ejecución; no se suman suite, package, casos externos ni ramas. La documentación y las capturas se revisaron después de construir la imagen; no intervienen en la lógica ejecutada. La preparación no cambia Java de aplicación, templates, JavaScript ni SQL: modifica un comentario CSS y dos identificadores del test de navegación, además de documentación y limpieza de archivos locales obsoletos.

## Qué cubren las pruebas

MySQL real y cadena de seguridad; validación de carrito y reconfirmación de precio; cabecera/líneas/stock transaccionales; idempotencia y concurrencia; rollback; autorización por rol y dueño; CSRF; persistencia de imágenes y compensación; movimientos de inventario; transiciones de pedidos con eventos y sin doble descuento. La suite combina pruebas de servicio, MockMvc y casos HTTP: no se presenta todo MockMvc como sockets reales.

La evidencia de navegador previa registra catálogo, variantes, carrito, confirmación, consulta de dueño y administración. Las capturas del README son recortes de esas pantallas reales, no una nueva aceptación visual de esta preparación. No se volvieron a ejecutar todos los recorridos manuales por una limpieza sin cambios de lógica.

La revisión independiente previa de la integración y la aceptación humana de interfaz son gates distintos. La revisión final de esta preparación documental queda a cargo de Eduin; no se autoaprueba su publicación.

## Reproducción y recursos

Seguir [SETUP](../local/SETUP.md), con ambos manifiestos y BD nueva. En esta preparación se ejecutó secuencialmente build → BD propia → suite → package → parada BD → Node. Recursos de prueba exclusivos; límites BD 768 MiB y test 1152 MiB, Maven 128 MiB, Surefire 384 MiB y cache de contextos 2. No se usaron Java/Maven/MySQL/Node del host.

Se reutilizaron cachés y dependencias disponibles: no fue una prueba cold-cache ni de instalación sin red. No se ejecutaron prune, reinicio del motor ni migraciones de bases existentes.

## Límites funcionales que permanecen

- No hay pago real, facturación fiscal, correo real, transportista, cancelaciones, devoluciones ni reintegros.
- No se probó corte físico de conexión durante COMMIT. La pérdida de entrega de resultado posterior a un commit real es otra prueba.
- El carrito vive en sesión, no reserva stock ni se comparte entre instancias. Perder sesión no recupera el carrito.
- No hay transacción distribuida archivo/BD. Ante incertidumbre se conservan archivos y pueden quedar huérfanos; no se promete limpieza perfecta.
- Dinero usa DOUBLE heredado con validaciones acotadas; no se certifica precisión monetaria general.
- No hay migración automática de volúmenes anteriores, pruebas de carga, auditoría exhaustiva de seguridad ni certificación integral de accesibilidad.
- Los recursos externos del navegador y los permisos de imágenes/marcas requieren revisión separada.

## Árbol actual e historia son revisiones distintas

Se retiraron del árbol el dump histórico, el log JVM y configuraciones de IDE, con copia e inventario privados previos. Se reemplazaron guías de ramas anteriores por documentación técnica vigente. No se incorporan conversaciones, instrucciones operativas, cachés, credenciales privadas ni configuraciones de herramientas. Las exclusiones preventivas de Git se conservan.

El uso asistido por IA se describe de forma explícita en [README](../../README.md) y [caso de estudio](CASE-STUDY.md). Sus referencias históricas son aceptables según Eduin y no constituyen un bloqueo ni justifican reescribir commits.

La revisión acotada de commits y blobs alcanzables desde el candidato y las referencias locales de main encontró configuraciones históricas con literales de conexión/credenciales y SQL con cuentas. No se probaron ni se publican valores. No se localizó un scanner especializado en la búsqueda acotada disponible; la revisión por patrones no certifica ausencia de secretos. Es necesario revisar credenciales reales o potenciales y decidir su tratamiento antes de publicar. No se reescribió historia ni se modificaron créditos Git.

También quedan por confirmar permisos y créditos del equipo académico, equivalencia de aliases y [procedencia de recursos gráficos](ASSETS.md). La limpieza del árbol y las pruebas satisfactorias no resuelven estos permisos. **Preparado para revisión local; no publicable todavía.**
