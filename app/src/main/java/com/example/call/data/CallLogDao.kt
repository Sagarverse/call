package com.example.call.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs")
    suspend fun getAllNow(): List<CallLogEntity>

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun findById(id: Long): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE systemId = :systemId")
    suspend fun findBySystemId(systemId: Long): CallLogEntity?

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE direction = 'Missed' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMissed(): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE direction = 'Outgoing' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestOutgoing(): CallLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<CallLogEntity>)

    @Query("UPDATE call_logs SET note = :note, tag = :tag WHERE id = :id")
    suspend fun updateNoteTagById(id: Long, note: String?, tag: String?)

    @Query("UPDATE call_logs SET note = :note, tag = :tag WHERE systemId = :systemId")
    suspend fun updateNoteTagBySystemId(systemId: Long, note: String?, tag: String?)

    @Query("DELETE FROM call_logs")
    suspend fun clearAll()

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
