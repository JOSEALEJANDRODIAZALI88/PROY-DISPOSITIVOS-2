# PaceFit AI — Registro de verificación

## Recompilación tras la publicación — 25/11/2026

- Publicación funcional en `integrante-4-diaz`: `0d65710`; documentación de publicación: `805a42e`.
- Se revisaron los ajustes de toolchain JDK 21 (`settings.gradle.kts` y
  `gradle/gradle-daemon-jvm.properties`) y se ejecutó de nuevo:
  `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`.
- Resultado: **BUILD SUCCESSFUL**, 32 unitarias aprobadas, sin fallos ni omisiones;
  Lint **0 errores y 17 advertencias**. APK instrumentado compilado.
- APK local actual: `app/build/outputs/apk/debug/app-debug.apk`, `0.3.0-beta.1`, código 3,
  **18.738.004 bytes**.
- SHA-256: `6dd4b09edc6eda299d4233a6cac4632bc8e2fc7058304c4f522a3a2947da1ac9`.
- Firma v2 verificada, un firmante; BuildConfig conserva la URL HTTPS del proyecto autorizado
  y su configuración pública.
- **No se ejecutaron de nuevo las pruebas instrumentadas sobre este nuevo binario.**
  Las 24 aprobadas en cada API que se detallan abajo corresponden al APK anterior `d197592d…`.
  Sus informes no se adjudican a este hash. No hubo cambios funcionales Kotlin en esta recompilación.
- El APK, las claves y la configuración privada no se publicaron en Git.

## Ampliación de confirmación por código — 25/11/2026

- Configuración remota consultada sin imprimir secretos: SMTP Gmail guardado, puerto TLS,
  remitente igual al usuario y confirmación obligatoria activa. No acredita entrega real.
- Plantilla de confirmación OTP aplicada al proyecto autorizado: el mensaje no contiene
  enlaces a `localhost` ni tokens de sesión en una URL.
- El proyecto usa **8 dígitos**. No se redujo esa configuración: el cliente admite códigos
  completos de 6 u 8 dígitos para compatibilidad, y el servidor decide su validez.
- Se añadió confirmación/reenvío en Cuenta, sin guardar contraseña/código en SavedState.
- APK y pruebas compilados correctamente: **32 unitarias aprobadas**, sin omisiones;
  Lint **0 errores y 17 advertencias**.
- **24 instrumentadas aprobadas en Android 15/API 35 y 24 en Android 8/API 26**:
  mismo APK, 24 casos distintos/48 ejecuciones, sin fallos ni omisiones.
  Se rechaza un OTP incorrecto sin crear sesión y se acepta el OTP real generado por Auth,
  seguido de login HTTPS, publicación, reapertura de sesión y borrado real de la cuenta temporal.
  El controlador verificó también que Auth ya no contenía esa cuenta.
- La prueba UI adicional comprueba códigos incompletos/completos y que el código introducido
  desaparece al recrear la actividad. No se guarda el código en SavedState ni se imprime.
- Los códigos de prueba se generaron por administración para usuarios desechables:
  **no se envió correo y no se acredita entrega Gmail**.
- Gemini todavía no está configurado según la última consulta; no se ejecutó inferencia.

Artefacto instrumentado de esta ampliación, anterior a la recompilación:

- `app/build/outputs/apk/debug/app-debug.apk`, `0.3.0-beta.1`, código 3, **19.168.774 bytes**.
- SHA-256: `d197592dc5282e4a03630a37b83b18555d0c16252d4eca151613ce47cf198405`.
- Firma v2 verificada con `apksigner`, un firmante.
- Evidencia por dispositivo: `app/build/verification/cloud-api26/` y `cloud-api35/`,
  cada uno con `instrumentation.txt` y `artifact.json` del mismo SHA.
- Apertura adicional en API 26: `am start -W`, `Status: ok`, **1.553 ms** de tiempo total.
  No acredita rendimiento en un teléfono físico de referencia.
- Requiere todavía entrega de correo real, nueva clave Gemini e inferencia, fotografías reales,
  aceptación GPS en teléfono, reinicio/almacenamiento/accesibilidad y operación de backups.
  No se marca el alcance original como Done.

