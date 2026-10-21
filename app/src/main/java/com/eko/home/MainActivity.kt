package com.eko.home

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.util.concurrent.Executors

/** Foreground-only, framework Views launcher. No services, receivers, network or wake locks. */
class MainActivity : Activity() {
    private data class App(val name: String, val component: ComponentName)
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("eko", MODE_PRIVATE) }
    private val white = Color.rgb(243, 245, 249)
    private val muted = Color.rgb(159, 172, 191)
    private lateinit var appArea: LinearLayout
    private lateinit var status: TextView
    private val consoleViews = mutableListOf<Button>()
    private var apps = emptyList<App>()
    private var generation = 0
    private var resumeFocus: String? = null
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        resumeFocus = savedInstanceState?.getString("focus")
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.rgb(10, 17, 27)) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(42), dp(24), dp(42), dp(24)) }
        scroll.addView(root)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("EKO", 28, white).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(TextClock(this).apply { format12Hour = "h:mm a"; format24Hour = "HH:mm"; textSize = 18f; setTextColor(muted); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES })
        root.addView(header)
        root.addView(label("Your space. Just play.", 14, muted))
        section(root, "CONSOLES")
        val consoles = LinearLayout(this)
        listOf("PS5", "Xbox").forEachIndexed { i, name ->
            val button = tile(name + "  ·  Choose HDMI", "console" + i, 24f) { openConsole(i, name) }
            button.setOnLongClickListener { chooseInput(i, name); true }
            consoleViews.add(button)
            addWeighted(consoles, button, 90)
        }
        root.addView(consoles)
        root.addView(label("Hold OK on a console to change its input", 12, muted))
        section(root, "STREAMING & APPS")
        appArea = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appArea)
        status = label("Loading installed apps…", 13, muted)
        root.addView(status)
        val actions = LinearLayout(this).apply { setPadding(0, dp(14), 0, 0) }
        addWeighted(actions, tile("All apps", "all") { showApps() }, 48)
        addWeighted(actions, tile("Inputs", "inputs") { inputHelp() }, 48)
        addWeighted(actions, tile("Settings", "settings") { showSettings() }, 48)
        root.addView(actions)
        setContentView(scroll)
        consoleViews.first().requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateConsoleLabels()
        loadApps()
    }
    override fun onPause() {
        resumeFocus = currentFocus?.tag as? String ?: resumeFocus
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("focus", currentFocus?.tag as? String ?: resumeFocus)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { generation++; worker.shutdownNow(); super.onDestroy() }
    @Deprecated("Launcher stays at its root on Back")
    override fun onBackPressed() { consoleViews.first().requestFocus() }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Int, color: Int) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); setPadding(dp(4), dp(3), dp(4), dp(3)) }
    private fun section(parent: LinearLayout, title: String) { parent.addView(label(title, 12, muted).apply { setPadding(dp(4), dp(18), 0, dp(8)); letterSpacing = .14f }) }
    private fun surface(focused: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(if (focused) Color.rgb(231, 238, 248) else Color.rgb(27, 39, 55))
        setStroke(dp(if (focused) 2 else 1), if (focused) Color.WHITE else Color.rgb(47, 61, 79))
    }
    private fun tile(title: String, key: String, size: Float = 15f, action: () -> Unit) = Button(this).apply {
        id = View.generateViewId(); tag = key; text = title; textSize = size; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER; setPadding(dp(12), dp(5), dp(12), dp(5))
        minHeight = 0; minimumHeight = 0; maxLines = 2
        isFocusable = true; isFocusableInTouchMode = true
        background = surface(false); setTextColor(white)
        setOnFocusChangeListener { v, focused ->
            v.background = surface(focused)
            (v as Button).setTextColor(if (focused) Color.rgb(17, 28, 43) else white)
            if (focused) v.parent.requestChildFocus(v, v)
        }
        setOnClickListener { action() }
    }
    private fun addWeighted(row: LinearLayout, view: View, height: Int) {
        row.addView(view, LinearLayout.LayoutParams(0, dp(height), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
    }
    private fun launch(intent: Intent, failure: String = "This screen is unavailable on this TV."): Boolean = try {
        startActivity(intent); true
    } catch (_: android.content.ActivityNotFoundException) { notice(failure); false
    } catch (_: SecurityException) { notice("The TV firmware blocked this action. " + failure); false }
    private fun notice(message: String) { AlertDialog.Builder(this).setTitle("EKO Home").setMessage(message).setPositiveButton("OK", null).show() }

    private fun loadApps() {
        if (loading) return
        loading = true
        val ticket = ++generation
        worker.execute {
            val found = try {
                val intents = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                intents.flatMap { category -> packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }
                    .filter { it.activityInfo.packageName != packageName && it.activityInfo.exported }
                    .map { App(it.loadLabel(packageManager).toString(), ComponentName(it.activityInfo.packageName, it.activityInfo.name)) }
                    .distinctBy { it.component.packageName }
                    .sortedWith(compareBy<App> { LauncherPolicy.rank(it.component.packageName) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            } catch (_: RuntimeException) { emptyList() }
            runOnUiThread {
                if (!isDestroyed && ticket == generation) {
                    loading = false
                    apps = found
                    renderApps()
                }
            }
        }
    }
    private fun renderApps() {
        val focusKey = currentFocus?.tag as? String ?: resumeFocus
        appArea.removeAllViews()
        apps.take(8).chunked(4).forEach { group ->
            val row = LinearLayout(this)
            group.forEach { app -> addWeighted(row, tile(app.name, app.component.flattenToString()) { openApp(app) }, 66) }
            repeat(4 - group.size) { addWeighted(row, View(this), 66) }
            appArea.addView(row)
        }
        status.text = if (apps.isEmpty()) "No launchable apps found. Try TV settings." else "Offline by design  ·  No ads  ·  " + apps.size + " apps"
        val wanted = window.decorView.findViewWithTag<View>(focusKey)
        (wanted ?: consoleViews.first()).requestFocus()
    }
    private fun openApp(app: App) { launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "The app may have been removed. Return Home to refresh.") }
    private fun showApps() {
        if (apps.isEmpty()) { notice("No launchable apps found. Install apps using your TV's existing store."); return }
        AlertDialog.Builder(this).setTitle("All apps").setItems(apps.map { it.name }.toTypedArray()) { _, i -> openApp(apps[i]) }.setNegativeButton("Close", null).show()
    }

    private fun hdmiInputs(): List<TvInputInfo> = try {
        getSystemService(TvInputManager::class.java)?.tvInputList?.filter { it.type == TvInputInfo.TYPE_HDMI } ?: emptyList()
    } catch (_: RuntimeException) { emptyList() }
    private fun updateConsoleLabels() {
        val ids = hdmiInputs().map { it.id }
        listOf("PS5", "Xbox").forEachIndexed { index, name ->
            val mapped = LauncherPolicy.resolveInput(prefs.getString("input" + index, null), ids) != null
            consoleViews[index].text = name + if (mapped) "  ·  HDMI" else "  ·  Choose HDMI"
        }
    }
    private fun openConsole(index: Int, name: String) {
        val id = LauncherPolicy.resolveInput(prefs.getString("input" + index, null), hdmiInputs().map { it.id })
        if (id == null) chooseInput(index, name) else switchInput(id)
    }
    private fun chooseInput(index: Int, name: String) {
        val inputs = hdmiInputs()
        if (inputs.isEmpty()) { inputHelp(); return }
        val labels = inputs.map { input ->
            val custom = input.loadCustomLabel(this)?.toString()
            (custom?.takeIf { it.isNotBlank() } ?: input.loadLabel(this).toString()) + "\n" + input.id
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Map " + name + " to an HDMI input")
            .setItems(labels) { _, chosen ->
                prefs.edit().putString("input" + index, inputs[chosen].id).apply()
                updateConsoleLabels()
                AlertDialog.Builder(this).setTitle("Input saved")
                    .setMessage("Opening this input depends on your firmware's TV app. If it does not switch, use the remote's Source/Input button.")
                    .setPositiveButton("Try input") { _, _ -> switchInput(inputs[chosen].id) }
                    .setNegativeButton("Later", null).show()
            }.setNeutralButton("Clear mapping") { _, _ -> prefs.edit().remove("input" + index).apply(); updateConsoleLabels() }
            .setNegativeButton("Cancel", null).show()
    }
    private fun switchInput(id: String) {
        // Public passthrough request to OEM TV app, not privileged HDMI/CEC control.
        launch(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Use the remote's Source/Input button. Direct HDMI switching is not supported by this firmware.")
    }
    private fun inputHelp() { notice("Use the remote's Source/Input button to select HDMI. EKO Home can request an input only if the TV exposes HDMI inputs and a compatible TV activity. Console power, CEC and automatic detection are not controlled. Hold OK on PS5/Xbox to map or clear an input.") }
    private fun showSettings() {
        val choices = arrayOf("TV settings", "Home app settings (if supported)", "Open stock / other Home", "About & verification")
        AlertDialog.Builder(this).setTitle("Settings").setItems(choices) { _, selected ->
            when (selected) {
                0 -> launch(Intent(Settings.ACTION_SETTINGS))
                1 -> launch(Intent(Settings.ACTION_HOME_SETTINGS), "Home selection is firmware-dependent. Keep using EKO Home as an ordinary TV app if unavailable.")
                2 -> otherHomes()
                3 -> notice("EKO Home 0.1.0 · Android 11+\nNative Kotlin / Android Views. No internet permission, ads, analytics, services or boot tasks.\nHDMI switching and default Home replacement require testing on the EKO K550USGQ. A successful activity launch does not confirm the physical input changed. Stock firmware and launcher remain installed.")
            }
        }.setNegativeButton("Close", null).show()
    }
    private fun otherHomes() {
        val homes = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName != packageName && it.activityInfo.exported }
        if (homes.isEmpty()) { notice("No other Home activity is visible. Use TV settings or your original remote Home behavior. EKO Home has not disabled any package."); return }
        AlertDialog.Builder(this).setTitle("Open another Home (does not change default)")
            .setItems(homes.map { it.loadLabel(packageManager).toString() }.toTypedArray()) { _, i ->
                val info = homes[i].activityInfo
                launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(ComponentName(info.packageName, info.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.setNegativeButton("Cancel", null).show()
    }
}
