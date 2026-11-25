# Supabase, Gemini y despliegue

El **25/11/2026**, con autorización del propietario, se creó y desplegó **PaceFit AI**:

- Proyecto: `ymfypllkcrqwfwihqzpf`; región **São Paulo (`sa-east-1`)**, instancia `nano`.
- Plan de la organización comprobado: **Free**. No se activó facturación ni se cambiaron otros proyectos.
- API: <https://ymfypllkcrqwfwihqzpf.supabase.co>.
- Panel: <https://supabase.com/dashboard/project/ymfypllkcrqwfwihqzpf>.
- Cuatro migraciones y cuatro Edge Functions desplegadas; bucket `city-photos` privado y cron de retención activo.
- 27 comprobaciones reales por HTTPS aprobadas con tres cuentas desechables propias.
- Confirmación por correo **activada**, mínimo 10 caracteres. SMTP Gmail guardado;
  entrega real e inferencia Gemini aún pendientes.

Las cuentas de prueba se crean confirmadas mediante administración **solo para verificar la integración**;
no se desactiva la confirmación para usuarios reales ni se considera probado el envío de correo.
Las pruebas locales siguen disponibles en Docker. Ni una prueba local ni la prueba cloud acreditan
inferencia Gemini, entrega SMTP o eliminación operativa de backups del proveedor.

### Administración segura desde esta máquina Windows

`scripts/cloud-admin.py` usa el inicio de sesión autorizado de Supabase CLI en Windows Credential
Manager; comprueba proyecto/estado/plan Free antes de operar. No imprime tokens ni guarda claves
de administración en Android. El registro de creación y contraseña cifrada DPAPI están en
`supabase/.temp/` (ignorado); no compartir ni versionar ese directorio.

```powershell
python scripts/cloud-admin.py audit
python scripts/cloud-admin.py status
# Prueba HTTPS con sus propias cuentas; requiere Gemini todavía sin configurar:
python scripts/cloud-admin.py smoke
```

`configure` sincroniza únicamente URL/clave pública con `local.properties` y exige confirmación de email.
`android-smoke <serial>` es optativo: instala el APK y las pruebas en el emulador desechable autorizado,
inyecta por stdin una cuenta temporal y verifica el cliente Android real; nunca empaqueta `service_role`.
Los usuarios y objetos de cada prueba se limpian por sus propios UUID, sin reiniciar el proyecto.
`status` muestra únicamente indicadores de configuración, nunca claves ni contraseñas.

### Confirmación dentro de Android, sin enlaces de retorno

`supabase/templates/confirmation.html` envía un código de un solo uso, no una URL con tokens.
En **Perfil → Cuenta**, el registro abre la entrada del código; también existe
**«Ya tengo un código de confirmación»** para volver después de cerrar la aplicación.
El proyecto mantiene OTP de 8 dígitos; no se redujo la seguridad del servidor. El cliente
acepta también 6 para entornos que utilicen esa longitud. El código no se conserva al rotar.

```powershell
python scripts/cloud-admin.py configure-confirmation
```

La plantilla ya se aplicó al proyecto autorizado. La acción mantiene la confirmación obligatoria
y no envía correos por sí sola. Verificar OTP consulta Auth por HTTPS; una sesión solo se guarda
tras una respuesta válida. «Solicitar otro código» respeta los límites del proveedor/Auth.
La prueba Android usa un OTP administrativo desechable, sin enviar correo: no acredita Gmail.
Para aceptar el envío hay que crear una cuenta propia en la app, recibir el mensaje y confirmar
el código allí, nunca en el chat. El código no se introduce en `cloud-admin.py`.

### Entrada privada de SMTP

Con un servicio SMTP gratuito ya autorizado, abrir en una terminal privada:

```powershell
python scripts/configure-smtp.py
```

