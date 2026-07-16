package com.jujidaw.data

import androidx.room.*
import com.jujidaw.model.Preset
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY category ASC, name ASC")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE category = :category ORDER BY name ASC")
    fun getPresetsByCategory(category: String): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE isFactory = 0 ORDER BY updatedAt DESC")
    fun getUserPresets(): Flow<List<PresetEntity>>

    @Query("SELECT COUNT(*) FROM presets")
    suspend fun getPresetCount(): Int

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPresetById(id: Long): PresetEntity?

    @Query("SELECT * FROM presets WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity): Long

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)
}

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String,
    val description: String = "",
    val isFactory: Boolean = false,
    val parametersJson: String,       // JSON serialized SynthState
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Database(entities = [PresetEntity::class], version = 1, exportSchema = false)
abstract class PresetDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
}
