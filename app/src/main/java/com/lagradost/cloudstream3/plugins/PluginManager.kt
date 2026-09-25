package com.lagradost.cloudstream3.plugins

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.removePluginMapping
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.AutoDownloadMode
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.InternalAPI
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainAPI.Companion.settingsForProvider
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.MainActivity.Companion.lastError
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.PROVIDER_STATUS_OK
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.debugPrint
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.plugins.RepositoryManager.ONLINE_PLUGINS_FOLDER
import com.lagradost.cloudstream3.plugins.RepositoryManager.PREBUILT_REPOSITORIES
import com.lagradost.cloudstream3.plugins.RepositoryManager.downloadPluginToFile
import com.lagradost.cloudstream3.plugins.RepositoryManager.getRepoPlugins
import com.lagradost.cloudstream3.plugins.RepositoryManager.sha256
import com.lagradost.cloudstream3.ui.settings.extensions.REPOSITORIES_KEY
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.sanitizeFilename
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.txt
import dalvik.system.PathClassLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStreamReader

// Different keys for local and not since local can be removed at any time without app knowing, hence the local are getting rebuilt on every app start
const val PLUGINS_KEY = "PLUGINS_KEY"
const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"

const val EXTENSIONS_CHANNEL_ID = "cloudstream3.extensions"
const val EXTENSIONS_CHANNEL_NAME = "Extensions"
const val EXTENSIONS_CHANNEL_DESCRIPT = "Extension notification channel"

// Data class for internal storage
@Serializable
data class PluginData(
    @JsonProperty("internalName") @SerialName("internalName") val internalName: String,
    @JsonProperty("url") @SerialName("url") val url: String?,
    @JsonProperty("isOnline") @SerialName("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") @SerialName("filePath") val filePath: String,
    @JsonProperty("version") @SerialName("version") val version: Int,
) {
    @WorkerThread
    fun toSitePlugin(): SitePlugin {
        return SitePlugin(
            this.filePath,
            PROVIDER_STATUS_OK,
            maxOf(1, version),
            1,
            internalName,
            internalName,
            emptyList(),
            File(this.filePath).name,
            null,
            null,
            null,
            null,
            File(this.filePath).length(),
            // No file hash for local plugins. Local plugins have no use for the hash, and it's expensive to compute.
            null
        )
    }
}

// This is used as a placeholder / not set version
const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE

// This always updates
const val PLUGIN_VERSION_ALWAYS_UPDATE = -1

object PluginManager {
    // Prevent multiple writes at once
    val lock = Mutex()

    const val TAG = "PluginManager"

    private var hasCreatedNotChanel = false

    /**
     * Store data about the plugin for fetching later
     * */
    private suspend fun setPluginData(data: PluginData) {
        lock.withLock {
            if (data.isOnline) {
                val plugins = getPluginsOnline()
                val newPlugins = plugins.filter { it.filePath != data.filePath } + data
                setKey(PLUGINS_KEY, newPlugins)
            } else {
                val plugins = getPluginsLocal()
                setKey(PLUGINS_KEY_LOCAL, plugins.filter { it.filePath != data.filePath } + data)
            }
        }
    }

    private suspend fun deletePluginData(data: PluginData?) {
        if (data == null) return
        lock.withLock {
            if (data.isOnline) {
                val plugins = getPluginsOnline().filter { it.url != data.url }
                setKey(PLUGINS_KEY, plugins)
            } else {
                val plugins = getPluginsLocal().filter { it.filePath != data.filePath }
                setKey(PLUGINS_KEY_LOCAL, plugins)
            }
        }
    }

    suspend fun deleteRepositoryData(repositoryPath: String) {
        lock.withLock {
            val plugins = getPluginsOnline().filter {
                !it.filePath.contains(repositoryPath)
            }
            val file = File(repositoryPath)
            safe {
                if (file.exists()) file.deleteRecursively()
            }
            setKey(PLUGINS_KEY, plugins)
        }
    }

