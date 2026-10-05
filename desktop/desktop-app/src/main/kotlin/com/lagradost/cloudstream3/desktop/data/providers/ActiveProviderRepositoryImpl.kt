package com.lagradost.cloudstream3.desktop.data.providers

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.domain.providers.repository.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class ActiveProviderRepositoryImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ActiveProviderRepository {
    private val _allRealProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    override val allRealProviders: StateFlow<List<MainAPI>> = _allRealProviders.asStateFlow()

    private val _activeProviderKeys = MutableStateFlow<List<String>>(emptyList())
    override val activeProviderKeys: StateFlow<List<String>> = _activeProviderKeys.asStateFlow()

    private val _activeProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    override val activeProviders: StateFlow<List<MainAPI>> = _activeProviders.asStateFlow()

    private val _currentSelectedProvider = MutableStateFlow<MainAPI?>(null)
    override val currentSelectedProvider: StateFlow<MainAPI?> = _currentSelectedProvider.asStateFlow()

    init {
        // Initial load from storage
        scope.launch(Dispatchers.IO) {
            val savedKeys = DesktopDataStore.getKey<List<String>>(PreferenceKeys.PREF_ACTIVE_PROVIDERS)
            val fallbackKey = DesktopDataStore.getKey<String>(PreferenceKeys.PREF_SELECTED_PROVIDER)
            val initialKeys = savedKeys ?: listOfNotNull(fallbackKey)
            _activeProviderKeys.value = initialKeys
            refreshProviders()
        }

        // Re-sync whenever repositories/plugins change
        scope.launch {
            DesktopRepositoryManager.syncGeneration.collectLatest {
                refreshProviders()
            }
        }
    }

    override fun isRealContentProvider(api: MainAPI): Boolean {
        if (api.name.equals("NONE", ignoreCase = true)) return false
        if (api.providerType == ProviderType.MetaProvider) return false
        return true
    }

    override fun getProviderKey(api: MainAPI): String {
        val src = api.sourcePlugin
        if (!src.isNullOrBlank() && src != "built-in") {
            val folder = File(src).parentFile?.name ?: ""
            if (folder.isNotBlank()) return "$folder::${api.name}"
        }
        return api.name
    }

    override fun matchesKey(api: MainAPI, key: String): Boolean {
        return getProviderKey(api) == key || api.name == key || api.name == key.substringAfter("::")
    }

    override fun refreshProviders() {
        val allApis = APIHolder.allProviders.filter { isRealContentProvider(it) }
        _allRealProviders.value = allApis

        val currentKeys = _activeProviderKeys.value
        val filtered = currentKeys.mapNotNull { key ->
            allApis.firstOrNull { matchesKey(it, key) }
        }.filter { api ->
            // Prevent selecting broken Netmirror providers that play the 10-minute spam abuse video
            val n = api.name.lowercase()
            !n.contains("netflix") && !n.contains("prime") && !n.contains("hotstar") && !n.contains("cnc verse")
        }

        val resolvedActive: List<MainAPI> = if (filtered.isEmpty()) {
            val preferred = allApis.firstOrNull {
                it.name.contains("MovieBox", ignoreCase = true)
            } ?: allApis.firstOrNull {
                it.name.contains("Castle", ignoreCase = true)
            }
            listOfNotNull(preferred ?: allApis.firstOrNull())
        } else {
            filtered
        }

        _activeProviders.value = resolvedActive

        val selected = _currentSelectedProvider.value
        val isSelectedBroken = selected != null && (
            selected.name.contains("netflix", ignoreCase = true) ||
            selected.name.contains("prime", ignoreCase = true) ||
            selected.name.contains("hotstar", ignoreCase = true) ||
            selected.name.contains("cnc verse", ignoreCase = true)
        )
        if (selected == null || isSelectedBroken || allApis.none { it.name == selected.name && it.sourcePlugin == selected.sourcePlugin }) {
            _currentSelectedProvider.value = resolvedActive.firstOrNull() ?: allApis.firstOrNull()
        }
    }

    override fun setActiveProviders(keys: List<String>) {
        _activeProviderKeys.value = keys
        refreshProviders()
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_ACTIVE_PROVIDERS, keys)
        }
    }

    override fun setSelectedProvider(api: MainAPI) {
        val previous = _currentSelectedProvider.value
        if (previous != null && previous.name != api.name) {
            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.closeProxySession()
        }
        _currentSelectedProvider.value = api
        val key = getProviderKey(api)
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_SELECTED_PROVIDER, key)
        }
    }

    override fun setSelectedProviderByName(name: String, sourcePlugin: String?) {
        val all = _allRealProviders.value
        val matched = all.firstOrNull {
            it.name == name && (sourcePlugin == null || it.sourcePlugin == sourcePlugin)
        } ?: all.firstOrNull { it.name == name }
        if (matched != null) {
            setSelectedProvider(matched)
        }
    }
}