El asistente comprueba el proyecto/plan, pide la contraseña oculta, verifica certificado TLS y
autenticación SMTP real (587/STARTTLS o 465/TLS) antes de guardarla únicamente en Supabase.
No crea cuentas, activa pagos ni guarda la contraseña en archivos. No sobrescribe un SMTP
preexistente. Con Gmail se necesita una contraseña de aplicación, no la contraseña de Google.
Esto no acredita entrega real ni el flujo de confirmación/retorno, que deben probarse aparte.

El propietario eligió Gmail para la beta. El formulario privado permite pegar con Ctrl+V y ver
caracteres enmascarados, sin mostrar la contraseña:

```powershell
python scripts/configure-smtp.py --gmail-gui
```

Exige una cuenta Gmail propia con verificación en dos pasos y una contraseña de aplicación
de 16 caracteres, creada en <https://myaccount.google.com/apppasswords>. No usar la contraseña
normal de Google. La página puede no estar disponible en cuentas gestionadas o con Protección
Avanzada; en ese caso elegir otro SMTP, no debilitar la seguridad de la cuenta.
No se guarda la contraseña en `.env`, argumentos ni registros; se verifica TLS antes de enviarla.
Esta opción es para la beta y no promete capacidad de correo de producción.

## Qué sale del teléfono

| Dato | Destino |
|---|---|
| Peso, estatura, edad y sexo | Solo Room/SQLite del teléfono. |
| Sesiones, rutas completas, pasos, altitud y clasificación del transporte | Solo Room/SQLite. |
| Correo/contraseña | Supabase Auth por HTTPS; sesión cifrada con Android Keystore. |
| Nombre público y consentimiento | Supabase; independiente del perfil de salud. |
| Foto JPEG sin EXIF, categoría, descripción y coordenadas de un registro urbano | Solo al confirmar expresamente su publicación; visibles a miembros autenticados. |
| Foto para sugerencia IA | Solo con consentimiento separado; teléfono → `ai-gateway` → Gemini. Sin coordenadas, salud ni identificadores en el payload enviado al modelo. |
| Área visible/IP al cargar teselas | OpenStreetMap, para representar el mapa. Sin descarga masiva ni garantía offline. |

No fotografiar personas identificables ni publicar imágenes ajenas sin permiso. La sugerencia IA es
corregible; no verifica que una recogida ocurrió. Los puntos de publicaciones se calculan en PostgreSQL
(poco/medio/mucho = 5/10/20), nunca se aceptan puntos enviados por el cliente.
Los puntos guardados **solo localmente** son estimaciones y no una clasificación competitiva.

## 1. Ejecutar backend local

Requisitos: Docker Desktop operativo, Node.js/npm, puertos 54320–54324 libres.
Desde la raíz de este repositorio:

```powershell
npx --yes supabase start
npx --yes supabase migration up
npx --yes supabase test db
```

En otra terminal, dejar sirviendo las funciones:

```powershell
npx --yes supabase functions serve --env-file .env.example
```

Sin claves Gemini, `.env.example` permite probar el resto y el error de IA indisponible:

```powershell
node scripts/test-backend.mjs
```

El script **rechaza destinos que no sean localhost**, crea tres usuarios de prueba con IDs únicos
(dos con perfil aceptado y uno sin él), prueba acceso cruzado/cascadas/fotos y limpia sus propios
usuarios y objetos al terminar.
Si se interrumpe abruptamente, revisar los usuarios `pacefit-e2e-…@example.test` creados por esa
ejecución; no borrar usuarios ajenos ni ejecutar `db reset` sobre una instalación con datos.
Las pruebas pgTAP se revierten mediante `ROLLBACK`.

Studio: <http://127.0.0.1:54323>. El correo local se consulta en Mailpit, puerto 54324.
La confirmación de correo está desactivada **solo en el entorno de desarrollo**.
`npx --yes supabase status` muestra claves locales: no copiarlas al chat ni a Git.
Para detener conservando los volúmenes: `npx --yes supabase stop` (sin `--no-backup`).

### Conectar Android al entorno local