## Entrega anterior: Supabase cloud y APK conectado — 25/11/2026

Proyecto nuevo y exclusivo autorizado: **PaceFit AI**, `ymfypllkcrqwfwihqzpf`, São Paulo,
instancia `nano`, organización **Free** comprobada antes y después del despliegue.
Los otros dos proyectos del usuario se conservaron sin cambios; no se activó facturación.
Se aplicaron cuatro migraciones y se desplegaron cuatro Edge Functions en ese proyecto.

| Comprobación real | Resultado |
|---|---|
| Auditoría cloud | Cuatro migraciones, tres tablas públicas con RLS, bucket privado y cron activo |
| `python scripts/cloud-admin.py smoke` | 27 comprobaciones HTTPS cloud aprobadas |
| Android compilación/unitarias | APK y pruebas compilados; 32 unitarias aprobadas, sin omisiones |
| Lint | 0 errores y 17 advertencias |
| Cliente Android 15/API 35 → Supabase HTTPS | 23 pruebas instrumentadas aprobadas, incluida conexión real |
| Cliente Android 8/API 26 → Supabase HTTPS | 23 pruebas instrumentadas aprobadas, incluida conexión real |

Las comprobaciones HTTPS repiten aislamiento anónimo/entre usuarios, puntos recalculados,
EXIF/GPS eliminado, bucket privado, cuota concurrente 40/45, resolución propia, refresh/cierre,
cascadas de cuenta y recibo tras borrar Auth, incluida cuenta sin perfil aceptado.
Tres cuentas desechables se crearon ya confirmadas **solo para pruebas** y se limpiaron por UUID.
Esto **no prueba** entrega de correo ni permite desactivar su confirmación.

`CloudConnectionTest` comprueba desde el repositorio Android real: login HTTPS, nombre público,
perfil de salud local, JPEG local y publicación consentida, puntos del servidor, descarga de foto
firmada, reapertura de sesión cifrada y lectura remota, eliminación de cuenta y purga SQLite/fotos.
El controlador además verifica por administración que el usuario Auth realmente desapareció.
Solo se inyecta la contraseña de esa cuenta temporal por stdin a un archivo privado de prueba;
la clave `service_role` nunca entra en Android, argumentos ni artefactos.
Las otras 22 pruebas también se vuelven a ejecutar con el APK configurado. La prueba de Cuenta
ahora comprueba el estado real configurado/sin configurar, sin omitirla mediante `return`.
El caso cloud es optativo en ejecuciones ordinarias sin fixture y se registra como omitido;
no se cuenta como aprobado sin la ejecución explícita con cuenta desechable.

Artefacto conectado anterior:

- `app/build/outputs/apk/debug/app-debug.apk`, `0.3.0-beta.1`, código 3, **19.168.774 bytes**.
- SHA-256: `bde0635343aa725809c4cf53bb41c1c1ee32178abdbf8b4dbc0dba046a841a68`.
- BuildConfig verificado: URL HTTPS del proyecto autorizado y JWT de rol público `anon`, no `service_role`.
- Evidencia Android cloud anterior: `app/build/verification/cloud-before-otp-api{26,35}/instrumentation.txt`
  y `artifact.json` en cada directorio. Mismo APK comprobado en ambas API:
  23 casos por API, 46 ejecuciones aprobadas, ninguna omisión.
- Firma v2 del APK conectado verificada con `apksigner`; un firmante.
- Apertura adicional del APK conectado en API 26: `am start -W`, `Status: ok`,
  2.112 ms de tiempo total. No acredita el objetivo de rendimiento en un teléfono de referencia.
- Revisión visual de Cuenta en API 26: formulario configurado, correo/contraseña habilitados,
  sin aviso de servidor desconectado. No se crearon registros personales de producción.

