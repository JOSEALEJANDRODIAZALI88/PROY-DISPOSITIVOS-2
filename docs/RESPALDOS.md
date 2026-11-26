# Respaldos de datos de aplicación — alcance y límites

Proyecto autorizado: `ymfypllkcrqwfwihqzpf`, **Free**. Estas herramientas no activan
facturación, no cambian otros proyectos y no restauran datos sobre producción.

## Qué se respalda

`scripts/cloud-backup.py` captura perfiles públicos activos, registros urbanos,
sus fotografías privadas, uso de IA y cuota reciente. Excluye cuentas en proceso de borrado.
Los archivos se cifran mediante **DPAPI del usuario Windows actual** antes de escribirlos en:

```text
supabase/.temp/application-backups/pacefit-FECHA-UUID.pfbk
```

Ese directorio se ignora en Git. No contiene una clave de cifrado exportada. El archivo
puede contener fotografías y ubicación de publicaciones consentidas; no compartirlo.
**No incluye** salud/rutas SQLite, contraseñas o sesiones Auth, ni secretos del servidor.
No es un respaldo completo del proveedor, PITR ni una copia portable garantizada a otro equipo.
Conservar el perfil/DPAPI Windows es requisito; perderlo puede impedir descifrar la copia.

## Comandos

```powershell
# Crear copia de los datos de aplicación actuales; solo el proyecto autorizado:
python scripts/cloud-backup.py create

# Borrar únicamente archivos gestionados que hayan alcanzado 29 días:
python scripts/cloud-backup.py prune

# Ensayo con DOS cuentas temporales propias, sin restaurar la base cloud:
python scripts/cloud-backup.py rehearse
```

La credencial administrativa se obtiene del llavero CLI ya autorizado, solo en memoria.
No añadir contraseñas, claves o archivos `.pfbk` al chat, Git o al APK.
Los límites rechazan copias de más de 1.000 publicaciones o del tamaño admitido
por el script; no se declara éxito truncando datos.

## Qué comprueba el ensayo

1. Crear dos cuentas/entradas/fotos sintéticas propias y una reserva de cuota sin llamar a Gemini.
2. Capturar, cifrar, descifrar y comparar la copia.
3. Eliminar realmente una cuenta temporal por el endpoint y comprobar su ausencia.
4. Volver a consultar Auth/perfiles activos y excluir esa cuenta y sus fotos del plan.
5. Restaurar los datos restantes en **PostgreSQL 17 desechable**, sin red ni puertos del host.
   Es un esquema de ensayo de datos/FK/puntos generados, no una restauración de toda la
   configuración productiva, RLS, extensiones o Auth.
6. Restaurar la foto restante a un directorio temporal privado y verificar su SHA-256.
7. Rechazar restauraciones caducadas y comprobar la purga sobre una copia temporal
   con reloj adelantado. No se simula que hayan transcurrido realmente 29 días.
8. Eliminar únicamente las cuentas y recursos del ensayo. La evidencia final se escribe
   después de verificar su limpieza; no contiene fotos ni identidades.

Resultados: `app/build/verification/application-backup/result-*.json`.
El contenedor y los directorios de restauración son temporales; el ensayo no publica
actividad real, no restaura cuentas eliminadas ni prueba una recuperación Auth/PITR.

## Retención y operación que siguen pendientes

- `load()` rechaza copias de 29 días y `prune` elimina solo nombres/formato gestionados,
  sin recursión ni seguir enlaces. Los 29 días dejan margen frente al máximo de 30.
- **No se ha instalado una tarea automática**. Ejecutar la purga diariamente y registrar
  su resultado; un equipo apagado o una copia externa no controlada rompe esa garantía.
- Inventariar copias del proveedor y externas. Antes de reabrir un servicio recuperado,
  aplicar de nuevo las eliminaciones y verificar las cuentas actuales; nunca restaurar
  directamente una copia antigua sin esa comprobación.
- La recuperación integral, portabilidad, operación diaria y retención de todas las copias
  del proveedor deben aceptarse aparte. No contratar PITR u otro plan de pago sin autorización.
- No marcar PF-011 como Done solo por aprobar el ensayo de datos de aplicación.

## Recuperación tras una interrupción

Las herramientas de aceptación anotan antes de crear Auth un diario de intención en
`supabase/.temp/acceptance-fixtures/`. Solo guarda el identificador/alias sintético
de su propia prueba; no contraseñas ni tokens.

Si se interrumpió un proceso, comprobar primero que no queden otras pruebas de aceptación
activas y ejecutar:

```powershell
python scripts/acceptance_common.py recover-fixtures
```

La limpieza verifica proyecto, ejecución y propiedad administrativa antes de borrar.
No enumera ni elimina usuarios ajenos. Ante una discrepancia, se detiene para revisión.
