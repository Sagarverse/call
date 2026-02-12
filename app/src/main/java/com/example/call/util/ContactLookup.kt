package com.example.call.util

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

object ContactLookup {
    data class Result(
        val displayName: String?,
        val photo: Bitmap?
    )

    fun lookup(context: Context, phoneNumber: String?): Result {
        if (phoneNumber.isNullOrBlank()) return Result(null, null)
        val canRead = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canRead) return Result(null, null)

        val contentResolver = context.contentResolver
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val uri = android.net.Uri.withAppendedPath(
            lookupUri,
            android.net.Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val photoIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_URI)
                val name = cursor.getString(nameIndex)
                val photoUri = cursor.getString(photoIndex)
                val photo = loadPhoto(contentResolver, photoUri)
                return Result(name, photo)
            }
        }
        return Result(null, null)
    }

    fun findPhoneNumberByName(context: Context, name: String): String? {
        val canRead = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canRead) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        
        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            } else null
        }
    }

    fun findContactUri(context: Context, phoneNumber: String?): android.net.Uri? {
        if (phoneNumber.isNullOrBlank()) return null
        val canRead = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canRead) return null

        val contentResolver = context.contentResolver
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val uri = android.net.Uri.withAppendedPath(
            lookupUri,
            android.net.Uri.encode(phoneNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.LOOKUP_KEY
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)
                val keyIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val contactId = cursor.getLong(idIndex)
                val lookupKey = cursor.getString(keyIndex)
                return ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            }
        }
        return null
    }

    fun findContactEmail(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        val canRead = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canRead) return null

        val contactId = findContactId(context, phoneNumber) ?: return null
        val projection = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS)
        val selection = ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?"
        val args = arrayOf(contactId.toString())
        return context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                cursor.getString(index)
            } else null
        }
    }

    fun findContactId(context: Context, phoneNumber: String?): Long? {
        if (phoneNumber.isNullOrBlank()) return null
        val canRead = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!canRead) return null

        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        val uri = android.net.Uri.withAppendedPath(
            lookupUri,
            android.net.Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)
                cursor.getLong(idIndex)
            } else null
        }
    }

    private fun loadPhoto(contentResolver: ContentResolver, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            contentResolver.openInputStream(uriString.toUri())?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }
}
