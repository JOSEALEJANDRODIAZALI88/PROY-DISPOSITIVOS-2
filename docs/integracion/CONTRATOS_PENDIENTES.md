# Contratos pendientes para integración entre integrantes

## PF-005 — datos de actividad y ambiente

El resumen diario debe recibir los registros de actividad ya persistidos por PF-003/PF-004. No duplique rutas ni vuelva a calcular pendiente si el módulo de esfuerzo ya entrega el resultado validado. Definir un contrato de lectura con fecha, duración, distancia, desnivel, calorías estimadas y calidad de dato.

Para UV, definir proveedor, unidad, timestamp, coordenadas aproximadas y política de caché. La pantalla actual no realiza llamadas HTTP ni requiere permisos de ubicación.

## PF-013 — repositorio social

Operaciones mínimas que deberá exponer el adaptador Supabase:

- `findNearbyRunners(approximateCell)`
- `sendConnectionRequest(userId)`
- `respondConnectionRequest(requestId, accepted)`
- `observeConversations()`
- `observeMessages(conversationId)` / `sendMessage(conversationId, body)`
- `blockUser(userId)` / `reportUser(userId, reason)`

Aplicar RLS en servidor para que solo participantes autorizados lean/escriban mensajes. La ubicación exacta no debe exponerse.

## PF-018 — grafo de calles

Adaptador esperado: nodos con identificador y elevación; aristas dirigidas con distancia, superficie si existe y pendiente calculada. El algoritmo de la pantalla puede reemplazar su entrada manual por el grafo normalizado sin cambiar el comparador de rutas.
