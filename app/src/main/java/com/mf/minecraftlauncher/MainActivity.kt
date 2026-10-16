package com.mf.minecraftlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.zip.ZipFile

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences("droid_launcher", MODE_PRIVATE) }

    private lateinit var versionSpinner: Spinner
    private lateinit var versionStatus: TextView
    private lateinit var ramLabel: TextView
    private lateinit var modList: LinearLayout
    private lateinit var serverList: LinearLayout
    private lateinit var rootContent: LinearLayout

    private var versions: List<MinecraftVersion> = emptyList()
    private var updatingVersionList = false
    private val modsDirectory: File by lazy { File(filesDir, "mods-staging").apply { mkdirs() } }
    private val importModRequestCode = 812
    private val maxModBytes = 64L * 1024L * 1024L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 23, 18)
        window.navigationBarColor = Color.rgb(16, 23, 18)
        setContentView(createUi())
        refreshServers()
        refreshMods()
        loadVersionManifest()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun createUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 23, 18))
            isFillViewport = true
        }
        rootContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(30))
        }
        scroll.addView(rootContent)

        val hero = TextView(this).apply {
            text = "DROID LAUNCHER"
            setTextColor(Color.rgb(183, 247, 133))
            textSize = 28f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            letterSpacing = 0.04f
        }
        rootContent.addView(hero)
        rootContent.addView(textView("A clean, phone-first Minecraft Java launcher prototype.", 14f, Color.rgb(198, 211, 201)).apply {
            setPadding(0, dp(6), 0, dp(16))
        })
        rootContent.addView(textView("This first build manages setup data; it does not launch the game yet.", 12f, Color.rgb(255, 210, 125)).apply {
            setPadding(0, 0, 0, dp(14))
        })

        val versionCard = section("Minecraft versions", "Fetches the official Mojang version manifest. Selecting a version saves a preference only; it does not install game files.")
        versionSpinner = Spinner(this)
        versionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Loading version catalog…"))
        versionCard.addView(versionSpinner, matchWrap())
        versionStatus = textView("Checking online catalog…", 12f, Color.LTGRAY)
        versionCard.addView(versionStatus)
        versionSpinner.onItemSelectedListener = SimpleItemSelectedListener { position ->
            if (!updatingVersionList && position in versions.indices) {
                preferences.edit().putString("selected_version", versions[position].id).apply()
                versionStatus.text = "Selected ${versions[position].id}. Not installed yet."
            }
        }
        versionCard.addView(actionButton("Refresh version catalog") { loadVersionManifest() })
        rootContent.addView(versionCard)

        val ramCard = section("Memory settings", "Saved locally for future engine integration. This value is not passed to Minecraft yet.")
        val ramSeek = SeekBar(this).apply { max = 8 }
        val savedRam = LauncherLogic.normalizeRamMb(preferences.getInt("ram_mb", 768))
        ramSeek.progress = (savedRam - LauncherLogic.MIN_RAM_MB) / LauncherLogic.RAM_STEP_MB
        ramLabel = textView("$savedRam MB", 16f, Color.WHITE)
        ramCard.addView(ramLabel)
        ramCard.addView(ramSeek, matchWrap())
        ramSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = LauncherLogic.normalizeRamMb(LauncherLogic.MIN_RAM_MB + progress * LauncherLogic.RAM_STEP_MB)
                ramLabel.text = "$value MB (saved setting only)"
                if (fromUser) preferences.edit().putInt("ram_mb", value).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        ramCard.addView(textView("On a phone with 2 GB RAM or less, start conservatively; Android and the launcher also need memory.", 12f, Color.rgb(198, 211, 201)))
        rootContent.addView(ramCard)

        val accountsCard = section("Account", "Secure Microsoft sign-in is not implemented in this starter. Never enter your Microsoft password into a custom form.")
        accountsCard.addView(actionButton("Account setup status") {
            AlertDialog.Builder(this)
                .setTitle("Microsoft account")
                .setMessage("Not implemented yet. A real account feature must use Microsoft's official OAuth authorization-code flow with PKCE, validate Minecraft ownership, and keep tokens out of logs. This app does not ask for or store credentials.")
                .setPositiveButton("OK", null)
                .show()
        })
        rootContent.addView(accountsCard)

        val skinsCard = section("Skin viewer", "Opens a third-party skin image page by username. It is a preview utility, not proof that you are signed in or own the account.")
        skinsCard.addView(actionButton("Preview a player's skin") { promptSkinUsername() })
        skinsCard.addView(textView("Skin preview service: mc-heads.net", 12f, Color.LTGRAY))
        rootContent.addView(skinsCard)

        val modsCard = section("Local mods (staging)", "Imports .jar files into app-private staging after checking ZIP/JAR structure. It does not install mods into Minecraft or scan for malware.")
        modsCard.addView(actionButton("Import mod JAR") { chooseModFile() })
        modsCard.addView(actionButton("Refresh staged mods") { refreshMods() })
        modList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        modsCard.addView(modList)
        rootContent.addView(modsCard)

        val serversCard = section("Servers", "Store server addresses on this phone. Status ping and joining require a working game/network engine and are not implemented here.")
        serversCard.addView(actionButton("Add server") { promptAddServer() })
        serverList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        serversCard.addView(serverList)
        rootContent.addView(serversCard)

        val playCard = section("Play", "The clean-room prototype has no Java runtime, renderer, or game-process integration yet.")
        playCard.addView(actionButton("PLAY — engine not connected") {
            AlertDialog.Builder(this)
                .setTitle("Game engine not connected")
                .setMessage("This app was built from a clean Android project. It currently manages version metadata, RAM preferences, staged mod archives, server bookmarks, and skin previews, but cannot start Minecraft until a compatible Java-on-Android runtime, native graphics/input libraries, library resolution, assets, and official account authentication are integrated and tested.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Read engine project") { _, _ -> openUrl("https://github.com/AngelAuraMC/Amethyst-Android") }
                .show()
        }.apply {
            textSize = 16f
            setTextColor(Color.rgb(16, 23, 18))
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(183, 247, 133)))
        })
        rootContent.addView(playCard)

        rootContent.addView(textView("No Minecraft game files are bundled. The official version list is fetched from Mojang. Prototype source is intended for learning and testing.", 11f, Color.LTGRAY).apply {
            setPadding(dp(2), dp(18), dp(2), 0)
        })
        return scroll
    }

    private fun section(title: String, explanation: String): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(27, 40, 32), dp(16))
        }
        val heading = textView(title, 18f, Color.WHITE).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(5))
        }
        panel.addView(heading)
        panel.addView(textView(explanation, 12f, Color.rgb(199, 211, 202)).apply {
            setPadding(0, 0, 0, dp(10))
        })
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = dp(14)
        panel.layoutParams = params
        return panel
    }

    private fun textView(text: String, size: Float, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.WHITE)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(54, 79, 61)))
        setOnClickListener { action() }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(5)
        params.bottomMargin = dp(5)
        layoutParams = params
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun loadVersionManifest() {
        versionStatus.text = "Fetching official Mojang version manifest…"
        worker.execute {
            var result: List<MinecraftVersion> = emptyList()
            var fromNetwork = false
            var message: String? = null
            try {
                val json = downloadManifest()
                File(filesDir, "version_manifest_v2.json").writeText(json, Charsets.UTF_8)
                result = LauncherLogic.parseVersionManifest(json)
                fromNetwork = true
            } catch (error: Exception) {
                message = error.message ?: error.javaClass.simpleName
                try {
                    val cache = File(filesDir, "version_manifest_v2.json")
                    if (cache.isFile) result = LauncherLogic.parseVersionManifest(cache.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    // The UI reports the network/cache failure below.
                }
            }
            mainHandler.post {
                versions = result
                if (versions.isNotEmpty()) {
                    updatingVersionList = true
                    versionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, versions)
                    val saved = preferences.getString("selected_version", null)
                    val preferred = versions.indexOfFirst { it.id == saved }.takeIf { it >= 0 }
                        ?: versions.indexOfFirst { it.type == "release" }.coerceAtLeast(0)
                    versionSpinner.setSelection(preferred)
                    updatingVersionList = false
                    val source = if (fromNetwork) "online" else "offline cache"
                    versionStatus.text = "${versions.size} versions loaded ($source). Selected versions are metadata only."
                } else {
                    versionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("No version data available"))
                    versionStatus.text = "Could not load version catalog. ${message ?: "No valid cache found."}"
                }
            }
        }
    }

    private fun downloadManifest(): String {
        val url = URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
        require(url.protocol == "https" && url.host == "piston-meta.mojang.com") { "Unexpected version manifest host" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 15000
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "DroidLauncherPrototype/0.1")
        }
        try {
            val status = connection.responseCode
            require(status == HttpURLConnection.HTTP_OK) { "Manifest request returned HTTP $status" }
            val output = StringBuilder()
            connection.inputStream.buffered().use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= 5 * 1024 * 1024) { "Manifest exceeds 5 MiB limit" }
                    output.append(String(buffer, 0, read, Charsets.UTF_8))
                }
            }
            return output.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun chooseModFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/java-archive"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/java-archive", "application/zip", "application/octet-stream", "*/*"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, importModRequestCode)
    }

    @Deprecated("Uses the Android document picker for Android 9 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != importModRequestCode || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        worker.execute {
            val outcome = try {
                stageMod(uri)
                "Mod archive staged privately. It is not installed into a game profile."
            } catch (error: Exception) {
                "Could not import mod: ${error.message ?: error.javaClass.simpleName}"
            }
            mainHandler.post {
                Toast.makeText(this, outcome, Toast.LENGTH_LONG).show()
                refreshMods()
            }
        }
    }

    private fun stageMod(uri: Uri) {
        val displayName = queryDisplayName(uri) ?: "imported-mod.jar"
        require(displayName.lowercase().endsWith(".jar")) { "Choose a .jar mod file" }
        val safeBase = displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_").take(70).ifBlank { "mod" }
        val temporary = File(modsDirectory, "${System.currentTimeMillis()}.part")
        var bytesCopied = 0L
        try {
            val input = contentResolver.openInputStream(uri) ?: error("The selected file could not be opened")
            input.use { source ->
                FileOutputStream(temporary).use { destination ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        bytesCopied += count
                        require(bytesCopied <= maxModBytes) { "Mod exceeds the 64 MiB staging limit" }
                        destination.write(buffer, 0, count)
                    }
                    destination.fd.sync()
                }
            }
            require(bytesCopied > 0L) { "The selected file is empty" }
            validateModJar(temporary)
            val finalFile = File(modsDirectory, "$safeBase-${System.currentTimeMillis()}.jar")
            require(temporary.renameTo(finalFile)) { "Could not safely finalize staged file" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun validateModJar(file: File) {
        ZipFile(file).use { archive ->
            val entries = archive.entries()
            var count = 0
            var hasModMarker = false
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                count++
                require(count <= 20000) { "JAR has too many entries" }
                val name = entry.name
                if (!entry.isDirectory && (name.endsWith(".class") || name == "fabric.mod.json" ||
                            name == "quilt.mod.json" || name == "mcmod.info" ||
                            name == "META-INF/mods.toml" || name == "META-INF/neoforge.mods.toml")) {
                    hasModMarker = true
                }
            }
            require(count > 0 && hasModMarker) { "Archive does not look like a Java mod JAR" }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun refreshMods() {
        if (!::modList.isInitialized) return
        modList.removeAllViews()
        val files = modsDirectory.listFiles()?.filter { it.isFile && it.extension.equals("jar", true) }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) {
            modList.addView(textView("No mod JARs staged.", 12f, Color.LTGRAY).apply { setPadding(0, dp(8), 0, dp(8)) })
            return
        }
        files.forEach { file ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            val name = textView("${file.name}\n${file.length() / 1024} KiB", 12f, Color.WHITE)
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val remove = Button(this).apply {
                text = "Delete"
                isAllCaps = false
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete staged copy?")
                        .setMessage("Only this app's staged copy will be deleted. The original file is unchanged.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            if (file.canonicalFile.parentFile == modsDirectory.canonicalFile && file.delete()) {
                                refreshMods()
                            } else {
                                Toast.makeText(this@MainActivity, "Could not delete staged copy", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .show()
                }
            }
            row.addView(remove)
            modList.addView(row)
        }
    }

    private fun promptAddServer() {
        val input = EditText(this).apply {
            hint = "example.org or play.example.org:25565"
            isSingleLine = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("Add server address")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val address = input.text.toString().trim()
                if (!LauncherLogic.isValidServerAddress(address)) {
                    Toast.makeText(this, "That server address or port looks invalid", Toast.LENGTH_LONG).show()
                } else {
                    val existing = readServers().toMutableList()
                    if (existing.none { it.equals(address, ignoreCase = true) }) existing.add(address)
                    saveServers(existing)
                    refreshServers()
                }
            }
            .show()
    }

    private fun readServers(): List<String> {
        return try {
            val json = JSONArray(preferences.getString("servers_json", "[]"))
            (0 until json.length()).mapNotNull { json.optString(it, null) }
                .filter { LauncherLogic.isValidServerAddress(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveServers(servers: List<String>) {
        val json = JSONArray()
        servers.forEach { json.put(it) }
        preferences.edit().putString("servers_json", json.toString()).apply()
    }

    private fun refreshServers() {
        if (!::serverList.isInitialized) return
        serverList.removeAllViews()
        val servers = readServers()
        if (servers.isEmpty()) {
            serverList.addView(textView("No saved servers yet.", 12f, Color.LTGRAY).apply { setPadding(0, dp(8), 0, dp(8)) })
            return
        }
        servers.forEach { address ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            row.addView(textView(address, 14f, Color.WHITE), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Remove"
                isAllCaps = false
                setOnClickListener {
                    saveServers(readServers().filterNot { it == address })
                    refreshServers()
                }
            })
            serverList.addView(row)
        }
    }

    private fun promptSkinUsername() {
        val input = EditText(this).apply {
            hint = "Minecraft username"
            isSingleLine = true
            setText(preferences.getString("skin_username", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("Preview skin")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open preview") { _, _ ->
                val username = input.text.toString().trim()
                if (!LauncherLogic.isValidMinecraftUsername(username)) {
                    Toast.makeText(this, "Username must be 1–16 letters, numbers, or underscores", Toast.LENGTH_LONG).show()
                } else {
                    preferences.edit().putString("skin_username", username).apply()
                    val encoded = URLEncoder.encode(username, "UTF-8")
                    openUrl("https://mc-heads.net/player/$encoded/300")
                }
            }
            .show()
    }

    private fun openUrl(value: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
        } catch (_: Exception) {
            Toast.makeText(this, "No browser is available", Toast.LENGTH_SHORT).show()
        }
    }
}
