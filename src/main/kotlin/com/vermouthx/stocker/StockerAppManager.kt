package com.vermouthx.stocker

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

object StockerAppManager {
    private val lock = Any()
    private var sharedApp: StockerApp? = null
    private val projects = mutableSetOf<Project>()

    fun myApplication(project: Project?): StockerApp? {
        if (project == null) return null
        synchronized(lock) {
            return if (projects.contains(project)) sharedApp else null
        }
    }

    fun getAllApplications(): Collection<StockerApp> {
        synchronized(lock) {
            return listOfNotNull(sharedApp)
        }
    }

    /**
     * Acquire shared StockerApp for a project. Creates if needed.
     * Returns the shared instance so all projects share one scheduler and settings.
     */
    fun register(project: Project): StockerApp {
        synchronized(lock) {
            val app = sharedApp ?: StockerApp().also { sharedApp = it }
            projects.add(project)
            return app
        }
    }

    fun unregister(project: Project) {
        synchronized(lock) {
            if (!projects.remove(project)) return
            if (projects.isEmpty()) {
                sharedApp?.dispose()
                sharedApp = null
            }
            // Other projects still running? Keep the app alive (data shared via message bus).
        }
    }

    class StockerProjectManagerListener : ProjectManagerListener {
        override fun projectClosing(project: Project) {
            unregister(project)
        }
    }
}