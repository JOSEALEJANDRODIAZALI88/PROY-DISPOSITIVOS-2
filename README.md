# PaceFit AI

Android en español · **0.3.0-beta.1** · rama `integrante-4-diaz`.
Fuente: [`recursos/PaceFit_AI_informe_final_Kanban.docx`](recursos/PaceFit_AI_informe_final_Kanban.docx).

Resumen de lo implementado, pruebas aprobadas y pendientes: **[ESTADO_ENTREGA.md](ESTADO_ENTREGA.md)**.

## Una interfaz, cuatro espacios

- **Inicio:** resumen de tus registros reales, actividad reciente y accesos directos.
- **Actividad:** iniciar, pausar, continuar y finalizar; notificación de grabación, GPS con pantalla
  apagada, pasos/barómetro opcionales, mapa, historial y detalle. Rutas en SQLite, no en Supabase.
  Los segmentos vehículo/no determinado se conservan y no suman metros deportivos.
  El detector combina velocidad, pasos disponibles y actividad indicada por el usuario; no equivale
  a reconocimiento automático validado en campo.
- **Ciudad:** mapa compartido autenticado y registros privados separados. Alta guiada:
  foto con vista previa → clasificación manual/IA corregible → ubicación → consentimiento.
  Publicación explícita, fotos privadas sin EXIF y puntos recalculados por el servidor.
- **Perfil:** salud privada separada de cuenta pública. CRUD de salud; cuenta, ajustes, privacidad
  y visor SQLite como pantallas secundarias con navegación atrás.

Los formularios de plogging e incidencias no comparten borradores. Los historiales muestran
resultados progresivamente sin truncar los totales a las primeras 50/200 filas.
Se mantienen los datos existentes mediante migraciones Room 1→2→3, sin reiniciar la base.

## Estado real

El código Android y el backend están implementados. El backend local pasó pruebas reales de
Auth/RLS/Storage/Edge Functions con tres usuarios, incluida cuota concurrente de IA y borrado de cuenta.
El **25/11/2026** también se creó y desplegó el proyecto exclusivo **PaceFit AI** en Supabase,
**plan Free**, región São Paulo, sin modificar los otros proyectos del usuario.
Sus cuatro migraciones y cuatro Edge Functions ya están desplegadas; **27 comprobaciones HTTPS
contra el servidor cloud aprobaron**, con cuentas desechables propias.
El APK ya está recompilado y probado contra ese servidor desde Android 8 y 15.
La URL y clave pública están configuradas localmente, nunca la clave de servidor.
**Pendientes: entrega real de correo, clave Gemini e inferencia real.** SMTP Gmail está guardado;
la confirmación por código en Cuenta ya pasó las pruebas contra Supabase en Android 8 y 15.
La confirmación de correo
sigue activada; no se desactiva para aparentar un registro funcional.
Sin configuración, Cuenta dice «Servidor sin conectar»: no inventa sesiones, mapas compartidos ni IA.
El perfil, las rutas y los registros urbanos privados funcionan sin cuenta.

Las pruebas de campo GPS y la aceptación con correos/fotografías reales requieren ejecución externa.
La heurística de transporte no es una clasificación clínicamente validada ni demuestra
una recogida real. No se afirma «100 % verificado» por compilar.
Evidencia, comandos y pendientes: [`docs/VERIFICACION.md`](docs/VERIFICACION.md).

Alcance: **PF-001, PF-007, PF-011 y PF-015**, con dependencias necesarias de PF-003/010
y únicamente la tarea de fotografía de PF-012. No se implementan BLE, comidas, asesor semanal,
chat, zonas competitivas, PDF, aclimatación ni dibujo. [`TICKETS.md`](TICKETS.md) conserva los
requisitos originales y distingue desarrollo, pruebas y despliegue.

## Compilar e instalar

JDK **21**, SDK **36**, min API **26**, target **35**, Gradle **8.13**, AGP **8.13.0**,
Kotlin **2.2.10**, bytecode Java **17**.
Abrir esta carpeta como proyecto, **no `C:\Users\DIAZ`**. `local.properties` es local y no se versiona.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

Linux/macOS: sustituir `.\gradlew.bat` por `./gradlew`.
APK: **`app/build/outputs/apk/debug/app-debug.apk`**, firma debug para desarrollo, no Play Store.

## Activar cuentas, publicación e IA

Guía completa: **[`docs/SERVIDOR.md`](docs/SERVIDOR.md)**.

