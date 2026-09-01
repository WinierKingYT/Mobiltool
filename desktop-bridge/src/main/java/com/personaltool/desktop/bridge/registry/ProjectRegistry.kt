package com.personaltool.desktop.bridge.registry

import java.io.File
import java.util.UUID

data class ProjectEntry(
    val id: String,
    val name: String,
    val localDirectory: File,
    val rootAlias: String
)

class ProjectRegistry {

    private val projects = mutableMapOf<String, ProjectEntry>()

    init {
        // Seed default development projects for local workspace
        val currentDir = File(".").canonicalFile
        registerProject(
            name = "PersonalMobileTool",
            directory = currentDir,
            alias = "~/Projects/PersonalMobileTool"
        )
        registerProject(
            name = "PromtGen",
            directory = File(currentDir.parentFile, "promtgen"),
            alias = "~/Projects/PromtGen"
        )
        registerProject(
            name = "Eleven",
            directory = File(currentDir.parentFile, "eleven"),
            alias = "~/Projects/Eleven"
        )
    }

    fun registerProject(name: String, directory: File, alias: String): ProjectEntry {
        val id = UUID.nameUUIDFromBytes(name.toByteArray()).toString()
        val entry = ProjectEntry(
            id = id,
            name = name,
            localDirectory = directory,
            rootAlias = alias
        )
        projects[id] = entry
        return entry
    }

    fun getProject(id: String): ProjectEntry? = projects[id]

    fun getAllProjects(): List<ProjectEntry> = projects.values.toList()

    fun isPathWithinRegisteredProject(file: File): Boolean {
        val canonical = file.canonicalFile
        return projects.values.any { entry ->
            canonical.startsWith(entry.localDirectory.canonicalFile)
        }
    }
}
