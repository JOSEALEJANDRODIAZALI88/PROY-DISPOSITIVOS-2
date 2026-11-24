# PaceFit AI — Tickets de la beta

Fuente: `recursos/PaceFit_AI_informe_final_Kanban.docx`.

## Acuerdo de entrega

Se entrega primero **PF-001**, por la prioridad de disponer de una beta rápida.
El resto del documento se conserva en el backlog; no se declara implementado.
La numeración PF es operativa y no sustituye los IDs RF/B/T del informe.

- Rama de la primera entrega: `primera-version`.
- Versión inicial: `0.1.0-beta.1`.
- Estados: Backlog → To Do → Análisis → Desarrollo → Testing → Despliegue → Done.
- Límites: To Do 5; Análisis 2; Desarrollo 3; Testing 2; Despliegue 1. WIP activo máximo 8.
- No mover a Done sin criterios verificados y evidencia. Una prueba escrita no equivale a una prueba ejecutada.
- Una tarjeta activa por responsable; dividir tareas grandes antes de iniciarlas.
- Los responsables humanos y las fechas se acuerdan con el equipo; no se inventan asignaciones ni reuniones.

## Resumen

| Ticket | Entrega | Informe | Dependencias | Estado |
|---|---|---|---|---|
| PF-001 | Base Android y perfil de salud local | RF01; B01 y parte de B02 | — | Testing: APK y 18 pruebas unitarias verificados; dispositivo pendiente |
| PF-002 | Catálogo de parámetros y fuentes | T01; RNF13 | — | Backlog |
| PF-003 | Registro de actividad y sensores | RF02; B02/B03/B04 | PF-001 | Backlog |
| PF-004 | Pendiente y esfuerzo | RF03; B05 | PF-002/003 | Backlog |
| PF-005 | Altitud, agua, UV y resumen diario | RF04; B06/B07 parcial | PF-002/003/004 | Backlog |
| PF-006 | Pulso mediante BLE | RF05; B08 | PF-003 | Backlog |
| PF-007 | Detección de transporte | RF06; B11 | PF-003 | Backlog |
| PF-008 | Alimentos locales y comidas | RF07; B10 | PF-001/002 | Backlog |
| PF-009 | Comida por fotografía | RF07; B12 | PF-008/012 | Backlog |
| PF-010 | Cuenta y perfil social | RF16; B20 parcial | PF-001 | Backlog |
| PF-011 | Seguridad remota y borrado de cuenta | RF16; B20; anexo B | PF-010 | Backlog |
| PF-012 | Gateway de IA y asesor semanal | RF08; B07 | PF-005/010/011 | Backlog |
| PF-013 | Corredores, solicitudes, chat y bloqueo | RF13; B17 | PF-003/010/011/012 | Backlog |
| PF-014 | Zonas y competencia semanal | RF12; B16 | PF-003/010/011 | Backlog |
| PF-015 | Plogging y reportes urbanos | RF14; B18 | PF-010/011/012 | Backlog |
| PF-016 | Reporte PDF sin internet | RF11; B09 | PF-003/005/008 | Backlog |
| PF-017 | Aclimatación orientativa | RF09; B13 | PF-001/002 | Backlog, opcional en informe |
| PF-018 | Ruta de menor esfuerzo | RF10; B14 | PF-003/004 | Backlog, opcional en informe |
| PF-019 | Dibujo GPS mediante IA | RF15; B19 | PF-003/010/011/012 | Backlog, opcional en informe |
| PF-020 | Validación y liberación integrada | B15; sección 8; anexo F | Tickets incluidos en la liberación | Backlog |

Los opcionales permanecen en el backlog porque se solicitó cubrir el documento completo,
pero no forman parte del primer ticket.

## PF-001 — Base Android y perfil de salud local

**Prioridad:** alta. **Entrega:** Android 8.0+; Kotlin, Compose, Room, ViewModel y StateFlow.

### Alcance