La confirmación de correo sigue activa y la contraseña mínima es 10 caracteres.
En esta entrega anterior estaban pendientes SMTP, confirmación real y Gemini. Se mantiene como
evidencia histórica, sin adjudicarle los cambios OTP posteriores. La primera ventana de configuración
Gemini se cerró sin guardar `.env`; no se subió una clave ni se llamó al modelo.
Siguen pendientes campo GPS, teléfono físico, dos dispositivos con cuentas propias, cinco fotos
reales, reinicio/almacenamiento/accesibilidad y operación de backups/retención. No se marca Done.
Guía y administración segura: [`SERVIDOR.md`](SERVIDOR.md).

Asistentes privados posteriores: comprobación de controles de ambos formularios aprobada,
sin usar portapapeles ni credenciales. Ocho pruebas Python offline aprobadas para la seguridad
del asistente SMTP (dobles de prueba, no Gmail real). Se corrigió la aceptación de los nuevos
formatos de clave Google y la entrada con Ctrl+V enmascarada. Esto no convierte los pendientes
SMTP/Gemini en aprobados: deben completarse los formularios y comprobar los servicios reales.

## Evidencia anterior al despliegue: integración `0.3.0-beta.1` — 25/11/2026

Alcance autorizado: PF-001/007/011/015 originales y dependencias necesarias PF-003/010/PF-012
(`photo` solamente). Cuatro secciones y navegación secundaria. Esta sección no hereda la
aceptación de versiones anteriores.

### Backend local ejecutado

Docker Desktop 28.1.1, Supabase CLI 3.4.0, PostgreSQL 17, Auth, PostgREST, Storage y Edge Runtime
locales reales. Se aplicaron las cuatro migraciones SQL, sin reiniciar/borrar la base.

| Comprobación | Resultado |
|---|---|
| `npx --yes supabase test db` | 34 pruebas pgTAP aprobadas |
| `node scripts/test-backend.mjs` | 27 comprobaciones HTTP aprobadas |
| Cuota concurrente | 45 solicitudes simultáneas: 40 admitidas, 5 rechazadas; 40 filas de auditoría |
| Aislamiento | Anónimo y escrituras directas denegados; acceso cruzado no altera propietarios/puntos/reportes |
| Fotografías | Bucket privado; acceso autenticado y eliminación de EXIF/GPS inyectado comprobados |
| Puntos | El cliente intentó 99.999; PostgreSQL devolvió 20 |
| Borrado | Auth/filas/fotos propios eliminados; otra cuenta conservada; reintento con recibo sin JWT |
| Cuenta sin perfil aceptado | Borrado y recibo comprobados; no se puede recrear el perfil mientras se elimina |
| Gemini sin configurar | Error 503 real; no hay clasificación inventada |

El script usa tres cuentas desechables propias, no un servidor cloud. Las pruebas SQL hacen
`ROLLBACK`; el script elimina únicamente sus usuarios/objetos. Se detuvo el stack conservando
sus volúmenes. No se ejecutó inferencia real, correo cloud ni borrado de backups del proveedor.

### Android

JDK 21, SDK de compilación 36, APK `0.3.0-beta.1` (código 3). No se utilizan los resultados
de `0.2.0-local.1` como evidencia de esta versión.

| Comprobación | Resultado |
|---|---|
| APK debug y APK de pruebas | Compilación correcta |
| Unitarias | 32 aprobadas, sin fallos, errores ni omisiones |
| Instrumentadas, Pixel_4 / Android 15 / API 35 / x86_64 | 22 aprobadas, sin fallos ni omisiones |
| Instrumentadas, Android 8.0 / API 26 / x86_64 | 22 aprobadas, sin fallos ni omisiones |
| Lint | 0 errores, 17 advertencias |
| Instalación adicional del APK final en API 26 y 35 | `adb install -r`: Success |
| Apertura adicional en API 26 y 35 | `am start -W`: Status ok, MainActivity; API 35 confirma `LaunchState: COLD` |
| Firma del APK final | `apksigner verify --verbose`: firma v2 verificada, un firmante |
| Directorios de producción, API 26 y 35 | Confirmados `.db`, `-wal`, `-shm` y `files/pacefit/photos/{plogging,reports}` |

