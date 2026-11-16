package com.example.data.storage

import com.example.model.AutoSaveStatus
import com.example.model.StudioProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background debounced auto-saver that coalesces rapid code editor changes
 * and persists the current [StudioProject] to `filesDir` after [debounceMs]
 * of inactivity, with support for immediate flushing when switching files or screens.
 */
class DebouncedProjectAutoSaver(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 650L,
    private val persistProject: suspend (StudioProject) -> StudioProject,
    private val onPersisted: suspend (StudioProject) -> Unit = {}
) {
    private val _status = MutableStateFlow(AutoSaveStatus.IDLE)
    val status: StateFlow<AutoSaveStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private var pendingProject: StudioProject? = null
    private var debounceJob: Job? = null

    fun scheduleSave(project: StudioProject) {
        pendingProject = project
        _status.value = AutoSaveStatus.PENDING

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            flushInternal()
        }
    }

    fun flushNow() {
        debounceJob?.cancel()
        debounceJob = null
        if (pendingProject != null) {
            scope.launch {
                flushInternal()
            }
        }
    }

    suspend fun flushNowBlocking(): StudioProject? {
        debounceJob?.cancel()
        debounceJob = null
        return flushInternal()
    }

    private suspend fun flushInternal(): StudioProject? = mutex.withLock {
        val snapshot = pendingProject ?: return@withLock null
        pendingProject = null
        _status.value = AutoSaveStatus.SAVING
        return@withLock try {
            val saved = persistProject(snapshot)
            onPersisted(saved)
            _status.value = AutoSaveStatus.SAVED
            saved
        } catch (ex: Exception) {
            // Retain snapshot if save failed so next edit or flush can retry
            if (pendingProject == null) {
                pendingProject = snapshot
            }
            _status.value = AutoSaveStatus.PENDING
            null
        }
    }
}