El proyecto del propietario ya está desplegado. Para otra instalación:
1. Crear un proyecto Supabase y aplicar `supabase/migrations`.
2. Desplegar las cuatro Edge Functions; configurar SMTP manteniendo la confirmación por correo.
3. Configurar `SUPABASE_URL` HTTPS y `SUPABASE_ANON_KEY` pública en `local.properties` y recompilar.
4. Guardar `GEMINI_API_KEY` y `GEMINI_MODEL` **solo en los secretos del servidor**.

Nunca poner `service_role`/claves Gemini en Android ni enviarlas por chat.
La app rechaza HTTP y certificados no confiables. Para el backend local HTTP se ofrece una
prueba Node; integrar Android local exige un proxy HTTPS válido o un proyecto HTTPS de desarrollo.

```powershell
npx --yes supabase start
npx --yes supabase migration up
npx --yes supabase test db
# Otra terminal:
npx --yes supabase functions serve --env-file .env.example
# Con las funciones sirviendo:
node scripts/test-backend.mjs
```

Docker Desktop y Node/npm necesarios solo para el backend local.

## SQLite, directorios y pruebas Android

**Perfil → Datos SQLite** muestra nueve tablas, columnas, recuentos y hasta 50 filas por consulta.
También: Android Studio → App Inspection → Database Inspector.
Esquema, rutas, migraciones y consultas: [`docs/SQLITE.md`](docs/SQLITE.md).

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Usar un **emulador de pruebas sin datos personales**: Gradle puede desinstalar la aplicación
y borrar sus datos al terminar. Compilar las pruebas no equivale a ejecutarlas.
El runner instrumentado usa una base, directorio y sesión de prueba separados, no los registros
de producción. La prueba FGS usa GPS simulado y no reemplaza caminar/correr/viajar con teléfono real.

Verificación del 25/11/2026: **32 unitarias**, **24 instrumentadas en cada API 26 y 35**
con el APK conectado a Supabase real, **34 pruebas SQL** y **27 comprobaciones HTTP locales**,
además de **27 comprobaciones HTTPS cloud**, aprobadas. Lint: **0 errores y 17 advertencias**.
La prueba Android cloud requiere la cuenta desechable del controlador descrito en
`docs/SERVIDOR.md`; sin ese fixture se omite, no se cuenta como aprobada.
El caso cloud rechaza un código incorrecto y acepta uno generado por Auth para la cuenta de
prueba. Esto comprueba la verificación OTP, no la entrega de un mensaje por Gmail.
Resultados, APK y límites en `docs/VERIFICACION.md`.
La recompilación posterior a la publicación volvió a aprobar las 32 unitarias y Lint.
Generó un APK distinto: las 24 instrumentadas por API corresponden al artefacto previo,
no se atribuyen al nuevo hash. Véanse el SHA vigente y esa validación pendiente en
[`ESTADO_ENTREGA.md`](ESTADO_ENTREGA.md).

## Privacidad

- Peso, talla, edad, sexo y rutas completas no se envían a Supabase/Gemini.
- Fotos urbanas solo se publican con consentimiento; la sugerencia IA tiene consentimiento separado.
- Las fotos se recomprimen sin EXIF en `files/pacefit/photos/{plogging,reports}`.
- Sesión de cuenta cifrada mediante Android Keystore; Room **no** añade cifrado propio.
- Sin analítica, cámara, almacenamiento amplio ni `ACCESS_BACKGROUND_LOCATION`.
  Ubicación/pasos/notificaciones se solicitan al iniciar sus funciones; el perfil no los necesita.
- Grabación FGS iniciada desde la app visible, con notificación; no se reinicia GPS automáticamente
  tras muerte del proceso. La actividad se recupera como interrumpida.
- Backup y transferencia Android siguen excluidos. Sin promesa de borrado forense ni de originales
  del selector. El operador cloud debe demostrar retención de respaldos ≤30 días.
- OpenStreetMap recibe solicitudes de teselas/IP/área visible; no se prometen mapas offline.

## Estructura

`domain/`: validación y detector · `data/`: Room, migraciones, directorios, repositorios, FGS y cliente
HTTPS · `ui/`: Navigation Compose/pantallas/ViewModels · `supabase/`: SQL/RLS/Storage/Edge Functions
y pgTAP · `scripts/`: integración local/cloud, administración e ingreso privado de configuración.

Repositorio independiente: <https://github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2>.
La actualización `0.3.0-beta.1` está publicada en
[`integrante-4-diaz`](https://github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2/tree/integrante-4-diaz),
con el historial `0.2.0-local.1` conservado. No cambiar el remoto del repositorio padre ni
sobrescribir `main` o `primera-version`. La publicación no implica cerrar los pendientes de aceptación.