    /**
     * Deletes all generated oat files which will force Android to recompile the dex extensions.
     * This might fix unrecoverable SIGSEGV exceptions when old oat files are loaded in a new app update.
     */
    fun deleteAllOatFiles(context: Context) {
        File("${context.filesDir}/${ONLINE_PLUGINS_FOLDER}").listFiles()?.forEach { repo ->
            repo.listFiles { file -> file.name == "oat" && file.isDirectory }?.forEach { file ->
                val success = file.deleteRecursively()
                Log.i(TAG, "Deleted oat directory: ${file.absolutePath} Success=$success")
            }
        }
    }

    fun getPluginsOnline(): Array<PluginData> {
        val list = getKey<Array<PluginData>>(PLUGINS_KEY) ?: emptyArray()
        return list.groupBy { it.internalName }.map { (_, items) ->
            items.maxByOrNull { it.version } ?: items.first()
        }.toTypedArray()
    }

    fun getPluginsLocal(): Array<PluginData> {
        return getKey<Array<PluginData>>(PLUGINS_KEY_LOCAL) ?: emptyArray()
    }

    private val CLOUD_STREAM_FOLDER =
        Environment.getExternalStorageDirectory().absolutePath + "/Cloudstream3/"

    private val LOCAL_PLUGINS_PATH = CLOUD_STREAM_FOLDER + "plugins"

    var currentlyLoading: String? = null

    // Maps filepath to plugin
    val plugins: MutableMap<String, BasePlugin> =
        LinkedHashMap<String, BasePlugin>()

    // Maps urls to plugin
    val urlPlugins: MutableMap<String, BasePlugin> =
        LinkedHashMap<String, BasePlugin>()

    private val classLoaders: MutableMap<PathClassLoader, BasePlugin> =
        HashMap<PathClassLoader, BasePlugin>()

    var loadedLocalPlugins = false
        private set

    var loadedOnlinePlugins = false
        private set

    private suspend fun maybeLoadPlugin(context: Context, file: File) {
        val name = file.name
        if (file.extension == "zip" || file.extension == "cs3") {
            loadPlugin(
                context,
                file,
                PluginData(name, null, false, file.absolutePath, PLUGIN_VERSION_NOT_SET)
            )
        } else {
            Log.i(TAG, "Skipping invalid plugin file: $file")
        }
    }

    // Helper class for updateAllOnlinePluginsAndLoadThem
    data class OnlinePluginData(
        val savedData: PluginData,
        val onlineData: PluginWrapper,
    ) {
        val isOutdated =
            onlineData.plugin.version > savedData.version ||
            onlineData.plugin.version == PLUGIN_VERSION_ALWAYS_UPDATE ||
            savedData.version == PLUGIN_VERSION_NOT_SET ||
            (onlineData.plugin.fileHash != null && File(savedData.filePath).exists() && sha256(File(savedData.filePath)) != onlineData.plugin.fileHash)
        val isDisabled = onlineData.plugin.status == PROVIDER_STATUS_DOWN

        fun validOnlineData(context: Context): Boolean {
            val expectedPath = getPluginPath(
                context,
                savedData.internalName,
                onlineData.repositoryData.url
            ).absolutePath
            return expectedPath == savedData.filePath ||
                    savedData.filePath.contains("/bundled/") ||
                    savedData.internalName.equals(onlineData.plugin.internalName, ignoreCase = true) ||
                    (savedData.url != null && savedData.url == onlineData.plugin.url)
        }
    }

    suspend fun loadSinglePlugin(context: Context, apiName: String): Boolean {
        if (apiName.equals("CNC Verse", ignoreCase = true) ||
            apiName.equals("CNC Verse Mobile", ignoreCase = true) ||
            apiName.equals("Netflix", ignoreCase = true) ||
            apiName.equals("NetflixM", ignoreCase = true) ||
            apiName.equals("Netflix Mirror", ignoreCase = true) ||
            apiName.equals("Prime Video", ignoreCase = true) ||
            apiName.equals("PrimeVideoM", ignoreCase = true) ||
            apiName.equals("Prime Video Mirror", ignoreCase = true) ||
            apiName.equals("Hotstar", ignoreCase = true) ||
            apiName.equals("HotstarM", ignoreCase = true) ||
            apiName.equals("HotStar Mirror", ignoreCase = true) ||
            apiName.equals("Disney", ignoreCase = true) ||
            apiName.equals("DisneyM", ignoreCase = true) ||
            apiName.equals("Disney+ Mirror", ignoreCase = true) ||
            apiName.equals("Disney Studio", ignoreCase = true) ||
            apiName.equals("Castle Tv", ignoreCase = true) ||
            apiName.equals("Castle TV", ignoreCase = true) ||
            apiName.equals("Castle TV (Use VLC)", ignoreCase = true) ||
            apiName.equals("CastleTvProvider", ignoreCase = true) ||
            apiName.equals("Ayushflix", ignoreCase = true) ||
            apiName.equals("Ayush Fliz", ignoreCase = true)) return true
        return false
    }

