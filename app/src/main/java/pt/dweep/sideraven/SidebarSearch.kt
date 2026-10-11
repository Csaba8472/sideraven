/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.feature.awesomebar.provider.SearchTermSuggestionsProvider
import mozilla.components.concept.toolbar.AutocompleteProvider
import org.mozilla.fenix.search.SearchFragmentAction
import org.mozilla.fenix.search.SearchFragmentState
import org.mozilla.fenix.search.SearchFragmentStore
import org.mozilla.fenix.search.ext.searchEngineShortcuts
import org.mozilla.fenix.components.search.BOOKMARKS_SEARCH_ENGINE_ID
import org.mozilla.fenix.components.search.HISTORY_SEARCH_ENGINE_ID
import org.mozilla.fenix.components.search.TABS_SEARCH_ENGINE_ID
import org.mozilla.fenix.search.SearchEngineSource
import androidx.lifecycle.lifecycleScope
import mozilla.components.browser.state.search.DefaultSearchEngineProvider
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.concept.engine.EngineSession.LoadUrlFlags
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.components.metrics.MetricsUtils
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.openToBrowser
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import org.mozilla.fenix.search.awesomebar.DefaultSuggestionIconProvider
import org.mozilla.fenix.search.awesomebar.DefaultSuggestionsStringsProvider
import org.mozilla.fenix.search.awesomebar.SearchSuggestionsProvidersBuilder
import org.mozilla.fenix.search.awesomebar.toSearchProviderState
import org.mozilla.fenix.search.createInitialSearchFragmentState
import pt.dweep.sideraven.ui.SearchRequest

/**
 * The Search Pop-up's link to Fenix: Fenix's own suggestion engine (history, bookmarks, open and
 * synced tabs, search engine suggestions), with every action pointed at the pop-up's target, the
 * current tab or a new one. Nothing in Fenix's own screens is touched.
 */
object SidebarSearch {
    /**
     * The inline completion for [text], from the sources Fenix's toolbar uses: history and bookmarks
     * (as Fenix's settings allow) and its shipped domains for the default engine; the scope's own
     * source for Bookmarks, History or Tabs; none for another search engine.
     */
    suspend fun autocomplete(
        activity: HomeActivity,
        text: String,
        engine: SearchEngine?,
    ): String? {
        val components = activity.components
        val settings = components.settings
        val private = activity.browsingModeManager.mode.isPrivate
        val sources: List<AutocompleteProvider> =
            when (engine?.takeIf { it.type == SearchEngine.Type.APPLICATION }?.id) {
                TABS_SEARCH_ENGINE_ID -> listOf(components.core.sessionAutocompleteProvider, components.backgroundServices.syncedTabsAutocompleteProvider)
                BOOKMARKS_SEARCH_ENGINE_ID -> listOf(components.core.bookmarksStorage)
                HISTORY_SEARCH_ENGINE_ID -> listOf(components.core.historyStorage)
                else ->
                    if (engine != null && engine.type != SearchEngine.Type.APPLICATION) {
                        emptyList()
                    } else {
                        listOfNotNull(
                            components.core.historyStorage.takeIf { settings.shouldShowHistorySuggestions && (!private || settings.shouldShowHistorySuggestionsInPrivate) },
                            components.core.bookmarksStorage.takeIf { settings.shouldShowBookmarkSuggestions && (!private || settings.shouldShowBookmarkSuggestionsInPrivate) },
                            components.core.domainsAutocompleteProvider,
                        )
                    }
            }
        return sources.sorted().firstNotNullOfOrNull { it.getAutocompleteSuggestion(text)?.text }
    }

    /** Deletes a removed suggestion from history: a past search's metadata, or a page's visits. */
    fun removeFromHistory(
        activity: HomeActivity,
        item: AwesomeBar.GroupedSuggestion,
    ) {
        val suggestion = item.suggestion as? AwesomeBar.Suggestion ?: return
        val history = activity.components.core.historyStorage
        activity.lifecycleScope.launch(Dispatchers.IO) {
            if (suggestion.provider is SearchTermSuggestionsProvider) {
                suggestion.title?.let { history.deleteHistoryMetadata(it) }
            } else {
                suggestion.description?.let { history.deleteVisitsFor(it) }
            }
        }
    }

    /**
     * Fenix's private-browsing suggestions question, under Fenix's conditions (private, not yet
     * answered, engine suggestions not already on there). [onAllowed] rebuilds the suggestions.
     */
    fun privatePrompt(
        activity: HomeActivity,
        onAllowed: () -> Unit,
        onLearnMore: () -> Unit,
    ): pt.dweep.sideraven.ui.PrivateSuggestionsPrompt {
        val settings = activity.components.settings
        return object : pt.dweep.sideraven.ui.PrivateSuggestionsPrompt {
            override val needed: Boolean
                get() =
                    activity.browsingModeManager.mode.isPrivate &&
                        !settings.showSearchSuggestionsInPrivateOnboardingFinished &&
                        !(settings.shouldShowSearchSuggestions && settings.shouldShowSearchSuggestionsInPrivate)

            override fun answer(allow: Boolean) {
                settings.shouldShowSearchSuggestionsInPrivate = allow
                settings.showSearchSuggestionsInPrivateOnboardingFinished = true
                if (allow) onAllowed()
            }

            override fun learnMore() {
                activity.components.useCases.tabsUseCases.addTab(
                    url = org.mozilla.fenix.settings.SupportUtils.getGenericSumoURLForTopic(org.mozilla.fenix.settings.SupportUtils.SumoTopic.SEARCH_SUGGESTION),
                    selectTab = true,
                    private = true,
                )
                onLearnMore()
            }
        }
    }

