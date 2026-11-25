# PaceFit AI — Edición local SQLite

Aplicación Android en español para el proyecto descrito en
[`recursos/PaceFit_AI_informe_final_Kanban.docx`](recursos/PaceFit_AI_informe_final_Kanban.docx).

**Versión:** `0.2.0-local.1` · **Alcance acordado:** Integrante 4 (PF-001, PF-007 y adaptaciones locales de PF-011/PF-015).
**Rama de esta edición:** `integrante-4-diaz`.
La primera entrega `0.1.0-beta.1` se publicó en `primera-version`.

## Qué funciona en esta entrega

- Navegación entre inicio, perfil, transporte, ciudad y datos.
- Crear, consultar, editar y eliminar un perfil de salud en Room.
- Validar peso, estatura y edad; aceptar coma/punto decimal; sexo opcional.
- Guardar el perfil de forma local y recuperar los datos al volver a abrir.
- Detección visible de transporte con GPS, pasos si están disponibles e indicación manual de actividad.
  Ventana configurable y persistente; tramos de vehículo o no determinados excluidos de futuras métricas deportivas.
- Plogging con cantidad confirmada y puntos **locales** 5/10/20; reportes bache/basura/luminaria/otro,
  con fotografía, coordenadas, resolución y borrado propios.
- Fotografías recomprimidas sin EXIF en directorios internos persistentes, no en caché.
- Visor de las cinco tablas SQLite con columnas, recuentos y hasta 50 filas; sin SQL arbitrario.
- Borrado confirmado de todos los registros y fotos locales, con reintento tras un borrado interrumpido.
- Sin cuenta, Internet ni analítica. Ubicación precisa y pasos opcionales, solicitados al usar GPS.
- Sin permisos de cámara/almacenamiento ni acceso a ubicación en segundo plano.
- Exclusión de backup y transferencia Android.

**Todavía no incluye** registro GPS en segundo plano (PF-003), reconocimiento automático de actividad,
BLE, cálculos de salud, comidas, Supabase, seguridad/borrado de cuentas remotas, IA, mapa compartido,
puntos verificados en servidor, social, PDF, aclimatación ni dibujo. Están desglosados en
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

Unitarias: perfil/ViewModel, validación de coordenadas, puntos y detector de transporte.
Instrumentadas: Room persistente, migración 1→2, EXIF, borrado de fotos/datos y recuperación de borrado interrumpido.

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

La segunda orden requiere un dispositivo/emulador conectado.
Compilar pruebas instrumentadas **no significa ejecutarlas**.
La ejecución del 24/11/2026 pasó 32 pruebas unitarias y 14 instrumentadas en API 35,
con Lint sin errores (14 advertencias). La aceptación manual y de campo sigue pendiente.
Resultados y límites reales: [`docs/VERIFICACION.md`](docs/VERIFICACION.md).

## Estructura

- `domain/`: perfil, validación y heurística de transporte sin Android.
- `data/`: entidades, DAO, Room v2, migración, directorios de fotos y adaptador GPS/pasos visible.
- `ui/`: estado con ViewModel/StateFlow y pantallas Compose.
- `PaceFitApplication`: una base de datos por proceso.
- `app/schemas/`: esquemas Room versionados; se conserva v1 y se migra a v2 sin perder el perfil.

## Ver tablas y directorios

Abre **Datos** dentro de la app. También puedes inspeccionar la base con
**Android Studio → App Inspection → Database Inspector** mientras ejecutas la APK debug.
Detalles, tablas y consultas: [`docs/SQLITE.md`](docs/SQLITE.md).

## Privacidad de esta beta

Solo existe un perfil local; no está vinculado a cuentas remotas.
Peso, estatura, edad y sexo no salen de la aplicación.
No hay SDK de terceros para telemetría ni permiso Internet en el manifiesto.
Se deshabilitan backup y transferencia de datos; un dispositivo comprometido no está
protegido por estas medidas. Room no aporta cifrado adicional de base de datos.
No se afirma borrado forense del almacenamiento. El borrado de una foto elimina la copia privada,
no el original elegido del proveedor de imágenes.
El seguimiento se detiene al ocultar la app; las ventanas incompletas no se guardan.
Sin sensor de pasos, la velocidad por sí sola no demuestra transporte: se conserva «No determinado»
salvo indicación explícita. La heurística no está validada en campo.

Desinstalar o borrar los datos de la app elimina su almacenamiento local.
No se solicitan claves de Supabase/Gemini porque esas integraciones siguen pendientes.
La app orientará, no diagnosticará; esta entrega no calcula recomendaciones.

## Flujo de tickets

Se implementa únicamente el alcance local acordado del Integrante 4, sin adelantar otros tickets.
Los requisitos remotos originales se conservan en [`TICKETS.md`](TICKETS.md), no se sustituyen por controles locales.
No se consideran terminadas pruebas de campo, seguridad remota ni funcionalidades futuras.

Repositorio objetivo: <https://github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2>.
Este proyecto tiene su propio `.git`; no cambiar el remoto de la carpeta de usuario.
