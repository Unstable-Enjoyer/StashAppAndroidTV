package com.github.damontecres.stashapp.ui.pages

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.stashapp.api.fragment.SavedFilter
import com.github.damontecres.stashapp.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.api.fragment.FullSceneData
import com.github.damontecres.stashapp.api.type.FilterMode
import com.github.damontecres.stashapp.api.type.FindFilterType
import com.github.damontecres.stashapp.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.data.DataType
import com.github.damontecres.stashapp.data.SortAndDirection
import com.github.damontecres.stashapp.data.SortOption
import com.github.damontecres.stashapp.suppliers.FilterArgs
import com.github.damontecres.stashapp.suppliers.toFilterArgs
import com.github.damontecres.stashapp.util.FilterParser
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashCoroutineExceptionHandler
import com.github.damontecres.stashapp.util.StashServer
import com.apollographql.apollo.api.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReelsViewModel : ViewModel() {
    private lateinit var server: StashServer
    private lateinit var queryEngine: QueryEngine

    private val _scenes = MutableStateFlow<List<SlimSceneData>>(emptyList())
    val scenes: StateFlow<List<SlimSceneData>> = _scenes

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _currentFilter = MutableStateFlow<FilterArgs>(
        FilterArgs(DataType.SCENE, name = "All Scenes")
    )
    val currentFilter: StateFlow<FilterArgs> = _currentFilter

    private val _currentSort = MutableStateFlow(SortAndDirection.random())
    val currentSort: StateFlow<SortAndDirection> = _currentSort

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _scenesGeneration = MutableStateFlow(0)
    val scenesGeneration: StateFlow<Int> = _scenesGeneration

    private val _savedFilters = MutableStateFlow<List<SavedFilter>>(emptyList())
    val savedFilters: StateFlow<List<SavedFilter>> = _savedFilters

    private val _sceneDetail = MutableStateFlow<FullSceneData?>(null)
    val sceneDetail: StateFlow<FullSceneData?> = _sceneDetail

    private val _loop = MutableStateFlow(true)
    val loop: StateFlow<Boolean> = _loop

    private val _autoAdvance = MutableStateFlow(true)
    val autoAdvance: StateFlow<Boolean> = _autoAdvance

    private var currentPage = 0
    private val pageSize = 20
    private var hasMore = true
    private var initialized = false
    private var prefs: SharedPreferences? = null
    private var resolvedSort: SortAndDirection? = null

    fun init(server: StashServer, context: Context) {
        if (initialized) return
        initialized = true
        this.server = server
        this.queryEngine = QueryEngine(server)
        this.prefs = context.getSharedPreferences("reels_prefs", Context.MODE_PRIVATE)

        // Restore persisted state
        restoreState()

        viewModelScope.launch(StashCoroutineExceptionHandler()) {
            try {
                val filters = queryEngine.getAllSavedFilters()
                    .filter { it.mode == FilterMode.SCENES }
                _savedFilters.value = filters
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load saved filters", e)
            }
        }

        loadScenes()
    }

    private fun restoreState() {
        prefs?.let { p ->
            val sortKey = p.getString(PREF_SORT, null)
            val sortDir = p.getString(PREF_SORT_DIR, null)
            if (sortKey != null) {
                _currentSort.value = SortAndDirection.create(
                    sortKey,
                    sortDir?.let { SortDirectionEnum.entries.firstOrNull { e -> e.rawValue == it } },
                )
            }
            _loop.value = p.getBoolean(PREF_LOOP, true)
            _autoAdvance.value = p.getBoolean(PREF_AUTO_ADVANCE, true)

            val filterName = p.getString(PREF_FILTER_NAME, null)
            if (filterName != null) {
                _currentFilter.value = _currentFilter.value.copy(name = filterName)
            }
        }
    }

    private fun persistState() {
        prefs?.edit()?.apply {
            putString(PREF_SORT, _currentSort.value.sort.key)
            putString(PREF_SORT_DIR, _currentSort.value.direction.rawValue)
            putBoolean(PREF_LOOP, _loop.value)
            putBoolean(PREF_AUTO_ADVANCE, _autoAdvance.value)
            putString(PREF_FILTER_NAME, _currentFilter.value.name)
            apply()
        }
    }

    fun loadScenes() {
        if (_isLoading.value) return
        _isLoading.value = true
        _scenesGeneration.value++
        currentPage = 0
        hasMore = true

        viewModelScope.launch(StashCoroutineExceptionHandler()) {
            try {
                resolvedSort = _currentSort.value.withResolvedRandom()
                val sort = resolvedSort!!
                val findFilter = FindFilterType(
                    page = Optional.present(1),
                    per_page = Optional.present(pageSize),
                    sort = Optional.present(sort.sortKey),
                    direction = Optional.present(sort.direction),
                )
                val filter = _currentFilter.value
                val objectFilter = filter.objectFilter
                val sceneFilter = if (objectFilter is com.github.damontecres.stashapp.api.type.SceneFilterType) {
                    objectFilter
                } else {
                    null
                }
                val scenes = queryEngine.findScenes(
                    findFilter = findFilter,
                    sceneFilter = sceneFilter,
                    useRandom = false,
                )
                _scenes.value = scenes
                currentPage = 1
                hasMore = scenes.size >= pageSize
            } catch (e: Exception) {
                Log.e(TAG, "Error loading scenes", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoading.value || !hasMore) return
        _isLoading.value = true

        viewModelScope.launch(StashCoroutineExceptionHandler()) {
            try {
                val sort = resolvedSort ?: _currentSort.value.withResolvedRandom()
                val nextPage = currentPage + 1
                val findFilter = FindFilterType(
                    page = Optional.present(nextPage),
                    per_page = Optional.present(pageSize),
                    sort = Optional.present(sort.sortKey),
                    direction = Optional.present(sort.direction),
                )
                val filter = _currentFilter.value
                val objectFilter = filter.objectFilter
                val sceneFilter = if (objectFilter is com.github.damontecres.stashapp.api.type.SceneFilterType) {
                    objectFilter
                } else {
                    null
                }
                val newScenes = queryEngine.findScenes(
                    findFilter = findFilter,
                    sceneFilter = sceneFilter,
                    useRandom = false,
                )
                if (newScenes.isNotEmpty()) {
                    _scenes.value = _scenes.value + newScenes
                    currentPage = nextPage
                }
                hasMore = newScenes.size >= pageSize
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more scenes", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index
        // Prefetch more when near the end
        if (index >= _scenes.value.size - 5) {
            loadMore()
        }
    }

    fun setSort(sort: SortAndDirection) {
        _currentSort.value = sort
        resolvedSort = null
        persistState()
        loadScenes()
    }

    fun setFilter(filter: FilterArgs, name: String? = null) {
        _currentFilter.value = filter.copy(name = name ?: filter.name ?: "All Scenes")
        resolvedSort = null
        persistState()
        loadScenes()
    }

    fun setSavedFilter(savedFilter: SavedFilter) {
        val filterParser = FilterParser(server.serverPreferences.serverVersion)
        val filterArgs = savedFilter.toFilterArgs(filterParser)
        _currentFilter.value = filterArgs
        _currentSort.value = filterArgs.sortAndDirection
        resolvedSort = null
        persistState()
        loadScenes()
    }

    fun setQuickFilter(name: String, filter: FilterArgs) {
        _currentFilter.value = filter.copy(name = name)
        resolvedSort = null
        persistState()
        loadScenes()
    }

    fun loadSceneDetail(sceneId: String) {
        viewModelScope.launch(StashCoroutineExceptionHandler()) {
            _sceneDetail.value = queryEngine.getScene(sceneId)
        }
    }

    fun clearSceneDetail() {
        _sceneDetail.value = null
    }

    fun toggleLoop() {
        _loop.value = !_loop.value
        persistState()
    }

    fun toggleAutoAdvance() {
        _autoAdvance.value = !_autoAdvance.value
        persistState()
    }

    companion object {
        private const val TAG = "ReelsViewModel"
        private const val PREF_SORT = "reels_sort"
        private const val PREF_SORT_DIR = "reels_sort_dir"
        private const val PREF_LOOP = "reels_loop"
        private const val PREF_AUTO_ADVANCE = "reels_auto_advance"
        private const val PREF_FILTER_NAME = "reels_filter_name"
    }
}