    /**
     * Needs to be run before other plugin loading because plugin loading can not be overwritten
     * 1. Gets all online data about the downloaded plugins
     * 2. If disabled do nothing
     * 3. If outdated download and load the plugin
     * 4. Else load the plugin normally
     *
     * DO NOT USE THIS IN A PLUGIN! It may case an infinite recursive loop lagging or crashing everyone's devices.
     * If you use it from a plugin, do not expect a stable jvmName, SO DO NOT USE IT!
     */
    fun unloadPluginByName(internalName: String) {
        val toUnload = synchronized(plugins) {
            plugins.filter { (path, plugin) ->
                path.contains(internalName, ignoreCase = true) ||
                plugin.filename?.contains(internalName, ignoreCase = true) == true ||
                plugin.filename?.let { File(it).nameWithoutExtension.equals(internalName, ignoreCase = true) } == true
            }.keys.toList()
        }
        for (path in toUnload) {
            unloadPlugin(path)
        }
    }

    /**
     * Needs to be run before other plugin loading because plugin loading can not be overwritten
     * 1. Gets all online data about the downloaded plugins
     * 2. If disabled do nothing
     * 3. If outdated download and load the plugin
     * 4. Else load the plugin normally
     *
     * DO NOT USE THIS IN A PLUGIN! It may case an infinite recursive loop lagging or crashing everyone's devices.
     * If you use it from a plugin, do not expect a stable jvmName, SO DO NOT USE IT!
     */
    @Suppress("FunctionName")
    @InternalAPI
    @Throws
    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem(activity: Activity) {
        assertNonRecursiveCallstack()

        ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(activity)

        val urls = (getKey<Array<RepositoryData>>(REPOSITORIES_KEY) ?: emptyArray()) + PREBUILT_REPOSITORIES
        val onlinePlugins = urls.toList().amap {
            getRepoPlugins(it) ?: emptyList()
        }.flatten().distinctBy { it.plugin.url }

        if (onlinePlugins.isEmpty()) {
            loadedOnlinePlugins = true
            afterPluginsLoadedEvent.invoke(false)
            return
        }

        val allPlugins = getPluginsOnline().flatMap { savedData ->
            onlinePlugins
                .filter { it.plugin.internalName.equals(savedData.internalName, ignoreCase = true) }
                .mapNotNull { onlineData ->
                    OnlinePluginData(savedData, onlineData).takeIf { it.validOnlineData(activity) }
                }
        }.distinctBy { it.onlineData.plugin.url }

        val updatedPlugins = mutableListOf<String>()

        allPlugins.amap { pluginData ->
            if (pluginData.isDisabled) {
                Log.i(TAG, "Unloading disabled plugin: ${pluginData.onlineData.plugin.name}")
                unloadPlugin(pluginData.savedData.filePath)
                unloadPluginByName(pluginData.savedData.internalName)
            } else if (pluginData.isOutdated) {
                Log.i(TAG, "Updating outdated plugin: ${pluginData.onlineData.plugin.name} (v${pluginData.savedData.version} -> v${pluginData.onlineData.plugin.version})")
                val targetFile = getPluginPath(
                    activity,
                    pluginData.savedData.internalName,
                    pluginData.onlineData.repositoryData.url
                )
                unloadPlugin(pluginData.savedData.filePath)
                unloadPluginByName(pluginData.savedData.internalName)

                if (downloadPlugin(
                        activity,
                        pluginData.onlineData.plugin.url,
                        pluginData.onlineData.plugin.fileHash,
                        pluginData.savedData.internalName,
                        targetFile,
                        true
                    )
                ) {
                    updatedPlugins.add(pluginData.onlineData.plugin.name)
                    try {
                        val oldFile = File(pluginData.savedData.filePath)
                        if (oldFile.absolutePath != targetFile.absolutePath && oldFile.exists() && oldFile.parentFile?.name == "bundled") {
                            oldFile.delete()
                        }
                    } catch (_: Throwable) {}
                }
            }
        }

        if (updatedPlugins.isNotEmpty()) {
            main {
                val message = activity.getString(R.string.plugins_updated_manually, updatedPlugins.size)
                showToast(message, Toast.LENGTH_SHORT)

                val notificationText = UiText.StringResource(
                    R.string.plugins_updated_manually,
                    listOf(updatedPlugins.size)
                )
                createNotification(activity, notificationText, updatedPlugins)
            }
            APIHolder.initAll()
        }

        loadedOnlinePlugins = true
        afterPluginsLoadedEvent.invoke(false)
        Log.i(TAG, "updateAllOnlinePluginsAndLoadThem finished. ${updatedPlugins.size} plugins updated.")
    }

