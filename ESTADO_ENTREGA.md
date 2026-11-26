# PaceFit AI — Lo implementado y lo pendiente

**Fecha de corte:** 25/11/2026

**Versión del APK actual:** `0.3.0-beta.1` (código 3)

**Rama de trabajo:** `integrante-4-diaz`

## Resumen

La aplicación Android, la base SQLite y el servidor Supabase están implementados.
El APK local actual se probó de nuevo contra el servidor real en Android 8 y Android 15:
24 pruebas aprobadas en cada API, con el mismo hash antes y después de las ejecuciones.
Los últimos informes de unitarias y Lint registran 32 aprobadas y 0 errores/17 advertencias;
no se volvieron a ejecutar en esta etapa ni se invocó otra compilación.
**La entrega original todavía no está cerrada al 100 %:** faltan conectar y probar
Gemini y completar la aceptación en teléfonos físicos, incluido el flujo de correo desde el APK.
La recepción real Gmail y la confirmación pública ya aprobaron mediante el asistente de escritorio.
También aprobaron el respaldo cifrado de datos de aplicación y su ensayo de restauración
aislada. Se instaló y ejecutó la tarea local diaria/de inicio de sesión para copiar y purgar.
Esto no equivale a recuperar Auth/PITR ni a garantizar retención con el equipo apagado.
Repositorio e historial: [GitHub, rama `integrante-4-diaz`](https://github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2/tree/integrante-4-diaz).
Esta actualización incorpora los asistentes, la guía física, la revalidación del APK,
la recepción real de correo y el ensayo de respaldo, sin cerrar los pendientes externos.

## 1. Lo que ya está implementado

### Interfaz y almacenamiento

- [x] Navegación organizada en **Inicio, Actividad, Ciudad y Perfil**.
- [x] Cuenta, ajustes, privacidad y visor SQLite como pantallas secundarias.
- [x] Crear, consultar, editar y eliminar el perfil de salud local.
- [x] SQLite persistente mediante Room, con nueve tablas y migraciones 1 → 2 → 3.
- [x] Visor dentro de la app para consultar tablas, columnas, recuentos y filas.
- [x] Directorios privados para fotografías, con nombres UUID y JPEG sin EXIF de origen.
- [x] Borrado local con recuperación de operaciones interrumpidas.
- [x] Salud y rutas permanecen en el teléfono; no se suben a Supabase ni a Gemini.

### Actividad y transporte

- [x] Iniciar, pausar, continuar y finalizar una actividad.
- [x] Servicio en primer plano con notificación y grabación con pantalla apagada.
- [x] Guardar sesiones, puntos GPS y segmentos; mostrar mapa, historial y detalle.
- [x] Pasos y barómetro opcionales, según permisos y sensores disponibles.
- [x] Detector conservador que combina velocidad, pasos disponibles y actividad indicada por el usuario.
- [x] Conservar vehículos y segmentos desconocidos, excluyéndolos de los metros deportivos.

**Límite:** la actividad indicada por el usuario no equivale a reconocimiento automático
validado en campo. Las pruebas GPS ejecutadas utilizaron posiciones simuladas.

### Cuenta y servidor

- [x] Proyecto Supabase exclusivo desplegado en **Free**, sin modificar los otros proyectos.
- [x] Cuatro migraciones SQL y cuatro Edge Functions desplegadas.
- [x] Autenticación real, sesión cifrada con Android Keystore, renovación y cierre de sesión.
- [x] Pantalla para confirmar el correo mediante código y solicitar otro código.
- [x] Plantilla de correo OTP desplegada; el servidor utiliza **8 dígitos**.
- [x] Gmail SMTP configurado, manteniendo obligatoria la confirmación del correo.
- [x] Correo real recibido por el usuario, OTP público confirmado, sesión renovada/cerrada
  y cuenta temporal eliminada, comprobados con el asistente de escritorio.
- [x] Seguridad RLS, permisos restringidos y almacenamiento privado de fotografías.
- [x] Borrado de cuenta, registros y fotografías, con recuperación mediante recibo y purga local.
- [x] Secretos del servidor separados del APK; Android solo utiliza la configuración pública.

**Límite:** la recepción real se probó desde el asistente de escritorio, no desde un teléfono
físico. Los OTP administrativos de las instrumentadas no son la evidencia de esa recepción.

### Ciudad, plogging e incidencias

- [x] Formularios separados con fotografía, vista previa, clasificación, ubicación y consentimiento.
- [x] Coordenadas manuales o GPS opcional; registros privados persistentes.
- [x] Clasificación manual disponible aunque Gemini no esté configurado.
- [x] Publicación explícita al servidor y mapa compartido para usuarios autenticados.
- [x] Puntos calculados por el servidor, sin confiar en los puntos enviados por el cliente.
- [x] Resolución y eliminación de registros propios.
- [x] Gateway de fotografía preparado para Gemini y cuota atómica de 40 llamadas por usuario/hora.

**Límite:** el gateway y su cuota están implementados, pero todavía no se ha probado inferencia real.

### Herramientas de aceptación y respaldo

- [x] Analizador de tiempos GPS sobre copia SQLite privada, sin imprimir coordenadas ni salud.
- [x] Copia cifrada DPAPI de datos cloud de aplicación, excluyendo salud/rutas/contraseñas/sesiones.
- [x] Ensayo real de restauración en PostgreSQL aislado y fotos temporales verificadas.
- [x] Exclusión de cuenta eliminada, puntos recalculados y recuperación de uso/cuota no vacíos.
- [x] Rechazo de copias caducadas y purga acotada de prueba, con límite de 29 días.
- [x] Tarea Windows del usuario actual, sin contraseña guardada ni elevación, a las 03:00 y al iniciar sesión.
- [x] Ejecución real de la tarea: copia completa cifrada/descifrada y purga previa, resultado Windows 0.
- [x] No reutilizar copias parciales de ensayo: verificar procedencia completa, SHA-256 y descifrado.
- [x] Diario durable para recuperar solo cuentas temporales propias tras una interrupción.
- [x] Asistente privado de cinco fotos para Gemini, con recomprensión sin EXIF y revisión manual.

El asistente de IA está preparado, no probado con inferencias reales. La copia DPAPI es
de aplicación, no Auth/PITR. La tarea ya está instalada/probada, pero necesita el equipo
encendido y el usuario conectado; la recuperación integral no está aceptada.

## 2. Pruebas que ya aprobaron

| Comprobación | Resultado registrado |
|---|---|
| Último informe de unitarias Android, no repetido en esta etapa | 32 aprobadas |
| Pruebas instrumentadas en Android 8 / API 26 | 24 aprobadas en el APK actual `a4e74835…` |
| Pruebas instrumentadas en Android 15 / API 35 | 24 aprobadas en el mismo APK actual |
| Último informe Lint, no repetido en esta etapa | 0 errores y 17 advertencias |
| Pruebas SQL del backend local | 34 aprobadas |
| Comprobaciones HTTP del backend local | 27 aprobadas |
| Comprobaciones HTTPS contra Supabase cloud | 27 aprobadas |
| Firma del APK actual | V2 verificada, un firmante |
| Correo real → OTP público → renovación/cierre → limpieza de prueba | Aprobado en el asistente de escritorio |
| Herramientas Python de aceptación/mantenimiento | 63 aprobadas; IA/SMTP/HTTP con dobles, no campo real |
| Copia cifrada → cuenta borrada excluida → datos/foto restaurados aisladamente | Aprobado; no recuperación del proveedor/Auth |
| Tarea diaria/logon → purga → captura completa cifrada | Aprobado; ejecución real y resultado Windows 0 |
| Inventario del proveedor en Free | 0 respaldos disponibles; PITR desactivado, sin activar pagos |

Son **24 casos instrumentados ejecutados en dos versiones: 48 ejecuciones**, no 48 casos distintos.
Las comprobaciones del backend se registraron en sus etapas correspondientes; el detalle y los
límites de cada ejecución están en [docs/VERIFICACION.md](docs/VERIFICACION.md).

Las pruebas Android contra cloud comprobaron rechazo de OTP incorrecto, aceptación de OTP válido,
login, publicación de foto, puntos del servidor, reapertura de sesión y borrado real de cuenta.
Los OTP de prueba se generaron por administración para cuentas desechables: **no se envió correo**.

## 3. Lo que falta para cerrar la entrega

### A. Conectar y comprobar Gemini

- [ ] Revocar cualquier clave que se haya compartido en el chat y generar una **nueva**.
- [ ] Introducirla únicamente en el formulario privado `scripts/configure-gemini.ps1`.
- [ ] Confirmar uso gratuito, validar el modelo y pulsar **Guardar y conectar**.
- [ ] Comprobar que los secretos quedaron desplegados en el proyecto autorizado.
- [ ] Ejecutar una inferencia real y validar cinco fotografías consentidas, incluyendo corrección manual.

**Bloqueo actual:** la última consulta no encontró clave ni modelo Gemini desplegados.
No enviar claves por chat ni guardarlas en este documento, Git o el APK. No activar facturación.
Asistente preparado: `python scripts/check-gemini.py`. Exige exactamente cinco fotos
autorizadas y revisión antes de registrar éxito; no publica fotos ni fabrica respuestas.

### B. Correo real — servicio aprobado; aceptación Android pendiente

- [x] Solicitar un correo por registro público normal, sin OTP administrativo.
- [x] Recibir el mensaje real en Gmail y pegar el código privadamente.
- [x] Verificar el código mediante Auth público, renovar/cerrar sesión y borrar la cuenta de prueba.
- [ ] Crear una cuenta propia desde el APK con un correo al que se tenga acceso.
- [ ] Completar también ese recorrido desde **Perfil → Cuenta** en un Android físico.
- [ ] Comprobar acceso, reenvío cuando corresponda, cierre y reapertura desde el APK.

El primer intento se reportó como no recibido y se corrigió el asistente para reconocer la respuesta
directa de GoTrue. En el siguiente envío autorizado, el usuario mostró el correo recibido y
completó la verificación: resultado **APROBADO**, registrado el 25/11/2026 a las 14:46:29 (-04).
La evidencia JSON sin correo, código ni tokens está en `app/build/verification/email-delivery/`.
Es una prueba real independiente de las 13 offline del asistente; no implica acceso al buzón
por el agente ni sustituye la aceptación Android.

### C. Validar GPS y transporte en un teléfono

- [ ] Instalar el APK en un Android físico y revisar permisos.
- [ ] Registrar al menos 20 minutos de actividad continua con pantalla apagada.
- [ ] Revisar continuidad del trazado y ausencia de huecos superiores a 30 segundos durante esa prueba.
- [ ] Probar caminar, correr, minibús y Teleférico; contrastar los segmentos y distancias.
- [ ] Comprobar pausa, continuación, finalización y persistencia tras reabrir.
- [ ] Revisar funcionamiento con sensores ausentes y permisos denegados o aproximados.

Estas pruebas requieren participación con un teléfono real; el emulador no las sustituye.
El usuario indicó que **ahora no tiene teléfono**. Está disponible
`scripts/check-field-recording.py` para medir los tiempos de una copia privada cuando exista
la grabación; datos sintéticos no cierran este pendiente.

### D. Aceptación y operación

- [ ] Validar el uso con dos teléfonos y cuentas propias: mapa compartido y acciones de autor.
- [ ] Completar pruebas físicas de reinicio, uso sin conexión y recuperación al volver la red.
- [ ] Revisar texto grande, accesibilidad y errores de almacenamiento/espacio insuficiente.
- [x] Implementar copia cifrada de aplicación y comprobar restauración aislada sin la cuenta borrada.
- [x] Instalar mantenimiento diario/al iniciar sesión y comprobar una captura completa mediante la tarea real.
- [ ] Completar recuperación integral/proveedor y garantizar la operación/retención fuera de este equipo.
- [ ] Revisar periódicamente fallos y ejecuciones perdidas; la tarea no funciona con el usuario desconectado o el PC apagado.
- [ ] Mantener el límite Free: no habilitar servicios de pago para completar esas comprobaciones.
- [ ] Registrar evidencias antes de marcar los tickets como Done.

### E. Publicación en GitHub — completada

- [x] Obtener autorización para publicar esta actualización.
- [x] Revisar cambios y secretos; excluir claves, configuración privada y archivos de compilación.
- [x] Preparar el commit y verificar su subida a `integrante-4-diaz`.
- [x] No sobrescribir `main` ni `primera-version`.

La publicación base del rediseño, integración cloud/OTP y pruebas corresponde
al commit `0d657107cc8db0cde9cf86a89dad4f242166cb42`. Se verificó el mismo hash
en la rama remota; `primera-version` conserva su commit anterior y no se creó ni modificó `main`.
La actualización posterior añade las herramientas y evidencias descritas en este documento.
Publicar código no cierra por sí solo la inferencia Gemini, la recuperación integral
ni la aceptación física.

### F. Revalidación del APK local — completada

- [x] Conservar por separado la compilación e informes anteriores de 32 unitarias y Lint.
- [x] Verificar firma y configuración HTTPS pública.
- [x] Ejecutar las 24 instrumentadas en API 26/35 con el APK actual y comprobar hashes.
- [x] Verificar en el servidor el borrado de las cuentas temporales de ambas ejecuciones.

Los informes actuales corresponden al hash `a4e74835…`. Los de `d197592d…` se archivaron
sin sobrescribir su evidencia histórica. No se recompiló ni se repitieron unitarias/Lint
durante esta revalidación.
Usar únicamente emuladores desechables sin datos personales para la instrumentación.

## 4. Estado de los tickets incluidos

| Ticket | Ya implementado | Pendiente principal |
|---|---|---|
| PF-001 | Perfil local, SQLite, edición y borrado | Aceptación física completa; continúa en Testing |
| PF-007 | Detector integrado con sesiones y rutas | Validación de transporte y distancias en campo |
| PF-011 | RLS, Storage, borrado y ensayo de respaldo cifrado de aplicación probados | Recuperación integral/proveedor y purga operativa |
| PF-015 | Registros, fotos, publicación, mapa y puntos de servidor | Gemini real y fotografías reales |
| PF-003, dependencia necesaria | Grabación FGS, pausa, rutas y sensores opcionales | Prueba física prolongada |
| PF-010, dependencia necesaria | Auth/OTP Android; correo recibido y sesión probados en escritorio | Completar correo y sesión en teléfono físico |
| PF-012, dependencia necesaria | Tarea de fotografía y cuota | Clave e inferencia; las otras tareas siguen en backlog |

No se consideran completados BLE, comidas, cálculos de salud, asesor semanal, chat,
zonas competitivas, PDF ni módulos opcionales. No forman parte de esta entrega autorizada.

## 5. APK y documentación

- **APK:** `app/build/outputs/apk/debug/app-debug.apk`.
- **Tipo:** compilación debug para desarrollo, no publicación Play Store.
- **Tamaño actual:** 18.736.774 bytes.
- **SHA-256 actual e instrumentado en ambas API:** `a4e74835603ee118da5990871012d897ba5270194866e5fc555086a8ddb06d21`.
- [Evidencias y limitaciones](docs/VERIFICACION.md).
- [Configuración del servidor y asistentes privados](docs/SERVIDOR.md).
- [Tablas SQLite y directorios](docs/SQLITE.md).
- [Tickets y requisitos originales](TICKETS.md).
- [Guía de aceptación con un teléfono físico](docs/ACEPTACION_FISICA.md).
- [Respaldo cifrado, ensayo de restauración y límites](docs/RESPALDOS.md).

Este resumen describe el APK y las evidencias indicados. Una modificación posterior requiere
su propia verificación; no convierte automáticamente los pendientes en aprobados.
