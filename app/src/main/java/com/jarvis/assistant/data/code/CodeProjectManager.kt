package com.jarvis.assistant.data.code

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class SavedCodeProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val code: String,
    val language: String = "html",
    val timestamp: Long = System.currentTimeMillis()
)

object CodeProjectManager {

    private const val PREF_NAME = "siya_code_projects_pref"
    private const val KEY_PROJECTS = "key_saved_code_projects"
    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getAllProjects(context: Context): List<SavedCodeProject> {
        return try {
            val json = getPrefs(context).getString(KEY_PROJECTS, null) ?: return emptyList()
            val type = object : TypeToken<List<SavedCodeProject>>() {}.type
            gson.fromJson<List<SavedCodeProject>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun saveProject(context: Context, title: String, code: String, language: String = "html"): SavedCodeProject {
        val current = getAllProjects(context).toMutableList()
        // If a project with same title already exists, update it, otherwise add to front
        val existingIndex = current.indexOfFirst { it.title.equals(title.trim(), ignoreCase = true) }
        val project: SavedCodeProject
        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            project = existing.copy(code = code, timestamp = System.currentTimeMillis(), language = language)
            current.removeAt(existingIndex)
            current.add(0, project)
        } else {
            project = SavedCodeProject(
                title = title.trim().ifBlank { "Untitled Project" },
                code = code,
                language = language,
                timestamp = System.currentTimeMillis()
            )
            current.add(0, project)
        }

        val json = gson.toJson(current)
        getPrefs(context).edit().putString(KEY_PROJECTS, json).apply()
        return project
    }

    @Synchronized
    fun deleteProject(context: Context, id: String) {
        val current = getAllProjects(context).toMutableList()
        current.removeAll { it.id == id }
        val json = gson.toJson(current)
        getPrefs(context).edit().putString(KEY_PROJECTS, json).apply()
    }
}
