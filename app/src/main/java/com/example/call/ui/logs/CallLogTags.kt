package com.example.call.ui.logs

import android.content.Context
import com.example.call.R

object CallLogTags {
    const val WORK = "work"
    const val PERSONAL = "personal"
    const val SPAM = "spam"

    fun normalize(tag: String?): String? {
        val value = tag?.trim()?.lowercase().orEmpty()
        return when (value) {
            WORK -> WORK
            PERSONAL -> PERSONAL
            SPAM -> SPAM
            else -> null
        }
    }

    fun label(context: Context, tag: String?): String? {
        return when (normalize(tag)) {
            WORK -> context.getString(R.string.tag_work)
            PERSONAL -> context.getString(R.string.tag_personal)
            SPAM -> context.getString(R.string.tag_spam)
            else -> null
        }
    }
}