- Proyecto Android con wrapper y comandos reproducibles.
- Inicio, edición del perfil y pantalla informativa de próximos tickets.
- Perfil único local con peso, estatura, edad, sexo opcional y fecha de actualización.
- Crear, consultar, modificar y eliminar con confirmación.
- Validación sin cierres inesperados; soporte de coma o punto decimal.
- Datos en almacenamiento privado; sin Internet, autenticación, analítica ni permisos sensibles.
- Exclusión explícita de copias de seguridad y transferencias Android.

### Criterios de aceptación

1. `assembleDebug`, `testDebugUnitTest` y `lintDebug` terminan correctamente.
2. Se puede instalar y abrir el APK en Android API 26 o posterior.
3. Un perfil válido se guarda y se muestra tras cerrar y volver a abrir la app.
4. Editar actualiza el mismo perfil; no crea perfiles duplicados.
5. Campos vacíos, números no finitos y valores fuera de los límites muestran errores.
6. Cambiar de pestaña o rotar no pierde un formulario durante la vida del proceso.
7. Eliminar requiere confirmación y deja el perfil vacío tras reabrir.
8. El manifiesto no solicita Internet ni permisos sensibles. No se registran valores de salud en logs.
9. La interfaz y README identifican como pendientes las funciones fuera de este ticket.

Los límites de entrada (peso 10–400 kg, estatura 50–250 cm y edad 1–120 años)
son controles de escritura, **no rangos clínicos**. El sexo puede omitirse; las futuras fórmulas
no deberán inventarlo.

### Evidencia y cierre

- Implementación: `app/src/main/java/com/pacefit/ai/`.
- Pruebas unitarias: `ProfileValidatorTest`.
- Pruebas del estado de pantalla: `ProfileViewModelTest`.
- Pruebas de Room, incluidas reapertura y borrado: `HealthDatabaseTest` (instrumentadas).
- Registro de ejecución: `docs/VERIFICACION.md`.
- Este ticket no estará en Done hasta instalar y comprobar persistencia/rotación en dispositivo o emulador.

## PF-002 — Catálogo de parámetros y fuentes

**Aceptación:** cada parámetro tiene valor, unidad, fuente, rango, estado y limitación.
Provisionales identificados; factores sin respaldo parten de valores neutros.
Verificar coeficientes Minetti para carrera y fórmula/umbrales de aclimatación antes de implementarlos.
Permitir editar parámetros y conservarlos tras reiniciar, sin modificar código.
**Evidencia:** catálogo versionado, bibliografía contrastada y prueba de cambio persistente.

## PF-003 — Registro de actividad

**Aceptación:** iniciar, pausar y finalizar; servicio en primer plano con notificación;
GPS cada 3–5 s, barómetro cuando existe y pasos. Descartar precisión >20 m.
Guardar rutas/puntos en Room, sobreviviendo a cambios de pantalla; ofrecer historial y resultado.
Permisos por versión con denegación controlada, sin exigir sensores ausentes.
Mapa con atribución OpenStreetMap y cumplimiento de su política; no prometer mapas offline.
**Evidencia:** recorrido de 20 minutos, pantalla apagada, sin huecos >30 s; prueba en modo avión.

## PF-004 — Pendiente y esfuerzo

**Aceptación:** suavizado en ventanas de 30 m; pendiente limitada al rango validado [-0,45; 0,45].
Tobler estima tiempo, Minetti energía; conversión física kcal/J.
Usar coeficientes de marcha/carrera verificados, no intercambiarlos.
Etiquetar estimaciones, explicar el efecto de subida y probar ruta plana frente a subida comparable.
**Evidencia:** pruebas unitarias con referencias y casos límite.

## PF-005 — Altitud, hidratación y resumen

