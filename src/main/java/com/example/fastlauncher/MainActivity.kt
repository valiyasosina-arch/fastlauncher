package com.example.fastlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

private class App(val label: String, val pkg: String, val cls: String, val icon: Drawable)

class MainActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor()
    private var apps: List<App> = emptyList()
    private val adapter = Adapter()
    private lateinit var grid: GridView

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = load()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(0xFF101418.toInt()))

        grid = GridView(this).apply {
            numColumns = 6
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(8)
            verticalSpacing = dp(8)
            clipToPadding = false
            setPadding(dp(48), dp(40), dp(48), dp(40))
            selector = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0x33FFFFFF)
                setStroke(dp(3), Color.WHITE)
            }
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> start(apps[pos]) }
            setOnItemLongClickListener { _, _, pos, _ -> showInfo(apps[pos]); true }
        }
        setContentView(grid)
        grid.requestFocus()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(pkgReceiver, filter)
        load()
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        io.shutdown()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        grid.setSelection(0)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)

    private fun load() {
        io.execute {
            val pm = packageManager
            val seen = HashSet<String>()
            val list = ArrayList<App>()
            for (category in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val query = Intent(Intent.ACTION_MAIN).addCategory(category)
                for (r in pm.queryIntentActivities(query, 0)) {
                    val info = r.activityInfo
                    if (info.packageName == packageName || !seen.add(info.packageName)) continue
                    val icon = info.loadBanner(pm)
                        ?: info.applicationInfo.loadBanner(pm)
                        ?: r.loadIcon(pm)
                    list.add(App(r.loadLabel(pm).toString(), info.packageName, info.name, icon))
                }
            }
            list.sortBy { it.label.lowercase() }
            runOnUiThread {
                apps = list
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun start(app: App) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(app.pkg, app.cls)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInfo(app: App) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Настройки приложения недоступны", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(12), dp(8), dp(12))
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(144), dp(81))
                })
                addView(TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
            }
            val app = apps[position]
            (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
            (row.getChildAt(1) as TextView).text = app.label
            return row
        }
    }
}
