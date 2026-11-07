package com.kinoshka.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kinoshka.app.App
import com.kinoshka.app.data.Collection
import com.kinoshka.app.data.Kinopoisk
import com.kinoshka.app.data.Movie
import com.kinoshka.app.data.Source
import com.kinoshka.app.data.SourceFinder
import com.kinoshka.app.data.UpdateInfo
import com.kinoshka.app.data.Updater
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Details(val movie: Movie) : Screen
}

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Fail(val message: String) : Load<Nothing>
}

class SourcesState(val sources: List<Source>, val errors: List<String>)

class MainVm(app: Application) : AndroidViewModel(app) {
    val prefs = (app as App).prefs
    private val kp = Kinopoisk(app, prefs)
    private val finder = SourceFinder(prefs)

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen get() = stack.last()

    val rows = mutableStateMapOf<String, Load<List<Movie>>>()
    var history by mutableStateOf(prefs.history())
        private set
    var hasKey by mutableStateOf(prefs.apiKey.isNotBlank())
        private set

    var query by mutableStateOf("")
    var results by mutableStateOf<Load<List<Movie>>?>(null)
        private set
    private var searchJob: Job? = null

    var sources by mutableStateOf<Load<SourcesState>>(Load.Loading)
        private set
    private var sourcesJob: Job? = null

    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateStatus by mutableStateOf<String?>(null)
        private set

    var crash by mutableStateOf(App.crashFile(app).takeIf { it.exists() }?.readText())
        private set

    init {
        loadRows()
        checkUpdate(silent = true)
    }

    // ---- навигация ----

    fun open(s: Screen) {
        stack.add(s)
        if (s is Screen.Details) findSources(s.movie, force = false)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        (screen as? Screen.Details)?.let { findSources(it.movie, force = false) }
        if (screen == Screen.Home) history = prefs.history()
        return true
    }

    // ---- каталог ----

    fun loadRows() {
        hasKey = prefs.apiKey.isNotBlank()
        if (!hasKey) return
        Kinopoisk.COLLECTIONS.forEach(::loadRow)
    }

    private fun loadRow(c: Collection) {
        if (rows[c.key] is Load.Ok) return
        rows[c.key] = Load.Loading
        viewModelScope.launch {
            rows[c.key] = runCatching { kp.collection(c) }
                .fold({ Load.Ok(it) }, { Load.Fail(it.message ?: "Ошибка") })
        }
    }

    fun retryRows() {
        rows.clear()
        loadRows()
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        results = Load.Loading
        searchJob = viewModelScope.launch {
            results = runCatching { kp.search(q) }.fold({ Load.Ok(it) }, { Load.Fail(it.message ?: "Ошибка") })
        }
    }

    // ---- источники ----

    fun findSources(movie: Movie, force: Boolean) {
        sourcesJob?.cancel()
        sources = Load.Loading
        sourcesJob = viewModelScope.launch {
            sources = runCatching { finder.find(movie, force) }
                .fold({ Load.Ok(SourcesState(it.sources, it.errors)) }, { Load.Fail(it.message ?: "Ошибка") })
        }
    }

    fun onWatch(movie: Movie) {
        prefs.addToHistory(movie)
        history = prefs.history()
    }

    fun forget(movie: Movie) {
        prefs.removeFromHistory(movie.id)
        history = prefs.history()
    }

    // ---- настройки ----

    fun saveKey(key: String) {
        prefs.apiKey = key
        prefs.apiBase = null
        rows.clear()
        loadRows()
    }

    fun clearCrash() {
        App.crashFile(getApplication<Application>()).delete()
        crash = null
    }

    fun checkUpdate(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) updateStatus = "Проверяю…"
            runCatching { Updater.check(getApplication<Application>()) }
                .onSuccess {
                    update = it
                    if (!silent) updateStatus = if (it == null) "Установлена последняя версия" else null
                }
                .onFailure { if (!silent) updateStatus = "Не удалось проверить: ${it.message}" }
        }
    }

    fun installUpdate() {
        val info = update ?: return
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            Updater.openInstallPermission(ctx)
            return
        }
        viewModelScope.launch {
            runCatching {
                Updater.download(ctx, info) { p -> updateStatus = "Скачиваю… ${(p * 100).toInt()}%" }
            }.onSuccess {
                updateStatus = null
                Updater.install(ctx, it)
            }.onFailure { updateStatus = "Ошибка загрузки: ${it.message}" }
        }
    }
}