**Aceptación:** Mifflin-St Jeor con supuestos explícitos; factor de altura inicialmente neutro.
Agua, electrolitos orientativos y UV por hora/lugar/altura con parámetros trazables.
No presentar gasto basal + ejercicio como gasto total diario ni como dosis médica.
Resumen local diario con WorkManager, recuperación tras reinicio y sin duplicados.
**Evidencia:** pruebas de fórmulas, noche/mediodía, parámetros y trabajo diario.

## PF-006 — BLE

**Aceptación:** escanear, conectar, desconectar y reconectar con permisos correctos.
Servicio 0x180D, característica 0x2A37; interpretar formatos de 8 y 16 bits.
Guardar pulso ligado a actividad; respaldar con registro manual si la banda no es compatible.
**Evidencia:** banda real frente a medición manual, diferencia ≤5 lpm en reposo.

## PF-007 — Transporte

**Aceptación:** combinar velocidad sostenida, pasos y actividad; ventana configurable.
Marcar y conservar tramos en vehículo, excluyéndolos de energía, metros y zonas.
Evitar clasificar carrera como vehículo solo por superar aproximadamente 7 km/h.
**Evidencia:** caminar, correr y viajar en minibús/Teleférico con trazas etiquetadas.

## PF-008 — Comidas y alimentos

**Aceptación:** base local de 50–100 alimentos/platos con fuente y porción documentadas;
buscar, registrar, corregir y eliminar comidas/bebidas en Room sin internet.
Calcular nutrientes a partir de cantidades confirmadas y datos respaldados, no datos inventados.
**Evidencia:** cálculos de porciones, consultas locales y persistencia.

## PF-009 — Comida por foto

**Aceptación:** capturar o elegir imagen, comprimir y quitar EXIF.
La IA propone alimento/porción; siempre se confirma o corrige antes de guardar.
Los nutrientes vienen de PF-008, nunca de la respuesta generativa.
**Evidencia:** platos locales y respaldo manual sin IA/Internet.

## PF-010 — Cuenta y perfil social

**Aceptación:** registro, acceso, cierre, errores claros y sesión persistente en Supabase.
Perfil público separado del de salud; solo nombre y foto necesarios.
Configuración por entorno, sin claves privadas en APK, código o Git.
Las funciones locales siguen utilizables sin sesión ni Internet.
**Evidencia:** dos cuentas de prueba; inspección de tablas sin datos de salud.

## PF-011 — Seguridad, privacidad y eliminación

**Aceptación:** migraciones SQL versionadas; RLS en todas las tablas; Storage por usuario.
Pruebas de acceso cruzado; RPC sensibles sin escrituras directas equivalentes.
Cuenta eliminada con cascada de perfil, mensajes, matches, fotos y datos locales;
retención de ai_usage de 30 días y procedimiento documentado para respaldos.
Consentimientos explícitos antes de compartir ubicación, resúmenes o fotografías.
**Evidencia:** pruebas con dos usuarios, usuario anónimo y borrado completo.

## PF-012 — Gateway y asesor

**Aceptación:** JWT verificado; límite atómico de 40 llamadas/usuario/hora, incluso concurrentes.
Seis tareas: advisor, explain, photo, moderate, draw, icebreakers.
Contrato `{task,payload}` → `{ok,data}` o `{ok:false,error}`; validación por tarea.
Secretos del modelo solo en servidor; contexto de hasta siete resúmenes, sin identidad ni GPS exacto.
Conversación en Room; respaldo explícito por límite, caída, respuesta inválida o sin Internet.
Sin diagnósticos ni dosis; síntomas graves generan recomendación de atención inmediata.
**Evidencia:** llamada 41 bloqueada, entradas malformadas y revisión de payloads.

## PF-013 — Comunidad

**Aceptación:** visibilidad opt-in solo durante actividad; redondeo a tres decimales,
expiración de 60 s y radio de 1500 m. Excluir bloqueados e ignorados.
Seguir, aceptar, ignorar sin avisar al remitente; match y chat solo para participantes.
Moderación efectiva en servidor, no evadible llamando directamente a la tabla.
Bloquear elimina match/mensajes y oculta ambas ubicaciones.
**Evidencia:** dos dispositivos; pruebas de RLS, chat, ignorar, moderación y bloqueo.