La app exige HTTPS incluso en debug; no se habilita tráfico HTTP global ni excepciones TLS.
El backend local usa HTTP y queda probado por el script Node. Para pruebas Android integradas,
exponerlo mediante un proxy HTTPS con certificado confiable para Android o usar un proyecto
Supabase de desarrollo HTTPS. No basta poner `localhost` en Android: sería el propio emulador.
No usar un túnel público para datos reales ni compartir claves de administración.

## 2. Preparar Supabase cloud (requiere tu cuenta)

1. Crear un proyecto de **desarrollo** desde <https://supabase.com/dashboard>.
2. En Auth, configurar correo/SMTP, confirmación de email, contraseña de al menos 10 caracteres,
   URLs permitidas y límites de envío. Nunca replicar la confirmación desactivada del entorno local
   en una entrega pública.
3. Obtener la URL HTTPS y la clave pública `anon` del proyecto. La clave pública no sustituye RLS.
   La clave `service_role` y los secretos de administración **no van en Android ni en Git**.
4. Autenticar la CLI y enlazar el proyecto autorizado:

```powershell
npx --yes supabase login
npx --yes supabase link --project-ref TU_PROJECT_REF
npx --yes supabase db push
npx --yes supabase functions deploy
```

`db push` aplica únicamente migraciones versionadas pendientes. Revisarlas y respaldar cualquier
proyecto preexistente antes de ejecutarlo; no usar `db reset` contra un servidor.
Las cuatro funciones validan explícitamente el JWT mediante `auth.getUser()`; por eso el
verificador JWT del gateway está desactivado en `supabase/config.toml`. No eliminar esa validación
ni asumir que decodificar un JWT equivale a verificarlo.

5. Verificar que el bucket `city-photos` siga **privado**, con límite 2 MiB/JPEG.
   La lectura exige una publicación compartida y un perfil autenticado activo. No hay escritura
   directa desde el cliente: las funciones controlan propietario, consentimiento y rutas.
6. Comprobar ejecución de `pacefit-ai-retention` en `cron.job`/`cron.job_run_details`.
   Si el proveedor no admite `pg_cron`, instalar un programador autenticado equivalente antes
   de publicar; no afirmar retención operativa solo porque existe una función SQL.

## 3. Configurar Gemini (requiere tu cuenta)

Crear clave de servidor en Google AI Studio/Google Cloud, elegir un modelo con entrada de imagen
disponible para tu proyecto y revisar facturación, cuotas y condiciones de tratamiento/retención.
No publicar fotos personales con condiciones de proveedor no aceptadas; el consentimiento de la
app no modifica los términos de Google.

En Windows, `scripts/configure-gemini.ps1` abre un formulario con entrada enmascarada compatible
con Ctrl+V. Exige confirmar uso gratuito y consulta modelos disponibles sin inferencia.
«Guardar y conectar» crea `.env` ignorado sin sobrescribir archivos existentes e invoca el
despliegue seguro al proyecto autorizado. Se admiten claves estándar y las nuevas claves de
autorización; Google valida la clave, no se presupone el prefijo antiguo.
No subir una captura de la clave ni enviarla por chat. Una clave expuesta debe revocarse y
sustituirse. Cerrar la ventana sin completar no configura Gemini ni inventa respuestas.

Crear `.env` local a partir de `.env.example` (ignorado por Git):

```dotenv
GEMINI_API_KEY=TU_CLAVE_SOLO_SERVIDOR
GEMINI_MODEL=IDENTIFICADOR_REAL_DEL_MODELO_CON_IMAGEN
```

Subir secretos al proyecto enlazado, sin escribir valores reales en comandos que queden en historial:

```powershell
npx --yes supabase secrets set --env-file .env
```

Para este proyecto autorizado también está disponible:

```powershell
python scripts/cloud-admin.py deploy-gemini
```

Lee únicamente las dos variables Gemini del `.env` privado, exige el proyecto Free autorizado
y rechaza sobrescribir secretos Gemini preexistentes. No imprime claves ni solicita inferencia.
Su existencia no significa que se haya introducido o desplegado una clave.

