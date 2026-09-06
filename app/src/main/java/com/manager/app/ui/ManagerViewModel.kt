package com.manager.app.ui

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.manager.app.ManagerGraph
import com.manager.app.data.ApkExtractor
import com.manager.app.data.AppEntry
import com.manager.app.data.AppFilter
import com.manager.app.data.InventoryState
import com.manager.app.data.ManagerPreferences
import com.manager.app.data.SortDirection
import com.manager.app.data.SortKey
import com.manager.app.data.ThemeMode
import com.manager.app.data.UninstallCoordinator
import com.manager.app.data.UsageSnapshot
import com.manager.app.data.UsageWindow
import com.manager.app.domain.Insights
import com.manager.app.domain.applyFilter
import com.manager.app.domain.applySearch
import com.manager.app.domain.applySort
import com.manager.app.domain.buildInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A transient message. Never a system Toast — the product draws its own. */
@Immutable
data class Notice(
    val id: Long,
    val title: String,
    val body: String? = null,
    val tone: NoticeTone = NoticeTone.Neutral,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

enum class NoticeTone { Neutral, Positive, Warning }

@Immutable
data class ExtractionState(
    val queue: List<AppEntry>,
    val index: Int = 0,
    val currentProgress: Float = 0f,
    val results: List<ApkExtractor.Outcome> = emptyList(),
    val finished: Boolean = false,
    val cancelled: Boolean = false,
) {
    val current: AppEntry? get() = queue.getOrNull(index)
    val total: Int get() = queue.size
    val succeeded: List<ApkExtractor.Outcome.Success> get() = results.filterIsInstance<ApkExtractor.Outcome.Success>()
    val failed: List<ApkExtractor.Outcome.Failure> get() = results.filterIsInstance<ApkExtractor.Outcome.Failure>()
    val overallProgress: Float
        get() = if (total == 0) 0f else ((index + currentProgress) / total).coerceIn(0f, 1f)
}

@Immutable
data class UninstallState(
    val queue: List<AppEntry>,
    val index: Int = 0,
    val removed: List<String> = emptyList(),
    val kept: List<String> = emptyList(),
    val failures: List<Pair<String, String>> = emptyList(),
    val finished: Boolean = false,
) {
    val current: AppEntry? get() = queue.getOrNull(index)
    val total: Int get() = queue.size
}

/**
 * Intents the UI needs the activity to launch. Typed rather than sniffed, because two of them
 * must come back to us: the uninstall queue can only advance once its screen closes, and usage
 * access must be re-checked the moment the user returns from Settings.
 */
enum class ActivityRequestKind { Uninstall, UsageSettings, General }

@Immutable
data class ActivityRequest(val kind: ActivityRequestKind, val intent: Intent)

/** Where the user is. Deliberately flat — this product has no deep hierarchy. */
enum class Destination(val title: String) { Dashboard("Overview"), Apps("Apps"), Usage("Usage") }

@Immutable
data class BrowseState(
    val query: String = "",
    val filter: AppFilter = AppFilter.All,
    val sortKey: SortKey = SortKey.Name,
    val sortDirection: SortDirection = SortDirection.Ascending,
    val selection: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
)

/**
 * One view model for the whole app.
 *
 * Every screen reads the same inventory, so splitting this per screen would mean scanning the
 * device more than once and letting two screens disagree about what is installed. Derived lists
 * are computed off the main thread and cached as state flows.
 */
class ManagerViewModel(private val graph: ManagerGraph) : ViewModel() {

    val inventory: StateFlow<InventoryState> = graph.packages.state
    val usageAccess: StateFlow<Boolean> = graph.permissions.usageAccess

    private val _usage = MutableStateFlow(UsageSnapshot.Empty)
    val usage: StateFlow<UsageSnapshot> = _usage.asStateFlow()

    private val _preferences = MutableStateFlow(ManagerPreferences())
    val preferences: StateFlow<ManagerPreferences> = _preferences.asStateFlow()

    private val _browse = MutableStateFlow(BrowseState())
    val browse: StateFlow<BrowseState> = _browse.asStateFlow()

    private val _destination = MutableStateFlow(Destination.Dashboard)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    private val _detail = MutableStateFlow<DetailRequest?>(null)
    val detail: StateFlow<DetailRequest?> = _detail.asStateFlow()

    private val _extraction = MutableStateFlow<ExtractionState?>(null)
    val extraction: StateFlow<ExtractionState?> = _extraction.asStateFlow()

    private val _uninstall = MutableStateFlow<UninstallState?>(null)

    /** Apps staged for removal, awaiting the product's own confirmation before Android's. */
    private val _uninstallConfirm = MutableStateFlow<List<AppEntry>>(emptyList())
    val uninstallConfirm: StateFlow<List<AppEntry>> = _uninstallConfirm.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val noticeChannel = Channel<Notice>(Channel.BUFFERED)
    val notices = noticeChannel.receiveAsFlow()

    private val activityRequests = Channel<ActivityRequest>(Channel.BUFFERED)
    val activityIntents = activityRequests.receiveAsFlow()

    private var extractionJob: Job? = null
    private var noticeSeed = 0L

    val insights: StateFlow<Insights> = combine(
        inventory,
        _usage,
        usageAccess,
    ) { state, usage, access ->
        buildInsights(state.apps, usage, access)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Insights.Empty)

    /** The list the Apps screen renders: filtered, searched and sorted, off the main thread. */
    val visibleApps: StateFlow<List<AppEntry>> = combine(
        inventory,
        _browse,
        _usage,
    ) { state, browse, usage ->
        state.apps
            .applyFilter(browse.filter, usage)
            .applySearch(browse.query)
            .applySort(browse.sortKey, browse.sortDirection, usage)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            graph.preferences.preferences.collect { prefs ->
                val first = _preferences.value
                _preferences.value = prefs
                if (first.sortKey != prefs.sortKey || first.sortDirection != prefs.sortDirection) {
                    _browse.value = _browse.value.copy(sortKey = prefs.sortKey, sortDirection = prefs.sortDirection)
                }
            }
        }
        viewModelScope.launch {
            graph.uninstalls.events.collect(::onUninstallEvent)
        }
        graph.packages.observePackageChanges()
    }

    fun onStart() {
        val granted = graph.permissions.refresh()
        if (inventory.value.apps.isEmpty()) graph.packages.refresh()
        viewModelScope.launch {
            _usage.value = graph.usage.load(_preferences.value.usageWindow)
        }
        if (!granted && _browse.value.filter == AppFilter.Dormant) {
            _browse.value = _browse.value.copy(filter = AppFilter.All)
        }
    }

    override fun onCleared() {
        graph.packages.stopObserving()
        super.onCleared()
    }

    // ---- Navigation -------------------------------------------------------------------------

    fun navigate(destination: Destination) {
        if (_destination.value == destination) return
        _destination.value = destination
        if (destination != Destination.Apps) exitSelection()
    }

    fun openSettings() { _settingsOpen.value = true }
    fun closeSettings() { _settingsOpen.value = false }

    // ---- Detail surface ---------------------------------------------------------------------

    fun openDetail(request: DetailRequest) { _detail.value = request }
    fun closeDetail() { _detail.value = null }

    // ---- Browsing ---------------------------------------------------------------------------

    fun setQuery(value: String) {
        _browse.value = _browse.value.copy(query = value)
    }

    fun setFilter(filter: AppFilter) {
        val current = _browse.value
        // Filters that describe an ordering bring their ordering with them; anything else keeps
        // whatever the user last chose.
        val (key, direction) = when (filter) {
            AppFilter.Largest -> SortKey.Size to SortDirection.Descending
            AppFilter.RecentlyInstalled -> SortKey.Installed to SortDirection.Descending
            AppFilter.RecentlyUpdated -> SortKey.Updated to SortDirection.Descending
            AppFilter.Dormant -> SortKey.Usage to SortDirection.Ascending
            else -> current.sortKey to current.sortDirection
        }
        _browse.value = current.copy(filter = filter, sortKey = key, sortDirection = direction)
    }

    fun setSort(key: SortKey, direction: SortDirection) {
        _browse.value = _browse.value.copy(sortKey = key, sortDirection = direction)
        viewModelScope.launch { graph.preferences.setSort(key, direction) }
    }

    fun toggleSortDirection() {
        val current = _browse.value
        setSort(
            current.sortKey,
            if (current.sortDirection == SortDirection.Ascending) SortDirection.Descending else SortDirection.Ascending,
        )
    }

    // ---- Selection --------------------------------------------------------------------------

    fun beginSelection(packageName: String) {
        _browse.value = _browse.value.copy(selectionMode = true, selection = setOf(packageName))
    }

    fun toggleSelection(packageName: String) {
        val current = _browse.value
        val next = if (packageName in current.selection) current.selection - packageName else current.selection + packageName
        _browse.value = current.copy(selection = next, selectionMode = next.isNotEmpty())
    }

    /**
     * A toggle, not a one-way door: pressing it when everything is already selected clears the
     * selection, which is the only sane meaning for a control that is already "on".
     */
    fun toggleSelectAllVisible() {
        val visible = visibleApps.value.map { it.packageName }.toSet()
        val current = _browse.value
        if (visible.isNotEmpty() && current.selection.containsAll(visible)) {
            _browse.value = current.copy(selection = emptySet(), selectionMode = false)
        } else {
            _browse.value = current.copy(selection = visible, selectionMode = visible.isNotEmpty())
        }
    }

    fun exitSelection() {
        if (!_browse.value.selectionMode && _browse.value.selection.isEmpty()) return
        _browse.value = _browse.value.copy(selection = emptySet(), selectionMode = false)
    }

    fun selectedEntries(): List<AppEntry> {
        val selection = _browse.value.selection
        if (selection.isEmpty()) return emptyList()
        return inventory.value.apps.filter { it.packageName in selection }
    }

    // ---- Usage ------------------------------------------------------------------------------

    fun setUsageWindow(window: UsageWindow) {
        viewModelScope.launch {
            graph.preferences.setUsageWindow(window)
            _usage.value = graph.usage.load(window)
        }
    }

    fun requestUsageAccess() {
        graph.permissions.usageAccessIntents().firstOrNull()?.let {
            activityRequests.trySend(ActivityRequest(ActivityRequestKind.UsageSettings, it))
        }
    }

    fun onReturnFromSettings() {
        val granted = graph.permissions.refresh()
        if (granted) {
            viewModelScope.launch {
                _usage.value = graph.usage.load(_preferences.value.usageWindow)
                graph.packages.refresh(force = true)
                notify(Notice(nextId(), "Usage access granted", "Storage figures and usage ranking are live.", NoticeTone.Positive))
            }
        }
    }

    // ---- Refresh ----------------------------------------------------------------------------

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        graph.permissions.refresh()
        graph.packages.refresh(force = true)
        viewModelScope.launch {
            _usage.value = graph.usage.load(_preferences.value.usageWindow)
            // A refresh that finishes instantly reads as a glitch; hold the state briefly so the
            // motion completes and the user sees that something happened.
            kotlinx.coroutines.delay(520)
            _refreshing.value = false
        }
    }

    // ---- Preferences ------------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { graph.preferences.setThemeMode(mode) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { graph.preferences.setHaptics(value) }
    fun completeOnboarding() = viewModelScope.launch { graph.preferences.setOnboarded(true) }

    // ---- Extraction -------------------------------------------------------------------------

    fun extract(entries: List<AppEntry>) {
        if (entries.isEmpty() || _extraction.value?.finished == false) return
        _extraction.value = ExtractionState(queue = entries)
        extractionJob = viewModelScope.launch {
            entries.forEachIndexed { index, entry ->
                _extraction.value = _extraction.value?.copy(index = index, currentProgress = 0f)
                val outcome = graph.extractor.extract(entry) { written, total ->
                    val fraction = if (total <= 0) 0f else (written.toFloat() / total).coerceIn(0f, 1f)
                    _extraction.value = _extraction.value?.copy(currentProgress = fraction)
                }
                _extraction.value = _extraction.value?.let {
                    it.copy(results = it.results + outcome, currentProgress = 1f)
                }
            }
            _extraction.value = _extraction.value?.copy(finished = true)
        }
    }

    fun cancelExtraction() {
        extractionJob?.cancel()
        _extraction.value = _extraction.value?.copy(finished = true, cancelled = true)
    }

    fun dismissExtraction() {
        val state = _extraction.value
        _extraction.value = null
        if (state != null && state.succeeded.isNotEmpty()) exitSelection()
    }

    fun share(outcome: ApkExtractor.Outcome.Success) {
        activityRequests.trySend(
            ActivityRequest(
                ActivityRequestKind.General,
                Intent.createChooser(graph.extractor.shareIntent(outcome), "Share ${outcome.displayName}"),
            ),
        )
    }

    fun openExtracted(outcome: ApkExtractor.Outcome.Success) {
        activityRequests.trySend(ActivityRequest(ActivityRequestKind.General, graph.extractor.viewIntent(outcome)))
    }

    // ---- Uninstall --------------------------------------------------------------------------

    fun requestUninstallConfirmation(entries: List<AppEntry>) {
        if (entries.isEmpty()) return
        _uninstallConfirm.value = entries
    }

    fun dismissUninstallConfirmation() {
        _uninstallConfirm.value = emptyList()
    }

    fun confirmUninstall() {
        val entries = _uninstallConfirm.value
        _uninstallConfirm.value = emptyList()
        startUninstall(entries)
    }

    fun startUninstall(entries: List<AppEntry>) {
        val removable = entries.filter { it.isUninstallable }
        val blocked = entries.size - removable.size
        if (removable.isEmpty()) {
            notify(
                Notice(
                    nextId(),
                    "Nothing to uninstall",
                    "Android does not allow these system packages to be removed.",
                    NoticeTone.Warning,
                ),
            )
            return
        }
        if (blocked > 0) {
            notify(
                Notice(
                    nextId(),
                    "$blocked skipped",
                    "System packages without updates cannot be removed.",
                    NoticeTone.Warning,
                ),
            )
        }
        _uninstall.value = UninstallState(queue = removable)
        requestCurrentUninstall()
    }

    private fun requestCurrentUninstall() {
        val state = _uninstall.value ?: return
        val entry = state.current
        if (entry == null) {
            finishUninstall()
            return
        }
        graph.uninstalls.request(entry.packageName)
    }

    private fun onUninstallEvent(event: UninstallCoordinator.Event) {
        val state = _uninstall.value ?: return
        when (event) {
            is UninstallCoordinator.Event.AwaitingConfirmation -> {
                activityRequests.trySend(ActivityRequest(ActivityRequestKind.Uninstall, event.intent))
            }

            is UninstallCoordinator.Event.Removed -> {
                graph.packages.forget(event.packageName)
                graph.icons.evict(event.packageName)
                advanceUninstall(state.copy(removed = state.removed + event.packageName))
            }

            is UninstallCoordinator.Event.Declined ->
                advanceUninstall(state.copy(kept = state.kept + event.packageName))

            is UninstallCoordinator.Event.Failed ->
                advanceUninstall(state.copy(failures = state.failures + (event.packageName to event.reason)))
        }
    }

    /**
     * Called when the system uninstall screen closes. The broadcast is authoritative, but some
     * OEM builds drop it, so the package list is checked as a backstop.
     */
    fun onUninstallScreenClosed() {
        val state = _uninstall.value ?: return
        val entry = state.current ?: return
        if (!graph.uninstalls.stillInstalled(entry.packageName)) {
            graph.packages.forget(entry.packageName)
            graph.icons.evict(entry.packageName)
            advanceUninstall(state.copy(removed = state.removed + entry.packageName))
        }
    }

    private fun advanceUninstall(state: UninstallState) {
        val next = state.copy(index = state.index + 1)
        _uninstall.value = next
        if (next.index >= next.queue.size) {
            finishUninstall()
        } else {
            requestCurrentUninstall()
        }
    }

    private fun finishUninstall() {
        val state = _uninstall.value ?: return
        _uninstall.value = state.copy(finished = true)
        val removed = state.removed.size
        val kept = state.kept.size
        val failed = state.failures.size

        val notice = when {
            removed > 0 && (kept + failed) == 0 -> Notice(
                nextId(),
                if (removed == 1) "1 app removed" else "$removed apps removed",
                null,
                NoticeTone.Positive,
            )

            removed > 0 -> Notice(
                nextId(),
                "$removed removed, ${kept + failed} kept",
                state.failures.firstOrNull()?.second,
                NoticeTone.Neutral,
            )

            failed > 0 -> Notice(nextId(), "Uninstall blocked", state.failures.first().second, NoticeTone.Warning)
            else -> Notice(nextId(), "Nothing was removed", null, NoticeTone.Neutral)
        }
        notify(notice)

        _uninstall.value = null
        if (removed > 0) {
            _browse.value = _browse.value.copy(
                selection = _browse.value.selection - state.removed.toSet(),
            ).let { if (it.selection.isEmpty()) it.copy(selectionMode = false) else it }
            if (_detail.value?.entry?.packageName in state.removed) _detail.value = null
        }
    }

    // ---- Notices ----------------------------------------------------------------------------

    fun notify(notice: Notice) {
        noticeChannel.trySend(notice)
    }

    fun nextId(): Long = ++noticeSeed

    class Factory(private val graph: ManagerGraph) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ManagerViewModel(graph) as T
    }
}