## PF-014 — Zonas

**Aceptación:** celdas de 0,001°; servidor calcula ID desde latitud/longitud.
Cola local de metros pendientes, reintentos idempotentes y máximo 500 m por llamada.
El cliente no puede imponer dueño, puntos, semana ni celdas falsas.
Semana desde lunes 00:00 America/La_Paz; empate visible como disputa.
**Evidencia:** dos corredores en una celda, cambio de semana y pruebas adversariales.

## PF-015 — Ciudad

**Aceptación:** foto comprimida sin EXIF, ubicación y confirmación de clasificación.
Plogging con cantidad poco/medio/mucho y puntos 5/10/20 recalculados en servidor.
Reportes bache/basura/luminaria/otro; autor puede resolver y borrar los propios.
Mapa para usuarios autenticados; no describir personas ni subir fotos sin consentimiento.
**Evidencia:** cinco fotos reales, permisos denegados y acceso cruzado a Storage.

## PF-016 — PDF

**Aceptación:** generación local sin Internet con actividad, tendencias y recomendaciones
orientativas; nota de no diagnóstico, versión/fecha y consentimiento para compartir.
No subir PDF ni salud a Supabase. Manejar falta de datos y errores de almacenamiento.
**Evidencia:** PDF en modo avión, con y sin historial.

## PF-017 — Aclimatación (opcional)

**Aceptación:** contrastar Lake Louise 2018, documentar ítems y umbrales del proyecto;
resolver la mención al sueño del informe antes de puntuarla.
Registro diario local, semáforo y recomendación urgente ante dolor de pecho,
disnea en reposo, confusión o desmayo, sin depender de IA.
**Evidencia:** cuestionarios sintéticos, límites y todos los síntomas de alarma.

## PF-018 — Menor esfuerzo (opcional)

**Aceptación:** limitar a zona de prueba con grafo/elevación y licencias identificadas.
Comparar ruta corta/suave con metros, desnivel, tiempo y kcal; señalar limitaciones
del modelo de elevación y escalinatas. No simular rutas sobre datos inexistentes.
**Evidencia:** recorridos comprobados en la zona y pruebas del algoritmo.

## PF-019 — Dibujo GPS (opcional)

**Aceptación:** IA entrega 40–120 puntos normalizados validados; escala inicial 2 km.
Mover, rotar, escalar y guiar recorrido; publicar y registrar repeticiones.
Sin ajuste automático a calles; advertir sobre seguridad y accesibilidad del recorrido.
**Evidencia:** corazón, gato y casa reconocibles; geometría y escala probadas.

## PF-020 — Validación y entrega

**Aceptación:** ejecutar casos de la sección 8 y registrar Pasa/Falla/Bloqueada/No ejecutada.
Incluir pruebas reales de distancia ±5%, huecos GPS, batería ≤20%/h en equipo identificado,
permisos, desconexión, privacidad, RLS, límite IA y dos teléfonos.
Encuesta 5–10 usuarios sin inventar resultados ni validación clínica.
APK identificable, instalación reproducible, notas, incidencias y reversión.
Tablero real de siete columnas con WIP, actas, exportaciones y métricas cuando existan.
La herramienta, responsables y accesos los confirma el equipo.
**Evidencia:** matriz RF → ticket → prueba → resultado → artefacto/commit.

## Cómo trabajar el siguiente ticket

1. Elegir y confirmar alcance y responsable; verificar dependencias y criterio de aceptación.
2. Mover a Análisis/Desarrollo respetando WIP; dividir en subtareas de hasta dos jornadas.
3. Implementar y probar; adjuntar evidencia real sin datos sensibles.
4. Registrar bloqueos y limitaciones; Testing no equivale a Done.
5. Publicar una versión identificable y revisar con el equipo.
