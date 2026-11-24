# PF-001 — Registro de verificación

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
