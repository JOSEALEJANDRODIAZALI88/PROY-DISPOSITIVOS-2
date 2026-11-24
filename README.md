# PaceFit AI — Primera beta

Aplicación Android en español para el proyecto descrito en
[`recursos/PaceFit_AI_informe_final_Kanban.docx`](recursos/PaceFit_AI_informe_final_Kanban.docx).

**Versión:** `0.1.0-beta.1` · **Ticket entregado:** PF-001 · **Rama:** `primera-version`.

## Qué funciona en esta entrega

- Navegación entre inicio, perfil y listado informativo de próximos módulos.
- Crear, consultar, editar y eliminar un perfil de salud en Room.
- Validar peso, estatura y edad; aceptar coma/punto decimal; sexo opcional.
- Guardar el perfil de forma local y recuperar los datos al volver a abrir.
- Sin cuenta, Internet, analítica ni permisos sensibles.
- Confirmación antes de eliminar; exclusión de backup y transferencia Android.

**Todavía no incluye** GPS, sensores, BLE, cálculos de salud, comidas, Supabase,
IA, mapa, social, PDF, aclimatación ni dibujo. Están desglosados en
[`TICKETS.md`](TICKETS.md), con dependencias y criterios de aceptación.
Las tarjetas de próximos módulos son informativas, no funciones simuladas.

## Abrir y compilar

Requisitos: **JDK 21**, Android SDK **36**, Android Studio y conexión para descargar dependencias.
La aplicación funciona desde Android 8.0 (API 26); target API 35.
El wrapper usa Gradle 8.13, AGP 8.13.0 y Kotlin 2.2.10.
El código fuente y bytecode usan Java 17.

Abrir esta carpeta, **no `C:\Users\DIAZ`**, como proyecto Android Studio.
Configurar el SDK en Android Studio, `ANDROID_HOME` o `local.properties` (ignorado por Git).

En Windows, desde esta carpeta:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

En Linux/macOS:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

También se publica un artefacto APK en los workflows de GitHub Actions cuando el build finaliza correctamente.
La firma debug es solo para desarrollo/beta, **no una publicación de Play Store**.

## Pruebas

Unitarias: validación y ViewModel, incluidos errores, edición, guardado y borrado.
Instrumentadas: creación, actualización, borrado y reapertura de Room.

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

La segunda orden requiere un dispositivo/emulador conectado.
Compilar pruebas instrumentadas **no significa ejecutarlas**.
Resultados y límites reales: [`docs/VERIFICACION.md`](docs/VERIFICACION.md).

## Estructura

- `domain/`: perfil y validación sin Android.
- `data/`: entidad, DAO, base Room y repositorio.
- `ui/`: estado con ViewModel/StateFlow y pantallas Compose.
- `PaceFitApplication`: una base de datos por proceso.
- `app/schemas/`: esquema Room versionado; cambios futuros requieren migraciones.

## Privacidad de esta beta

Solo existe un perfil local; no está vinculado a cuentas remotas.
Peso, estatura, edad y sexo no salen de la aplicación.
No hay SDK de terceros para telemetría ni permiso Internet en el manifiesto.
Se deshabilitan backup y transferencia de datos; un dispositivo comprometido no está
protegido por estas medidas. Room no aporta cifrado adicional de base de datos.
No se afirma borrado forense del almacenamiento al eliminar el perfil.

Desinstalar o borrar los datos de la app elimina su almacenamiento local.
No se solicitan claves de Supabase/Gemini porque esas integraciones siguen pendientes.
La app orientará, no diagnosticará; este primer ticket no calcula recomendaciones.

## Flujo de tickets

Comenzamos por PF-001 para obtener una base instalable rápidamente.
Consultar [`TICKETS.md`](TICKETS.md) antes de iniciar otro módulo.
No se consideran terminadas pruebas de campo ni funcionalidades futuras.

Repositorio objetivo: <https://github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2>.
Este proyecto tiene su propio `.git`; no cambiar el remoto de la carpeta de usuario.