    /**
     * Automatically download plugins not yet existing on local
     * 1. Gets all online data from online plugins repo
     * 2. Fetch all not downloaded plugins
     * 3. Download them and reload plugins
     *
     * DO NOT USE THIS IN A PLUGIN! It may case an infinite recursive loop lagging or crashing everyone's devices.
     * If you use it from a plugin, do not expect a stable jvmName, SO DO NOT USE IT!
     */
    @Suppress("FunctionName")
    @InternalAPI
    @Throws
    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_downloadNotExistingPluginsAndLoad(
        activity: Activity,
        mode: AutoDownloadMode
    ) {
        return
    }

    @Throws
    private fun assertNonRecursiveCallstack() {
        if (Thread.currentThread().stackTrace.any { it.methodName == "loadPlugin" }) {
            throw Error("You tried to call a function that will recursively call loadPlugin, this will cause crashes or memory leaks. Do not do this, there is better ways to implement the feature than reloading plugins. Are you sure you read the compile error or docs?")
        }
    }

    /**
     * Use updateAllOnlinePluginsAndLoadThem
     *
     * DO NOT USE THIS IN A PLUGIN! It may case an infinite recursive loop lagging or crashing everyone's devices.
     * If you use it from a plugin, do not expect a stable jvmName, SO DO NOT USE IT!
     */
    @Suppress("FunctionName")
    @InternalAPI
    @Throws
    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(context: Context) {
        val onlinePlugins = getPluginsOnline()
        Log.i(TAG, "loadAllOnlinePlugins: loading ${onlinePlugins.size} online plugins")
        for (pluginData in onlinePlugins) {
            val file = File(pluginData.filePath)
            if (file.exists() && file.length() > 0) {
                if (!plugins.containsKey(file.absolutePath)) {
                    loadPlugin(context, file, pluginData)
                }
            } else {
                Log.w(TAG, "Online plugin file missing: ${pluginData.filePath}")
            }
        }
        loadedOnlinePlugins = true
    }

    /**
     * Reloads all local plugins and forces a page update, used for hot reloading with deployWithAdb
     *
     * DO NOT USE THIS IN A PLUGIN! It may case an infinite recursive loop lagging or crashing everyone's devices.
     * If you use it from a plugin, do not expect a stable jvmName, SO DO NOT USE IT!
     */
    @Suppress("FunctionName")
    @InternalAPI
    @Throws
    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_hotReloadAllLocalPlugins(activity: FragmentActivity?) {
        return
    }

