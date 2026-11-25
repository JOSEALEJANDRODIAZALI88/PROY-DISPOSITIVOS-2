# PaceFit AI — Lo implementado y lo pendiente

**Fecha de corte:** 25/11/2026

**Versión del APK verificado:** `0.3.0-beta.1` (código 3)

**Rama de trabajo:** `integrante-4-diaz`

## Resumen

La aplicación Android, la base SQLite y el servidor Supabase están implementados.
El APK está compilado y probado contra el servidor real en Android 8 y Android 15.
**La entrega original todavía no está cerrada al 100 %:** faltan conectar y probar
Gemini, comprobar la llegada real del correo y completar la aceptación en teléfonos físicos.
La publicación en GitHub fue autorizada; el envío se verificará en `integrante-4-diaz`.

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
- [x] Seguridad RLS, permisos restringidos y almacenamiento privado de fotografías.
- [x] Borrado de cuenta, registros y fotografías, con recuperación mediante recibo y purga local.
- [x] Secretos del servidor separados del APK; Android solo utiliza la configuración pública.

**Límite:** SMTP configurado y OTP verificado no demuestran que un mensaje haya llegado a Gmail.

### Ciudad, plogging e incidencias

- [x] Formularios separados con fotografía, vista previa, clasificación, ubicación y consentimiento.
- [x] Coordenadas manuales o GPS opcional; registros privados persistentes.
- [x] Clasificación manual disponible aunque Gemini no esté configurado.
- [x] Publicación explícita al servidor y mapa compartido para usuarios autenticados.
- [x] Puntos calculados por el servidor, sin confiar en los puntos enviados por el cliente.
- [x] Resolución y eliminación de registros propios.
- [x] Gateway de fotografía preparado para Gemini y cuota atómica de 40 llamadas por usuario/hora.

**Límite:** el gateway y su cuota están implementados, pero todavía no se ha probado inferencia real.

## 2. Pruebas que ya aprobaron

| Comprobación | Resultado registrado |
|---|---|
| Pruebas unitarias Android | 32 aprobadas |
| Pruebas instrumentadas en Android 8 / API 26 | 24 aprobadas |
| Pruebas instrumentadas en Android 15 / API 35 | 24 aprobadas |
| Lint del APK actual | 0 errores y 17 advertencias |
| Pruebas SQL del backend local | 34 aprobadas |
| Comprobaciones HTTP del backend local | 27 aprobadas |
| Comprobaciones HTTPS contra Supabase cloud | 27 aprobadas |
| Firma del APK actual | V2 verificada, un firmante |

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

### B. Comprobar el correo real

- [ ] Crear una cuenta propia desde el APK con un correo al que se tenga acceso.
- [ ] Comprobar que llega el mensaje de confirmación, revisando también spam.
- [ ] Introducir el código de ocho dígitos en **Perfil → Cuenta**.
- [ ] Comprobar acceso, reenvío cuando corresponda, cierre y reapertura de sesión.

**Ya está hecho:** configuración Gmail y plantilla OTP. **Falta:** demostrar entrega y uso real del correo.

### C. Validar GPS y transporte en un teléfono

- [ ] Instalar el APK en un Android físico y revisar permisos.
- [ ] Registrar al menos 20 minutos de actividad continua con pantalla apagada.
- [ ] Revisar continuidad del trazado y ausencia de huecos superiores a 30 segundos durante esa prueba.
- [ ] Probar caminar, correr, minibús y Teleférico; contrastar los segmentos y distancias.
- [ ] Comprobar pausa, continuación, finalización y persistencia tras reabrir.
- [ ] Revisar funcionamiento con sensores ausentes y permisos denegados o aproximados.

Estas pruebas requieren participación con un teléfono real; el emulador no las sustituye.

### D. Aceptación y operación

- [ ] Validar el uso con dos teléfonos y cuentas propias: mapa compartido y acciones de autor.
- [ ] Completar pruebas físicas de reinicio, uso sin conexión y recuperación al volver la red.
- [ ] Revisar texto grande, accesibilidad y errores de almacenamiento/espacio insuficiente.
- [ ] Validar operativamente backups, restauración y retención máxima de 30 días tras borrar una cuenta.
- [ ] Mantener el límite Free: no habilitar servicios de pago para completar esas comprobaciones.
- [ ] Registrar evidencias antes de marcar los tickets como Done.

### E. Publicar la actualización en GitHub

- [x] Obtener autorización para publicar esta actualización.
- [x] Revisar cambios y secretos; excluir claves, configuración privada y archivos de compilación.
- [ ] Preparar el commit y verificar su subida a `integrante-4-diaz`.
- [ ] No sobrescribir `main` ni `primera-version`.

La versión local anterior ya fue publicada; **el rediseño y los cambios actuales siguen sin commit/push**.

## 4. Estado de los tickets incluidos

| Ticket | Ya implementado | Pendiente principal |
|---|---|---|
| PF-001 | Perfil local, SQLite, edición y borrado | Aceptación física completa; continúa en Testing |
| PF-007 | Detector integrado con sesiones y rutas | Validación de transporte y distancias en campo |
| PF-011 | RLS, Storage privado y borrado de cuenta probado | Aceptación operativa de backups/retención |
| PF-015 | Registros, fotos, publicación, mapa y puntos de servidor | Gemini real y fotografías reales |
| PF-003, dependencia necesaria | Grabación FGS, pausa, rutas y sensores opcionales | Prueba física prolongada |
| PF-010, dependencia necesaria | Auth, sesión y verificación OTP | Entrega real del correo |
| PF-012, dependencia necesaria | Tarea de fotografía y cuota | Clave e inferencia; las otras tareas siguen en backlog |

No se consideran completados BLE, comidas, cálculos de salud, asesor semanal, chat,
zonas competitivas, PDF ni módulos opcionales. No forman parte de esta entrega autorizada.

## 5. APK y documentación

- **APK:** `app/build/outputs/apk/debug/app-debug.apk`.
- **Tipo:** compilación debug para desarrollo, no publicación Play Store.
- **Tamaño:** 19.168.774 bytes.
- **SHA-256:** `d197592dc5282e4a03630a37b83b18555d0c16252d4eca151613ce47cf198405`.
- [Evidencias y limitaciones](docs/VERIFICACION.md).
- [Configuración del servidor y asistentes privados](docs/SERVIDOR.md).
- [Tablas SQLite y directorios](docs/SQLITE.md).
- [Tickets y requisitos originales](TICKETS.md).

Este resumen describe el APK y las evidencias indicados. Una modificación posterior requiere
su propia verificación; no convierte automáticamente los pendientes en aprobados.