    /** Fenix's engine shortcuts: search engines, then its Bookmarks, History and Tabs scopes. */
    fun engines(activity: HomeActivity): List<SearchEngine> = activity.components.core.store.state.search.searchEngineShortcuts

    /** Fenix's suggestion sources for [request]; [onDone] closes the pop-up after an action. */
    fun providers(
        activity: HomeActivity,
        request: SearchRequest,
        engine: SearchEngine? = null,
        onDone: () -> Unit,
    ): List<AwesomeBar.SuggestionProvider> {
        val components = activity.components
        val selectedTab = components.core.store.state.selectedTabId
        val initial =
            createInitialSearchFragmentState(
                context = activity,
                components = components,
                tabId = if (request.newTab) null else selectedTab,
                pastedText = null,
                searchAccessPoint = MetricsUtils.Source.NONE,
            )
        // Fenix's search screen sets the engine once its store starts; the pop-up has no store, so it
        // sets it here, or the engine's suggestions (Google's, say) never appear.
        val default = components.core.store.state.search.selectedOrDefaultSearchEngine
        val base = default?.let { initial.copy(searchEngineSource = SearchEngineSource.Default(it), defaultEngine = it) } ?: initial
        val state = engine?.let { picked(activity, base, it) } ?: base
        val builder =
            SearchSuggestionsProvidersBuilder(
                components = components,
                scope = activity.lifecycleScope,
                browsingModeManager = activity.browsingModeManager,
                includeSelectedTab = request.newTab,
                loadUrlUseCase =
                    object : SessionUseCases.LoadUrlUseCase {
                        override fun invoke(
                            url: String,
                            flags: LoadUrlFlags,
                            additionalHeaders: Map<String, String>?,
                            originalInput: String?,
                        ) {
                            open(activity, url, request.newTab, flags = flags)
                            onDone()
                        }
                    },
                searchUseCase =
                    object : SearchUseCases.SearchUseCase {
                        override fun invoke(
                            searchTerms: String,
                            searchEngine: SearchEngine?,
                            parentSessionId: String?,
                        ) {
                            open(activity, searchTerms, request.newTab, engine = searchEngine, forceSearch = true)
                            onDone()
                        }
                    },
                selectTabUseCase =
                    object : TabsUseCases.SelectTabUseCase {
                        override fun invoke(tabId: String) {
                            components.useCases.tabsUseCases.selectTab(tabId)
                            sidebarNavController(activity)?.openToBrowser()
                            onDone()
                        }
                    },
                suggestionsStringsProvider = DefaultSuggestionsStringsProvider(activity, DefaultSearchEngineProvider(components.core.store)),
                suggestionIconProvider = DefaultSuggestionIconProvider(activity),
                onSearchEngineSuggestionSelected = {},
            )
        return builder.getProvidersToAdd(state.toSearchProviderState()).toList()
    }

    /** Enter in the pop-up: load the text as an address, or search for it, in the target. */
    fun submit(
        activity: HomeActivity,
        request: SearchRequest,
        text: String,
        engine: SearchEngine? = null,
    ) = open(activity, text, request.newTab, engine, forceSearch = engine != null)

    /** The search state for a picked engine or scope, as Fenix's search screen would set it. */
    private fun picked(
        activity: HomeActivity,
        base: SearchFragmentState,
        engine: SearchEngine,
    ): SearchFragmentState {
        val store = SearchFragmentStore(base)
        val mode = activity.browsingModeManager.mode
        val settings = activity.components.settings
        val application = engine.type == SearchEngine.Type.APPLICATION
        store.dispatch(
            when {
                application && engine.id == HISTORY_SEARCH_ENGINE_ID -> SearchFragmentAction.SearchHistoryEngineSelected(engine)
                application && engine.id == BOOKMARKS_SEARCH_ENGINE_ID -> SearchFragmentAction.SearchBookmarksEngineSelected(engine)
                application && engine.id == TABS_SEARCH_ENGINE_ID -> SearchFragmentAction.SearchTabsEngineSelected(engine)
                engine == base.defaultEngine -> SearchFragmentAction.SearchDefaultEngineSelected(engine, mode, settings)
                else -> SearchFragmentAction.SearchShortcutEngineSelected(engine, mode, settings)
            },
        )
        return store.state
    }

    private fun open(
        activity: HomeActivity,
        searchTermOrUrl: String,
        newTab: Boolean,
        engine: SearchEngine? = null,
        forceSearch: Boolean = false,
        flags: LoadUrlFlags = LoadUrlFlags.none(),
    ) {
        // Fenix decides URL vs search and opens the browser screen, exactly as its toolbar does.
        sidebarNavController(activity)?.openToBrowser()
        activity.components.useCases.fenixBrowserUseCases.loadUrlOrSearch(
            searchTermOrURL = searchTermOrUrl,
            newTab = newTab,
            private = activity.browsingModeManager.mode.isPrivate,
            forceSearch = forceSearch,
            searchEngine = engineFor(engine, activity.components.core.store.state.search),
            flags = flags,
        )
    }

    /**
     * The engine to search with: the one a suggestion named, else the selected or default engine.
     * Fenix treats a missing engine as "load the text as an address", so this must not be null.
     */
    fun engineFor(
        requested: SearchEngine?,
        search: mozilla.components.browser.state.state.SearchState,
    ): SearchEngine? = requested ?: search.selectedOrDefaultSearchEngine
}