    /**
     * @param forceReload see afterPluginsLoadedEvent, basically a way to load all local plugins
     * and reload all pages even if they are previously valid
     *
     * DO NOT USE THIS IN A PLUGIN! It may case an infinite recursive loop lagging or crashing everyone's devices.
     * If you use it from a plugin, do not expect a stable jvmName, SO DO NOT USE IT!
     */
    @Suppress("FunctionName")
    @InternalAPI
    @Throws
    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context: Context, forceReload: Boolean) {
        loadedLocalPlugins = true
        return
    }

    /** @return true if safe mode is enabled in any possible way. */
    fun isSafeMode(): Boolean {
        return checkSafeModeFile() || lastError != null
    }

    /**
     * This can be used to override any extension loading to fix crashes!
     * @return true if safe mode file is present
     **/
    fun checkSafeModeFile(): Boolean {
        return safe {
            val folder = File(CLOUD_STREAM_FOLDER)
            if (!folder.exists()) return@safe false
            val files = folder.listFiles { _, name ->
                name.equals("safe", ignoreCase = true)
            }
            files?.any()
        } ?: false
    }

    suspend fun loadBundledPlugins(context: Context) {
        try {
            val pluginsDir = File(context.filesDir, ONLINE_PLUGINS_FOLDER)
            if (!pluginsDir.exists()) pluginsDir.mkdirs()
            val bundledDir = File(pluginsDir, "bundled")
            if (!bundledDir.exists()) bundledDir.mkdirs()

            val candidates = mutableListOf<String>()
            try {
                context.assets.list("plugins")?.forEach { name ->
                    if (name.endsWith(".cs3", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)) {
                        candidates.add("plugins/$name")
                    }
                }
            } catch (_: Exception) {}

            try {
                context.assets.list("")?.forEach { name ->
                    if (name.endsWith(".cs3", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)) {
                        candidates.add(name)
                    }
                }
            } catch (_: Exception) {}

            // Deduplicate candidates by their file name (e.g. plugins/CNC Verse.cs3 vs CNC Verse.cs3)
            val uniqueCandidates = candidates.distinctBy { File(it).name }

            val prefs = context.getSharedPreferences("bundled_plugins_sync", Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt("version_code", -1)
            val currentVersion = com.lagradost.cloudstream3.BuildConfig.VERSION_CODE
            val isNewVersion = currentVersion != lastVersion
            val isTv = com.lagradost.cloudstream3.ui.settings.Globals.isLayout(com.lagradost.cloudstream3.ui.settings.Globals.TV or com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR) ||
                       com.lagradost.cloudstream4.compose.DeviceLayout.isAutoTv(context)

            // Clean up removed legacy individual plugin files
            val legacyFiles = listOf("CNC Verse.cs3", "CNC Verse Mobile.cs3", "CastleTvProvider.cs3", "HDOProvider.cs3", "StreamFlixProvider.cs3")
            for (legacy in legacyFiles) {
                try { File(bundledDir, legacy).delete() } catch (_: Exception) {}
                try { File(pluginsDir, legacy).delete() } catch (_: Exception) {}
            }

            for (assetPath in uniqueCandidates) {
                val fileName = File(assetPath).name
                if (isTv && fileName.contains("Mobile", ignoreCase = true)) {
                    continue
                }
                if (!isTv && fileName.equals("CNC Verse.cs3", ignoreCase = true)) {
                    continue
                }
                val outFile = File(bundledDir, fileName)

                val shouldCopy = isNewVersion || !outFile.exists() || outFile.length() == 0L

                if (shouldCopy) {
                    try {
                        val tempFile = File(bundledDir, "${fileName}.tmp")
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                        context.assets.open(assetPath).use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            if (outFile.exists()) {
                                outFile.setWritable(true)
                                outFile.delete()
                            }
                            tempFile.renameTo(outFile)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy bundled plugin $assetPath", e)
                    }
                }

                val internalName = outFile.nameWithoutExtension
                val onlinePlugins = getPluginsOnline()
                val existingOnline = onlinePlugins.firstOrNull { it.internalName.equals(internalName, ignoreCase = true) }

                // If an online version was already downloaded in Extensions folder and exists, load that instead of the bundled fallback
                if (existingOnline != null &&
                    !existingOnline.filePath.contains("/bundled/") &&
                    File(existingOnline.filePath).exists() &&
                    File(existingOnline.filePath).length() > 0
                ) {
                    Log.i(TAG, "Bundled plugin $internalName skipped; user/online downloaded plugin found: ${existingOnline.filePath} (v${existingOnline.version})")
                    loadPlugin(context, File(existingOnline.filePath), existingOnline)
                    continue
                }

                if (outFile.exists() && outFile.length() > 0) {
                    val pluginData = PluginData(
                        internalName = internalName,
                        url = "https://raw.githubusercontent.com/jaatayushh/ayushflix/main/Ayushflix.cs3",
                        isOnline = true,
                        filePath = outFile.absolutePath,
                        version = 1
                    )
                    loadPlugin(context, outFile, pluginData)
                }
            }

            prefs.edit().putInt("version_code", currentVersion).apply()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load bundled plugins", t)
        }
    }

    /**
     * @return True if successful, false if not
     * */
    suspend fun loadPlugin(context: Context, file: File, data: PluginData): Boolean {
        val fileName = file.nameWithoutExtension
        val filePath = file.absolutePath
        currentlyLoading = fileName
        Log.i(TAG, "Loading plugin: $data")

        return try {
            // In case of Android 14+ then
            try {
                // Set the file as read-only and log if it fails
                if (!file.setReadOnly()) {
                    Log.e(TAG, "Failed to set read-only on plugin file: ${file.name}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to set dex as read-only")
                logError(t)
            }

            val loader = PathClassLoader(filePath, context.classLoader)
            var manifest: BasePlugin.Manifest
            loader.getResourceAsStream("manifest.json").use { stream ->
                if (stream == null) {
                    Log.e(TAG, "Failed to load plugin $fileName: No manifest found")
                    return false
                }
                InputStreamReader(stream).use { reader ->
                    manifest = parseJson<BasePlugin.Manifest>(reader.readText())
                }
            }

            val name: String = manifest.name ?: "NO NAME".also {
                Log.d(TAG, "No manifest name for ${data.internalName}")
            }
            val version: Int = manifest.version ?: PLUGIN_VERSION_NOT_SET.also {
                Log.d(TAG, "No manifest version for ${data.internalName}")
            }

            @Suppress("UNCHECKED_CAST")
            val pluginClass: Class<*> =
                loader.loadClass(manifest.pluginClassName) as Class<out BasePlugin?>
            val pluginInstance: BasePlugin =
                pluginClass.getDeclaredConstructor().newInstance() as BasePlugin

            // Sets with the proper version
            setPluginData(data.copy(version = version))

            if (plugins.containsKey(filePath)) {
                Log.i(TAG, "Plugin with name $name already exists")
                return true
            }

            pluginInstance.filename = file.absolutePath
            if (manifest.requiresResources) {
                Log.d(TAG, "Loading resources for ${data.internalName}")
                // based on https://stackoverflow.com/questions/7483568/dynamic-resource-loading-from-other-apk
                val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                val addAssetPath =
                    AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                addAssetPath.invoke(assets, file.absolutePath)

                @Suppress("DEPRECATION")
                (pluginInstance as? Plugin)?.resources = Resources(
                    assets,
                    context.resources.displayMetrics,
                    context.resources.configuration
                )
            }
            synchronized(plugins) {
                plugins[filePath] = pluginInstance
            }
            synchronized(classLoaders) {
                classLoaders[loader] = pluginInstance
            }
            synchronized(urlPlugins) {
                urlPlugins[data.url ?: filePath] = pluginInstance
            }
            if (pluginInstance is Plugin) {
                pluginInstance.load(context)
            } else {
                pluginInstance.load()
            }
            Log.i(TAG, "Loaded plugin ${data.internalName} successfully")
            currentlyLoading = null
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load $file: ${Log.getStackTraceString(e)}")
            showToast(
                // context.getActivity(), // we are not always on the main thread
                context.getString(R.string.plugin_load_fail).format(fileName),
                Toast.LENGTH_LONG
            )
            currentlyLoading = null
            false
        }
    }

    fun unloadPlugin(absolutePath: String) {
        Log.i(TAG, "Unloading plugin: $absolutePath")
        val plugin = synchronized(plugins) {
            plugins[absolutePath] ?: plugins.values.firstOrNull { it.filename == absolutePath }
        }
        if (plugin == null) {
            Log.w(TAG, "Couldn't find plugin $absolutePath")
            return
        }

        try {
            plugin.beforeUnload()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to run beforeUnload $absolutePath: ${Log.getStackTraceString(e)}")
        }

        // remove all registered apis
        APIHolder.apis.filter { api -> api.sourcePlugin == plugin.filename }.forEach {
            removePluginMapping(it)
        }

        APIHolder.allProviders.withLock {
            APIHolder.allProviders.removeAll { provider -> provider.sourcePlugin == plugin.filename }
        }

        extractorApis.withLock {
            extractorApis.removeAll { provider -> provider.sourcePlugin == plugin.filename }
        }

        VideoClickActionHolder.allVideoClickActions.withLock {
            VideoClickActionHolder.allVideoClickActions.removeAll { action -> action.sourcePlugin == plugin.filename }
        }

        synchronized(classLoaders) {
            classLoaders.values.removeIf { v -> v == plugin }
        }

        synchronized(plugins) {
            plugins.remove(plugin.filename)
            plugins.remove(absolutePath)
        }

        synchronized(urlPlugins) {
            urlPlugins.values.removeIf { v -> v == plugin }
        }
    }

    /**
     * Spits out a unique and safe filename based on name.
     * Used for repo folders (using repo url) and plugin file names (using internalName)
     * */
    fun getPluginSanitizedFileName(name: String): String {
        return sanitizeFilename(
            name,
            true
        ) + "." + name.hashCode()
    }

    /**
     * This should not be changed as it is used to also detect if a plugin is installed!
     **/
    fun getPluginPath(
        context: Context,
        internalName: String,
        repositoryUrl: String
    ): File {
        val folderName = getPluginSanitizedFileName(repositoryUrl) // Guaranteed unique
        val fileName = getPluginSanitizedFileName(internalName)
        return File("${context.filesDir}/${ONLINE_PLUGINS_FOLDER}/${folderName}/$fileName.cs3")
    }

    suspend fun downloadPlugin(
        activity: Activity,
        pluginUrl: String,
        pluginHash: String?,
        internalName: String,
        repositoryUrl: String,
        loadPlugin: Boolean
    ): Boolean {
        val file = getPluginPath(activity, internalName, repositoryUrl)
        return downloadPlugin(activity, pluginUrl, pluginHash, internalName, file, loadPlugin)
    }

    suspend fun downloadPlugin(
        activity: Activity,
        pluginUrl: String,
        pluginHash: String?,
        internalName: String,
        file: File,
        loadPlugin: Boolean,
    ): Boolean {
        try {
            Log.d(TAG, "Downloading plugin: $pluginUrl to ${file.absolutePath}")
            // The plugin file needs to be salted with the repository url hash as to allow multiple repositories with the same internal plugin names
            val newFile = downloadPluginToFile(activity, pluginUrl, file, pluginHash) ?: return false

            val data = PluginData(
                internalName,
                pluginUrl,
                true,
                newFile.absolutePath,
                PLUGIN_VERSION_NOT_SET
            )

            return if (loadPlugin) {
                unloadPlugin(file.absolutePath)
                unloadPluginByName(internalName)
                loadPlugin(
                    activity,
                    newFile,
                    data
                )
            } else {
                setPluginData(data)
                true
            }
        } catch (e: Exception) {
            logError(e)
            return false
        }
    }

    suspend fun deletePlugin(file: File): Boolean {
        val list =
            (getPluginsLocal() + getPluginsOnline()).filter { it.filePath == file.absolutePath }

        return try {
            if (File(file.absolutePath).delete()) {
                unloadPlugin(file.absolutePath)
                list.forEach { deletePluginData(it) }
                return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * DO NOT USE THIS IN A PLUGIN! It may case an infinite recursive loop lagging or crashing everyone's devices.
     * If you use it from a plugin, do not expect a stable jvmName, SO DO NOT USE IT!
     */
    @Suppress("FunctionName")
    @InternalAPI
    @Throws
    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_manuallyReloadAndUpdatePlugins(activity: Activity) {
        assertNonRecursiveCallstack()

        showToast(activity.getString(R.string.starting_plugin_update_manually), Toast.LENGTH_LONG)

        ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(activity)
        afterPluginsLoadedEvent.invoke(false)

        val urls = (getKey<Array<RepositoryData>>(REPOSITORIES_KEY)
            ?: emptyArray()) + PREBUILT_REPOSITORIES
        val onlinePlugins = urls.toList().amap {
            getRepoPlugins(it) ?: emptyList()
        }.flatten().distinctBy { it.plugin.url }

        val allPlugins = getPluginsOnline().flatMap { savedData ->
            onlinePlugins
                .filter { it.plugin.internalName.equals(savedData.internalName, ignoreCase = true) }
                .mapNotNull { onlineData ->
                    OnlinePluginData(savedData, onlineData).takeIf { it.validOnlineData(activity) }
                }
        }.distinctBy { it.onlineData.plugin.url }

        val updatedPlugins = mutableListOf<String>()

        allPlugins.amap { pluginData ->
            if (pluginData.isDisabled) {
                Log.e(
                    "PluginManager",
                    "Unloading disabled plugin: ${pluginData.onlineData.plugin.name}"
                )
                unloadPlugin(pluginData.savedData.filePath)
                unloadPluginByName(pluginData.savedData.internalName)
            } else {
                val targetFile = getPluginPath(
                    activity,
                    pluginData.savedData.internalName,
                    pluginData.onlineData.repositoryData.url
                )
                unloadPlugin(pluginData.savedData.filePath)
                unloadPluginByName(pluginData.savedData.internalName)

                val existingFile = File(pluginData.savedData.filePath)
                if (existingFile.exists() && existingFile.absolutePath != targetFile.absolutePath) {
                    existingFile.delete()
                }

                if (downloadPlugin(
                        activity,
                        pluginData.onlineData.plugin.url,
                        pluginData.onlineData.plugin.fileHash,
                        pluginData.savedData.internalName,
                        targetFile,
                        true
                    )
                ) {
                    updatedPlugins.add(pluginData.onlineData.plugin.name)
                }
            }
        }.also {
            main {
                val message = if (updatedPlugins.isNotEmpty()) {
                    activity.getString(R.string.plugins_updated_manually, updatedPlugins.size)
                } else {
                    activity.getString(R.string.no_plugins_updated_manually)
                }
                showToast(message, Toast.LENGTH_LONG)

                val notificationText = UiText.StringResource(
                    R.string.plugins_updated_manually,
                    listOf(updatedPlugins.size)
                )
                createNotification(activity, notificationText, updatedPlugins)

                if (updatedPlugins.isNotEmpty()) {
                    APIHolder.initAll()
                }
            }
        }

        loadedOnlinePlugins = true
        afterPluginsLoadedEvent.invoke(false)

        Log.i("PluginManager", "Plugin update done!")
    }

    private fun Context.createNotificationChannel() {
        hasCreatedNotChanel = true
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = EXTENSIONS_CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText =
                EXTENSIONS_CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(EXTENSIONS_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        context: Context,
        uitext: UiText,
        extensions: List<String>
    ): Notification? {
        try {

            if (extensions.isEmpty()) return null

            val content = extensions.joinToString(", ")
//          main { // DON'T WANT TO SLOW IT DOWN
            val builder = NotificationCompat.Builder(context, EXTENSIONS_CHANNEL_ID)
                .setAutoCancel(false)
                .setColorized(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setColor(context.colorFromAttribute(R.attr.colorPrimary))
                .setContentTitle(uitext.asString(context))
                //.setContentTitle(context.getString(title, extensionNames.size))
                .setSmallIcon(R.drawable.ic_baseline_extension_24)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(content)
                )
                .setContentText(content)

            if (!hasCreatedNotChanel) {
                context.createNotificationChannel()
            }

            val notification = builder.build()
            // notificationId is a unique int for each notification that you must define
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(context)
                    .notify((System.currentTimeMillis() / 1000).toInt(), notification)
            }
            return notification
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }
}
