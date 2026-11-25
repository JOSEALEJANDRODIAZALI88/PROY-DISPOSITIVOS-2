# Integrante 3 — PF-002, PF-005, PF-013 y PF-018

## Integración con la base original

Esta entrega parte de la primera versión del repositorio. Conserva el `MainActivity`, `PaceFitApplication`, `ProfileViewModel`, la pantalla Inicio, el editor de perfil, la pantalla de tickets y la barra inferior existente. Se agrega una cuarta entrada compacta: **Mi módulo**.

Dentro de esa única pantalla se usan pestañas superiores para PF-002, PF-005, PF-013 y PF-018.

## PF-002 — Catálogo de parámetros

- Entidad Room `PaceParameterEntity` y DAO `PaceFitModulesDao`.
- Crear, editar y eliminar parámetros.
- Cada parámetro almacena clave, nombre, valor numérico, unidad, fuente/justificación, estado y fecha de actualización.
- Se precargan cuatro valores explícitamente marcados como provisionales/referenciales.
- Los cambios se persisten en la base local `pacefit-health.db`.

## PF-005 — Altitud, hidratación, UV y resumen diario

- Registro local de consumos de agua con fecha/hora.
- Total diario calculado desde Room.
- Referencia de agua = peso del perfil × parámetro configurable ml/kg/día. Se presenta como estimación, no prescripción.
- Altitud e índice UV se pueden introducir manualmente; la pantalla identifica claramente que no son lecturas automáticas.
- El UV se clasifica en categorías informativas. No se inventa una consulta meteorológica.
- Actividad, pendiente, pulso y datos automáticos quedan pendientes de contratos PF-003/PF-004/PF-006 y de una fuente UV.

## PF-013 — Corredores, solicitudes, chat y bloqueo

La pantalla muestra el estado de integración pendiente y enumera las operaciones necesarias. No genera perfiles ficticios ni simula solicitudes o mensajes. Para habilitar operaciones reales se requiere:

- Proyecto y URL/clave pública de Supabase configurados de forma segura.
- Autenticación y perfil social PF-010.
- Esquema de tablas y políticas RLS PF-011.
- Contratos de ubicación aproximada y consentimiento.

No se debe insertar una clave `service_role` en la aplicación Android.

## PF-018 — Ruta de menor esfuerzo

- La pantalla acepta un grafo dirigido de tramos ingresados por el usuario: `origen,destino,km,pendiente`.
- Calcula la ruta de menor distancia y la ruta de menor costo estimado mediante Dijkstra.
- El costo de esfuerzo usa el polinomio de caminata Minetti y restringe la pendiente al intervalo documentado [-0.45, 0.45].
- No descarga calles ni elevaciones. Para rutas reales se debe proporcionar el grafo OSM y elevaciones desde la integración del equipo.
- Para representar una calle bidireccional, ingresar ambos sentidos como tramos separados.

## Persistencia y migración

La base Room pasa de versión 1 a 2. Se agrega `MIGRATION_1_2` para crear `pace_parameters` y `water_logs` sin borrar `health_profile`. La base original no se reemplaza ni se elimina.

## Verificación

En la raíz del proyecto:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

La compilación debe ejecutarse en un entorno con acceso a la distribución Gradle y repositorios Maven. No se debe declarar la build verificada hasta ejecutar ambos comandos correctamente.
