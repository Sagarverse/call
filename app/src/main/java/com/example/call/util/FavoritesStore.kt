package com.example.call.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FavoritesStore {
    data class Favorite(val name: String, val number: String)

    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorites_json"

    fun getFavorites(context: Context): List<Favorite> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            val results = ArrayList<Favorite>(json.length())
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val number = item.optString("number")
                if (number.isNotBlank()) {
                    results.add(Favorite(name, number))
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addFavorite(context: Context, favorite: Favorite) {
        val current = getFavorites(context).toMutableList()
        if (current.any { it.number == favorite.number }) return
        current.add(favorite)
        save(context, current)
    }

    fun removeFavorite(context: Context, number: String) {
        val current = getFavorites(context).filterNot { it.number == number }
        save(context, current)
    }

    private fun save(context: Context, favorites: List<Favorite>) {
        val array = JSONArray()
        favorites.forEach { fav ->
            val obj = JSONObject()
            obj.put("name", fav.name)
            obj.put("number", fav.number)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAVORITES, array.toString())
            .apply()
    }
}
