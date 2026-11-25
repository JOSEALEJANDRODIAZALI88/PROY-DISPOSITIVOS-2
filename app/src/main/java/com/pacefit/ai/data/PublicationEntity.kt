package com.pacefit.ai.data

import androidx.room.*

@Entity(tableName = "city_publications")
data class PublicationEntity(@PrimaryKey val localId: String, val ownerId: String, val remoteId: String, val kind: String)

@Dao
interface PublicationDao {
    @Query("SELECT * FROM city_publications WHERE localId = :id") suspend fun find(id: String): PublicationEntity?
    @Upsert suspend fun save(publication: PublicationEntity)
    @Query("DELETE FROM city_publications WHERE localId = :id") suspend fun delete(id: String)
    @Query("DELETE FROM city_publications") suspend fun clear()
}
