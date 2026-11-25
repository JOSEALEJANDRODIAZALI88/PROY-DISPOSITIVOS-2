# SQLite persistente y directorios

Room utiliza SQLite real; no es una base en memoria, un archivo JSON ni un servicio remoto.
`PaceFitApplication` mantiene una sola instancia de `pacefit-health.db`.

## Dónde se guarda

En la instalación Android del usuario actual:

```text
<dataDir>/
├── databases/
│   ├── pacefit-health.db
│   ├── pacefit-health.db-wal   # puede existir mientras SQLite está abierto
│   └── pacefit-health.db-shm
├── files/pacefit/
│   ├── erase.pending          # solo durante un borrado, para recuperarlo
│   └── photos/
│       ├── plogging/<UUID>.jpg
│       └── reports/<UUID>.jpg
└── no_backup/
    ├── pacefit-session.enc    # tokens cifrados; clave en Android Keystore
    ├── pacefit-account-delete.request  # solo si se pidió eliminar cuenta
    └── pacefit-account-delete.pending  # solo tras acuse real del servidor
```

En un dispositivo típico `<dataDir>` es `/data/user/0/com.pacefit.ai`.
La pantalla **Perfil → Datos SQLite** muestra las rutas reales, sin asumir un usuario concreto.
No se usa la caché ni un directorio público para las fotos persistentes.
No se necesitan permisos de almacenamiento ni `MANAGE_EXTERNAL_STORAGE`.

Cerrar/reabrir la app o reiniciar el teléfono no borra estos archivos.
Desinstalar, borrar datos desde Android o confirmar el borrado en la app sí elimina los datos locales.
No hay sincronización de salud ni rutas. Solo los registros urbanos publicados expresamente
tienen una copia remota; borrar su copia local no borra la publicación.

## Nueve tablas del esquema 3

| Tabla | Contenido |
|---|---|
| `health_profile` | Perfil único, clave 1: peso, estatura, edad, sexo, actualización. |
| `transport_segments` | Ventanas GPS, metros estimados, velocidad media, pasos opcionales, actividad indicada, clasificación y elegibilidad deportiva. |
| `plogging_entries` | Cantidad confirmada, puntos locales, coordenadas, nombre de foto y fecha. |
| `urban_reports` | Categoría confirmada, descripción opcional, coordenadas, foto, resuelto y fecha. |
| `local_settings` | Ventana del detector (15–120 s), clave 1; por defecto 30 s. |
| `activity_sessions` | Inicio, fin, estado y tiempo activo acumulado; solo una sesión abierta. |
| `route_points` | Puntos de ruta: fecha, coordenadas, precisión, velocidad, altitud/barómetro opcionales; FK a sesión. |
| `session_segments` | Vincula las ventanas clasificadas con su sesión; FK a segmento/sesión. |
| `city_publications` | Asociación del registro local con su publicación/propietario remoto, sin duplicar puntos. |

`app/schemas/com.pacefit.ai.data.HealthDatabase/1.json` conserva el esquema inicial.
`2.json` conserva la entrega local. `3.json` incorpora sesiones/rutas/publicaciones.
`MIGRATION_1_2` y `MIGRATION_2_3` crean tablas sin borrar los registros existentes;
no hay migración destructiva.

## Consultar desde el teléfono

1. Abre **Perfil → Datos SQLite**.
2. Desliza los nombres de tablas y elige una.
3. Pulsa **Actualizar tabla** después de editar datos en otra pestaña.
4. Verás columnas, cantidad total y hasta 50 filas. La tabla se desplaza horizontalmente.

El visor es de solo lectura y usa una lista cerrada de tablas. No ejecuta SQL escrito por el usuario
ni exporta automáticamente información de salud.

## Consultar desde Android Studio

1. Instala/ejecuta `app-debug.apk` en un emulador o teléfono API 26+.
2. Abre **View → Tool Windows → App Inspection → Database Inspector**.
3. Selecciona `com.pacefit.ai`, después `pacefit-health.db`.
4. Expande las tablas o ejecuta, por ejemplo:

```sql
SELECT * FROM health_profile WHERE id = 1;
SELECT * FROM local_settings;
SELECT classification, includedInMetrics, distanceMeters FROM transport_segments;
SELECT quantity, points, photoName FROM plogging_entries ORDER BY createdAt DESC;
SELECT kind, resolved, photoName FROM urban_reports ORDER BY createdAt DESC;
SELECT id, state, durationMillis FROM activity_sessions ORDER BY startedAt DESC;
SELECT * FROM route_points WHERE sessionId = 'ID_DE_SESION' ORDER BY timeMillis, id;
SELECT * FROM session_segments WHERE sessionId = 'ID_DE_SESION';
SELECT localId, remoteId, kind FROM city_publications;
```

**Device Explorer** permite ver `data/data/com.pacefit.ai/files/pacefit/photos/` en la APK debug.
Un gestor de archivos común no puede abrir los directorios privados: es deliberado.
No copies solo `.db` mientras está abierto: puede haber cambios pendientes en WAL.
Si necesitas extraer datos para revisión, hazlo explícitamente con las herramientas de Android Studio
y evita compartir información real de salud o coordenadas.

## Fotos, consistencia y borrado

- El selector concede acceso solo a la imagen elegida. Puede conservarse permiso de lectura de ese
  documento para recuperar el borrador; las copias guardadas no dependen de que el proveedor conserve el original.
- Se admiten imágenes decodificables hasta 20 MB; se reduce el lado mayor a como máximo 1600 px
  y se vuelve a codificar en JPEG calidad 80. Se respeta la orientación y no se copia EXIF.
- La vista previa no conserva una segunda copia en la caché de imágenes. Las copias temporales
  para IA se eliminan al terminar y se limpian al iniciar tras una interrupción.
- Un nombre UUID evita reutilizar nombres originales y rutas arbitrarias. La tabla guarda solo el nombre.
- Si falla la inserción, se elimina la nueva copia. Al iniciar se eliminan fotos privadas sin referencias.
- El borrado completo crea primero `erase.pending`, elimina las copias privadas y borra las nueve tablas
  en una transacción SQLite. Si se interrumpe, se reintenta antes de acceder al almacenamiento.
- El borrado excluye originales del selector y archivos ajenos. No promete eliminación forense
  de bloques flash, WAL o respaldos previos externos.

Los puntos SQLite son locales. Las publicaciones reciben puntos calculados en PostgreSQL;
ninguna de ambas cantidades prueba que la recogida ocurrió. Ver arquitectura, configuración,
consentimientos y borrado de cuenta en [`SERVIDOR.md`](SERVIDOR.md).

La notificación FGS mantiene la grabación al apagar pantalla; Room conserva cada punto aceptado.
Tras muerte del proceso no se inicia GPS solo: la sesión abierta pasa a interrumpida.
Las pruebas instrumentadas usan `pacefit-instrumentation.db`, `files/pacefit-instrumentation` y
un namespace de sesión separado; nunca purgan estos archivos de producción.
