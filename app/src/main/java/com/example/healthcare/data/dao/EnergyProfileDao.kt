package com.example.healthcare.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthcare.data.entity.EnergyProfileHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface EnergyProfileDao {
    @Query("SELECT * FROM energy_profile_history ORDER BY effectiveFromDate DESC, updatedAt DESC")
    fun getAllProfiles(): Flow<List<EnergyProfileHistory>>

    @Query(
        "SELECT * FROM energy_profile_history " +
            "WHERE effectiveFromDate <= :date " +
            "ORDER BY effectiveFromDate DESC, updatedAt DESC LIMIT 1"
    )
    fun getProfileForDate(date: String): Flow<EnergyProfileHistory?>

    @Query("SELECT * FROM energy_profile_history WHERE effectiveFromDate = :date LIMIT 1")
    suspend fun getProfileStartingOn(date: String): EnergyProfileHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: EnergyProfileHistory)
}
