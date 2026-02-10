package com.example.call.util

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

object CalendarEventHelper {
    fun insertCallEvent(
        context: Context,
        title: String,
        description: String,
        startMillis: Long,
        endMillis: Long
    ) {
        val canWrite = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (!canWrite) return

        val calendarId = getPrimaryCalendarId(context) ?: return
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            CalendarContract.Calendars.VISIBLE + " = 1",
            null,
            null
        )?.use { cursor ->
            var fallbackId: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val isPrimary = cursor.getInt(1) == 1
                if (fallbackId == null) fallbackId = id
                if (isPrimary) return id
            }
            return fallbackId
        }
        return null
    }
}