Unitarias: `LocalFeaturesTest` (13), `ProfileValidatorTest` (9) y `ProfileViewModelTest` (10).
Las advertencias de Lint son recomendaciones de versiones, target API y KSP; no se creó
un baseline ni se ocultaron los nuevos chequeos con supresiones.

El runner usa `pacefit-instrumentation.db`, directorios y sesión de prueba aislados.
**Ejecutar la instrumentación solo en emuladores de prueba sin datos personales:** Gradle
desinstala la aplicación al terminar y puede borrar sus archivos de producción, aunque el
runner aísle los casos. Los emuladores usados no contenían registros personales de producción.
El caso FGS inyecta GPS simulado: no acredita precisión, batería ni 20 minutos de recorrido real.

Cobertura instrumentada: `HealthDatabaseTest` (2), `LocalStorageTest` (9),
`ActivityStorageTest` (3), `ActivityServiceTest` (1), `SessionStoreTest` (2) y
`LocalNavigationTest` (5). Comprueba migraciones 1→2→3 preservando el perfil,
reapertura de SQLite/fotos/rutas, purga recuperable y cascadas locales, sesión cifrada y archivo
corrupto, GPS simulado con pantalla apagada, pausa duplicada sin sumar tiempo y finalización.
La interfaz prueba CRUD del perfil con confirmación, borrador tras navegación/recreación,
visor SQLite, alta guiada con foto real seleccionada/coordenadas con coma y estado de servidor
sin configurar. El proveedor de archivos de la foto de prueba existe solo en la variante debug.
Los casos no publican ni eliminan registros de producción del usuario; esta separación no
evita la desinstalación que realiza Gradle.

Además se revisaron capturas reales de las cuatro secciones en API 35: navegación semántica,
colores consistentes, formularios separados y estados vacíos sin contenido ficticio.
También se revisó Inicio en API 26. El arranque posterior a instalar el APK final en API 26
tardó **1.641 ms** (`TotalTime`; `WaitTime`: 1.685 ms). En API 35, el mismo APK final
tardó **4.531 ms** (`TotalTime`; `WaitTime`: 4.563 ms), con arranque en frío confirmado.
Estas mediciones no acreditan el objetivo de dos segundos en el equipo físico de referencia.

La batería API 26 se ejecutó en el AVD nuevo `PaceFit_API26_I4_20261125`; la última API 35,
en `Pixel_4` / `emulator-5556`. Ambos resultados archivados corresponden al mismo SHA-256
indicado abajo, sin cambios posteriores de código. Son 22 casos por API, no 44 casos distintos.

Permisos comprobados en el APK: Internet/estado de red, FGS/FGS de ubicación, notificaciones,
ubicación aproximada/precisa y actividad física; permiso de receptor AndroidX de firma.
Sin cámara, almacenamiento amplio ni ubicación en segundo plano. Se conservan las exclusiones
de backup y transferencia. Salud/rutas permanecen locales; la publicación urbana exige consentimiento.

### Artefacto y reproducción actual

- Archivo: `app/build/outputs/apk/debug/app-debug.apk`.
- Versión: **0.3.0-beta.1**, código 3, firma de desarrollo.
- Tamaño: **19.168.774 bytes**.
- SHA-256: `0ba40d06d2840a24d87642c67dc4f80069b37103af0b651e21739f88b9459f64`.

```powershell
# Solo en un emulador de pruebas sin datos personales.
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --continue --max-workers=2
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
adb shell am start -W -n com.pacefit.ai/.MainActivity
```

