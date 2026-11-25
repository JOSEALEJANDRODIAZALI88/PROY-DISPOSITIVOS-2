package com.pacefit.ai.modules

/** Contratos de integración: las implementaciones concretas pertenecen a los módulos/proveedores indicados. */

data class RunnerSummary(
    val userId: String,
    val displayName: String,
    val approximateLatitude: Double?,
    val approximateLongitude: Double?,
    val activityLabel: String?,
)

data class ConnectionRequest(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val status: String,
)

data class SocialMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val createdAtEpochMillis: Long,
)

/** Implementar con Supabase + Auth + RLS; nunca con credenciales service_role dentro de Android. */
interface SocialRepository {
    suspend fun findNearbyRunners(approximateCell: String): List<RunnerSummary>
    suspend fun sendConnectionRequest(userId: String): ConnectionRequest
    suspend fun respondToRequest(requestId: String, accept: Boolean)
    suspend fun sendMessage(conversationId: String, body: String): SocialMessage
    suspend fun blockUser(userId: String)
    suspend fun reportUser(userId: String, reason: String)
}

data class StreetNode(val id: String, val latitude: Double, val longitude: Double, val elevationMeters: Double?)
data class StreetEdge(val fromNodeId: String, val toNodeId: String, val distanceMeters: Double, val slope: Double)
data class StreetGraph(val nodes: List<StreetNode>, val edges: List<StreetEdge>)

/** Implementar con OpenStreetMap + proveedor DEM/elevación. */
interface StreetGraphProvider {
    suspend fun loadGraph(origin: Pair<Double, Double>, destination: Pair<Double, Double>): StreetGraph
}

data class UvReading(val index: Double, val observedAtEpochMillis: Long, val source: String)

/** Implementar con proveedor meteorológico; null significa que no hay lectura disponible. */
interface UvProvider {
    suspend fun currentUv(latitude: Double, longitude: Double): UvReading?
}
