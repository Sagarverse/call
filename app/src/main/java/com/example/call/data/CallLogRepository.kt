package com.example.call.data

import android.content.ContentResolver
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CallLogRepository(private val dao: CallLogDao) {
    fun observeLogs(): Flow<List<CallLogEntity>> = dao.observeAll()

    suspend fun getAllNow(): List<CallLogEntity> = dao.getAllNow()

    suspend fun findById(id: Long): CallLogEntity? = dao.findById(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun clearAll() = dao.clearAll()

    suspend fun addLog(log: CallLogEntity) {
        dao.insert(log)
    }

    suspend fun getLatest(): CallLogEntity? = dao.getLatest()

    suspend fun getLatestMissed(): CallLogEntity? = dao.getLatestMissed()

    suspend fun getLatestOutgoing(): CallLogEntity? = dao.getLatestOutgoing()

    suspend fun updateNoteTag(id: Long, note: String?, tag: String?) {
        dao.updateNoteTagById(id, note, tag)
    }

    suspend fun saveCallFromTelecom(contentResolver: ContentResolver, call: Call): Long {
        val details = call.details
        val number = details.handle?.schemeSpecificPart ?: ""
        val timestamp = details.creationTimeMillis
        val duration = if (details.connectTimeMillis > 0) {
            (System.currentTimeMillis() - details.connectTimeMillis) / 1000
        } else 0

        val direction = when {
            details.callDirection == Call.Details.DIRECTION_INCOMING && duration == 0L -> "Missed"
            details.callDirection == Call.Details.DIRECTION_INCOMING -> "Incoming"
            details.callDirection == Call.Details.DIRECTION_OUTGOING -> "Outgoing"
            else -> "Unknown"
        }

        val displayName = lookupContactName(contentResolver, number)

        val log = CallLogEntity(
            phoneNumber = number,
            displayName = displayName,
            direction = direction,
            timestamp = timestamp,
            durationSeconds = duration,
            note = null,
            tag = null
        )
        return dao.insert(log)
    }

    suspend fun syncFromSystem(
        contentResolver: ContentResolver,
        canReadContacts: Boolean
    ) {
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            val results = mutableListOf<CallLogEntity>()
            try {
                contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC LIMIT 500"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                    val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                    val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                    val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                    while (cursor.moveToNext()) {
                        val systemId = cursor.getLong(idIndex)
                        val number = cursor.getString(numberIndex) ?: ""
                        val cachedName = cursor.getString(nameIndex)
                        val timestamp = cursor.getLong(dateIndex)
                        val direction = mapDirection(cursor.getInt(typeIndex))
                        val durationSeconds = cursor.getLong(durationIndex)

                        var displayName = cachedName
                        if (canReadContacts && !number.isNullOrBlank()) {
                            val contactName = lookupContactName(contentResolver, number)
                            if (!contactName.isNullOrBlank()) {
                                displayName = contactName
                            }
                        }

                        results.add(
                            CallLogEntity(
                                systemId = systemId,
                                phoneNumber = number,
                                displayName = displayName,
                                direction = direction,
                                timestamp = timestamp,
                                durationSeconds = durationSeconds,
                                note = null,
                                tag = null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                return@withContext
            }

            if (results.isNotEmpty()) {
                dao.clearAll()
                dao.insertAll(results)
            }
        }
    }

    private fun mapDirection(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "Incoming"
            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
            CallLog.Calls.MISSED_TYPE -> "Missed"
            CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
            CallLog.Calls.REJECTED_TYPE -> "Rejected"
            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
            else -> "Unknown"
        }
    }

    private fun lookupContactName(
        contentResolver: ContentResolver,
        phoneNumber: String
    ): String? {
        if (phoneNumber.isBlank()) return null
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val lookupUri = android.net.Uri.withAppendedPath(uri, android.net.Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        return try {
            contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