Reportes locales no versionados:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/verification/api35-final/report/index.html`
- `app/build/verification/api35-final/results/`
- `app/build/verification/api35-final/artifact.json`
- `app/build/verification/api26-final/report/index.html`
- `app/build/verification/api26-final/results/`
- `app/build/verification/api26-final/artifact.json`
- `app/build/reports/androidTests/connected/debug/index.html` (última ejecución conectada)

### Aceptación externa pendiente

- Proyecto Supabase HTTPS del usuario, SMTP/confirmación real y recorrido Android→servidor en dos dispositivos.
- Gemini configurado solo en servidor y cinco fotografías propias; revisión humana de clasificación/privacidad.
- Caminata/carrera/minibús/Teleférico con pantalla apagada durante 20 minutos; errores de distancia e intervalos GPS.
- Permisos precisa/aproximada/denegada y sensores presentes/ausentes en teléfono físico.
- Reinicio real, falta de espacio, interrupción de borrado, texto grande/accesibilidad.
- Operación diaria de retención y procedimiento probado de backups/PITR ≤30 días.

Ningún resultado local acredita esos pendientes ni cierra por sí solo los tickets originales.
Configuración y despliegue: [`SERVIDOR.md`](SERVIDOR.md).

---

## Histórico: edición local `0.2.0-local.1`

Alcance: PF-001 y adaptaciones locales del Integrante 4 (PF-007/PF-011/PF-015).
Los resultados de la primera beta que aparecen más abajo son históricos y no validan esta edición.

### Verificación automática ejecutada

Fecha: **24 de noviembre de 2026**. Emulador **Pixel_4**, Android 15 (**API 35**),
`x86_64`, dispositivo ADB `emulator-5554`. JDK 21 y SDK de compilación 36.

| Comprobación | Resultado |
|---|---|
| APK debug y APK de pruebas | Compilan correctamente |
| Pruebas unitarias | 32, sin fallos, errores ni omisiones |
| Pruebas instrumentadas ejecutadas | 14, sin fallos, errores ni omisiones |
| Android Lint | 0 errores, 14 advertencias |
| Instalación adicional de la APK final | `adb install -r`: Success |
| Apertura adicional de la APK final | `am start -W`: Status ok, MainActivity, arranque en frío |
| Firma | `apksigner verify --verbose`: verifica firma v2 |
| Permisos de la APK | Ubicación aproximada/precisa y actividad; sin Internet, ubicación en segundo plano, cámara ni almacenamiento |
| Directorios de la instalación | Confirmados `.db`, `-wal`, `-shm` y `files/pacefit/photos/{plogging,reports}` |

Unitarias: 13 de `LocalFeaturesTest`, 9 de `ProfileValidatorTest` y 10 de
`ProfileViewModelTest`. Cubren validación, clasificación conservadora y limpieza de borradores.

Instrumentadas: 2 de `HealthDatabaseTest`, 9 de `LocalStorageTest` y 3 de
`LocalNavigationTest`. Se ejecutaron CRUD, migración 1→2 conservando el perfil,
reapertura de SQLite con datos de todos los módulos, fotos sin metadatos GPS/cámara,
resolución/borrado de reporte y foto, borrado de las cinco tablas/configuración/fotos,
recuperación del marcador de borrado, limpieza de huérfanos y rechazo de rutas/SQL arbitrarios.
También se comprobaron las columnas/rutas del visor, coordenadas manuales sin permiso
de ubicación y borrador de perfil al cambiar de pestaña y recrear la actividad.
Se corrigió el desplazamiento vertical/horizontal de la prueba del visor y se repitió
la batería completa, con las 14 pruebas instrumentadas pasando.

Las pruebas de almacenamiento usan bases y directorios exclusivos de prueba. Las de navegación
no guardan ni eliminan registros del usuario. La persistencia comprobada mediante reapertura
de SQLite **no equivale** a un reinicio del teléfono ni a toda la aceptación manual de la interfaz.

Las 14 advertencias de Lint corresponden a versiones disponibles, target API 35
y recomendación de KSP. No hay baseline ni nuevas supresiones para ocultarlas.
Se utiliza AndroidX ExifInterface para leer orientación y se codifica un JPEG nuevo
sin copiar los metadatos de origen.

### Artefacto y reproducción

- Archivo: `app/build/outputs/apk/debug/app-debug.apk`.
- Versión: `0.2.0-local.1`, código 2, firma de desarrollo.
- Tamaño: **12.303.409 bytes**.
- SHA-256: `39bd0f92c61834a18c770c9948beff5327671cb6e74d6df140ffd936acdb2276`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest --continue
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
adb shell am start -W -n com.pacefit.ai/.MainActivity
```

