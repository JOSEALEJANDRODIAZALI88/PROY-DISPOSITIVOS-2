# PaceFit AI — Registro de verificación

## Edición local `0.2.0-local.1`

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
