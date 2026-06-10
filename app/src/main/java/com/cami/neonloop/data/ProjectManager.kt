package com.cami.neonloop.data

import android.content.Context
import com.cami.neonloop.model.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Estructura en disco (almacenamiento interno de la app):
 * projects/<id>/project.json  -> metadata
 * projects/<id>/track_N.wav   -> audio crudo de cada pista
 * exports/                    -> mezclas exportadas (WAV)
 */
class ProjectManager(private val context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val root get() = File(context.filesDir, "projects").apply { mkdirs() }
    val exportDir get() = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }

    fun newProject(name: String) = Project(id = UUID.randomUUID().toString(), name = name)

    fun save(p: Project) {
        val dir = File(root, p.id).apply { mkdirs() }
        File(dir, "project.json").writeText(json.encodeToString(p))
    }

    fun load(id: String): Project? {
        val f = File(root, "$id/project.json")
        return if (f.exists()) json.decodeFromString(f.readText()) else null
    }

    fun listProjects(): List<Project> =
        root.listFiles()?.mapNotNull { load(it.name) } ?: emptyList()

    fun delete(id: String) { File(root, id).deleteRecursively() }

    fun trackFile(p: Project, trackIndex: Int) =
        File(File(root, p.id), "track_$trackIndex.wav")
}