Reportes locales no versionados:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/outputs/androidTest-results/connected/debug/`

### Aceptación todavía pendiente

- [ ] Recorrido manual completo del CRUD del perfil, confirmaciones y reapertura del proceso.
- [ ] Recorrido real caminando/corriendo/minibús/Teleférico; etiquetas y error de distancia.
- [ ] Ubicación precisa, aproximada y denegada en teléfonos físicos; sensor de pasos presente/ausente.
- [ ] Cinco fotografías reales: formatos, orientaciones, selección cancelada, errores y revisión visual.
- [ ] Reinicio del teléfono con registros ya guardados; confirmar tablas y fotos.
- [ ] Instalación y accesibilidad en API 26 y texto grande.
- [ ] Borrado con falta de espacio/errores reales de almacenamiento y reinicio durante el borrado.

No se ejecutan ni se afirman pruebas de RLS, cuentas, Storage, IA, mapa compartido,
puntos de servidor, BLE, batería de registro en segundo plano o validación clínica.
La detección local se detiene al ocultar la app y no sustituye PF-003.

---

## Histórico: PF-001 `0.1.0-beta.1`

Versión: `0.1.0-beta.1`.

## Verificación automática

| Comprobación | Estado |
|---|---|
| Compilación APK debug | Pasa: APK generado |
| Pruebas unitarias de validación y ViewModel | Pasa: 18 pruebas, 0 fallas, 0 errores, 0 omitidas |
| Android Lint | Pasa: 0 errores, 14 advertencias |
| Compilación de pruebas instrumentadas | Pasa; no equivale a ejecutarlas |
| Pruebas instrumentadas en dispositivo | No ejecutadas: `adb devices` no muestra equipos |
| Instalación y smoke test | No ejecutados: falta dispositivo/emulador |
| Inspección de permisos del APK | Pasa: sin Internet ni permisos sensibles |
| Firma del APK | Pasa: `apksigner verify`, firma debug v2 |

Las advertencias de Lint corresponden a versiones más recientes disponibles,
target API 35 y recomendación de KSP en lugar de kapt. No se creó baseline ni
se desactivó el chequeo de compatibilidad: se corrigió un atributo de tema API 27
para conservar soporte API 26.

Se ejecutaron 9 casos de `ProfileValidatorTest` y 9 de `ProfileViewModelTest`.
Los tests de Room se compilaron pero no se ejecutaron, por falta de dispositivo.

El permiso `com.pacefit.ai.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` se genera
por AndroidX y es de firma; no es un permiso sensible ni habilita acceso a Internet.

## Artefacto local

- Archivo: `app/build/outputs/apk/debug/app-debug.apk`.
- Tamaño: 10.221.560 bytes.
- SHA-256: `1202e6cf7befcedd3ca0558e89e28a343b678083ca55da76730616463162e4d6`.
- `apksigner` verifica una firma debug. La instalación aún no está comprobada.

No se ha ejecutado ninguna prueba de campo, BLE, GPS, batería, usuario o backend.
Esos módulos aún no existen y pertenecen a tickets posteriores.

## Comandos reproducibles

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest
```

Reportes locales (no versionados):

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/outputs/androidTest-results/connected/`

## Checklist manual para cerrar PF-001

- [ ] Instalar y abrir en API 26+; registrar modelo, API y versión.
- [ ] Crear perfil válido con coma decimal.
- [ ] Cerrar completamente, reabrir y verificar persistencia.
- [ ] Editar y reabrir; comprobar que existe un solo perfil.
- [ ] Probar campos vacíos, valores límite y teclado decimal.
- [ ] Rotar con formulario sin guardar y cambiar de pestaña.
- [ ] Cancelar borrado y comprobar que conserva el perfil.
- [ ] Confirmar borrado; reabrir y comprobar perfil vacío.
- [ ] Comprobar funcionamiento en modo avión y texto grande.
- [ ] Ejecutar las pruebas instrumentadas de Room.

La compilación correcta no sustituye este checklist. PF-001 permanece en Testing
hasta reunir evidencia de ejecución en dispositivo o emulador.