Verificación de asistentes sin credenciales ni llamadas reales:

```powershell
powershell -NoProfile -STA -ExecutionPolicy Bypass -File scripts/configure-gemini.ps1 -CheckUi
python scripts/configure-smtp.py --gmail-gui --check-ui
python scripts/test_private_setup.py
```

Las ocho pruebas Python comprueban con SMTP/administración simulados: TLS antes de contraseña,
certificado verificado, rechazo de puerto sin TLS, fallos sin escritura y no sobrescribir SMTP.
No acreditan acceso Gmail, envío de correo ni inferencia real.

Para IA local usar `functions serve --env-file .env`. No guardar `service_role` en ese archivo ni
enviar claves por chat. Supabase inyecta las variables internas de servidor a sus funciones.

`ai-gateway` admite únicamente `task: "photo"` para PF-015. Las otras cinco tareas/asesor semanal
de PF-012 siguen fuera de esta entrega. El servidor valida tamaño/JPEG, elimina metadata otra vez,
limita atómicamente a **40 solicitudes por usuario/hora**, restringe etiquetas y valida la respuesta.
Sin clave/modelo, cuota agotada, imagen insegura, respuesta inválida o caída, informa error;
la clasificación manual sigue disponible. No devuelve resultados ficticios.

## 4. Configurar y construir Android

Añadir a `local.properties` (sin cambiar `sdk.dir`) o definir variables de entorno:

```properties
SUPABASE_URL=https://TU_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=TU_CLAVE_PUBLICA_ANON
```

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r "app/build/outputs/apk/debug/app-debug.apk"
```

Sin configuración, perfil, rutas y registros urbanos privados funcionan; Cuenta indica
**Servidor sin conectar** y no simula inicio de sesión. Sin clave Gemini de servidor, la app
no puede habilitar una inferencia real.

## 5. Eliminación y respaldos (obligatorio antes de producción)

`account-delete` requiere JWT y escribir `ELIMINAR`. Marca el perfil en eliminación, bloquea
nuevas publicaciones/subidas, elimina fotos incluidas huérfanas del directorio propio y borra
Auth; las FK eliminan perfil, publicaciones, auditoría y cuota. Un recibo aleatorio de 256 bits
permite confirmar una eliminación completada si se perdió la respuesta y el JWT ya no existe.
El recibo se guarda solo como hash en el servidor, sin email/salud; expira con la tarea de 30 días.

En Android se registra la intención y después el acuse antes de purgar SQLite/fotos/sesión;
los marcadores permiten reintento tras interrupciones. Borrado local **no** borra la cuenta;
cerrar sesión **no** elimina el perfil de salud. Las publicaciones son independientes de sus copias locales.
No se incluyen chat/matches porque esos módulos no existen en este alcance; deberán añadir sus FK
de cascada y pruebas cuando se implementen.

Responsable del despliegue:

1. Registrar fecha/ID de solicitud y verificar ausencia de usuario Auth, filas propias y objetos.
   No registrar contraseña, tokens, fotos, coordenadas ni valores de salud en la evidencia.
2. Configurar retención de backups/PITR/exports externos **≤30 días** y restringir su acceso.
   Verificar el plan contratado y la política real de Supabase, no darlo por supuesto.
3. Mantener registro protegido de eliminaciones durante esa ventana. Antes de restaurar un backup,
   reaplicar esas eliminaciones y purgas **antes de reabrir el servicio**. Probar este procedimiento
   en entorno aislado y documentar fechas de expiración y responsable.
4. Eliminar exports locales/manuales y revisar retención de logs. No conservar recibos ni auditoría
   indefinidamente. Configurar el proveedor Gemini según los términos aceptados.
5. No prometer borrado forense instantáneo de flash ni de copias del proveedor.

**Pendiente de aceptación externa:** email real y confirmación/retorno, dos dispositivos físicos,
inferencias Gemini con cinco fotos propias, prueba de expiración/renovación y ejecución de
retención/backups del proyecto cloud. La preparación de código no acredita estas operaciones.
