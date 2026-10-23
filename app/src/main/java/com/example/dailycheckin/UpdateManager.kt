package com.example.dailycheckin

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在线更新：
 * 1. 打开 App 每天自动静默检查一次；设置页可手动检查
 * 2. 有新版本 → 弹窗 → 系统下载器下载 APK → 下完自动调起安装
 *
 * 服务端：GitHub Releases（仓库每次发版上传 APK，tag 命名 v1.16 之类）
 * 若国内访问慢，可把 REPO 换到 Gitee（API 结构相同）。
 */
object UpdateManager {

    // TODO: 创建好 GitHub 仓库后，把这里改成 "你的用户名/仓库名"，例如 "zhangsan/daily-tasks"
    private const val REPO = "AIEEEMOer/daily-tasks"

    private const val PREF = "settings_pref"
    private const val KEY_LAST_CHECK = "last_update_check"
    private val ui = Handler(Looper.getMainLooper())

    data class ReleaseInfo(val version: String, val apkUrl: String, val notes: String)

    /** 当前安装版本名，如 "1.16" */
    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"

    /** 打开 App 时调用：每天最多静默检查一次，有新版才弹窗 */
    fun autoCheck(activity: Activity) {
        val today = Habit.today()
        val sp = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (sp.getString(KEY_LAST_CHECK, "") == today) return
        sp.edit().putString(KEY_LAST_CHECK, today).apply()
        checkAsync(activity, manual = false)
    }

    /** 手动/自动检查更新入口（自动模式无结果时不打扰） */
    fun checkAsync(activity: Activity, manual: Boolean) {
        if (manual) {
            Toast.makeText(activity, Lang.s("正在检查更新…", "Checking for updates…"), Toast.LENGTH_SHORT).show()
        }
        Thread {
            val info = fetchLatest()
            ui.post {
                when {
                    info == null -> {
                        if (manual) Toast.makeText(
                            activity,
                            Lang.s("检查失败：网络异常或仓库未配置", "Check failed: network error or repo not configured"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    isNewer(info.version, currentVersion(activity)) -> showUpdateDialog(activity, info)
                    manual -> Toast.makeText(
                        activity,
                        Lang.s("已是最新版本", "You're on the latest version"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    /** 拉取 GitHub 最新 Release 信息（在子线程调用） */
    private fun fetchLatest(): ReleaseInfo? {
        return try {
            val api = "https://api.github.com/repos/$REPO/releases/latest"
            val conn = URL(api).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "DailyTasks-App")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val o = JSONObject(body)
            val version = o.optString("tag_name").removePrefix("v")
            val notes = o.optString("body")
            val assets = o.optJSONArray("assets") ?: return null
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
            if (apkUrl.isEmpty()) null else ReleaseInfo(version, apkUrl, notes)
        } catch (e: Exception) {
            null
        }
    }

    /** 版本号比较：latest 是否比 current 新（按点分段数值比较，支持 1.14.1） */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 发现新版本：弹窗提示下载 */
    private fun showUpdateDialog(activity: Activity, info: ReleaseInfo) {
        val msg = buildString {
            if (info.notes.isNotBlank()) append(info.notes.trim()).append("\n\n")
            append(Lang.s("当前版本 v", "Current: v")).append(currentVersion(activity))
            append(Lang.s(" → 最新版本 v", " → Latest: v")).append(info.version)
        }
        AlertDialog.Builder(activity)
            .setTitle(Lang.s("🎉 发现新版本 v${info.version}", "🎉 New version v${info.version}"))
            .setMessage(msg)
            .setPositiveButton(Lang.s("立即下载", "Download now")) { _, _ ->
                download(activity, info.apkUrl)
            }
            .setNegativeButton(Lang.s("暂不", "Later"), null)
            .show()
    }

    /** 系统下载器下载 APK 到应用私有目录，完成后自动安装 */
    private fun download(activity: Activity, url: String) {
        val fileName = "daily-tasks-update.apk"
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val old = File(dir, fileName)
        // 已下载过的更新包直接安装，不重复下载
        if (old.exists() && old.length() > 0) {
            Toast.makeText(activity, Lang.s("更新包已下载，直接安装", "Update already downloaded, installing"), Toast.LENGTH_SHORT).show()
            installApk(activity, old)
            return
        }

        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(Lang.s("每日任务 更新包", "Daily Tasks update"))
            .setDescription(Lang.s("正在下载最新版本…", "Downloading latest version…"))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(req)
        Toast.makeText(activity, Lang.s("开始下载更新包…", "Download started…"), Toast.LENGTH_SHORT).show()

        // —— 实时进度弹窗：进度条 + 百分比 + 已下载/总大小 ——
        val dp = activity.resources.displayMetrics.density
        val bar = android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            // 进度条染主题色
            progressTintList = android.content.res.ColorStateList.valueOf(Theme.primary)
        }
        val tvPct = android.widget.TextView(activity).apply {
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF5A6B62.toInt())
            setPadding(0, (10 * dp).toInt(), 0, 0)
        }
        val box = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = (24 * dp).toInt()
            setPadding(p, (8 * dp).toInt(), p, 0)
            addView(bar, android.widget.LinearLayout.LayoutParams(-1, -2))
            addView(tvPct)
        }
        val dlg = AlertDialog.Builder(activity)
            .setTitle(Lang.s("📥 正在下载更新", "📥 Downloading update"))
            .setView(box)
            .setNegativeButton(Lang.s("后台下载", "Background")) { d, _ -> d.dismiss() }
            .create()
        dlg.setCanceledOnTouchOutside(false)
        dlg.show()
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Theme.dark)

        // 完成/失败只处理一次的标志（轮询与广播可能同时触发）
        var finished = false

        // 监听下载完成 → 调起安装
        // 注意：该广播由系统下载管理器（另一个应用）发出，必须用 RECEIVER_EXPORTED，
        // 否则 Android 13+ 收不到回调，下载完成后不会自动调起安装
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId && !finished) {
                    finished = true
                    try {
                        activity.unregisterReceiver(this)
                    } catch (_: Exception) {
                    }
                    if (dlg.isShowing) dlg.dismiss()
                    Toast.makeText(activity, Lang.s("下载完成，正在调起安装…", "Download complete, launching installer…"), Toast.LENGTH_SHORT).show()
                    installApk(activity, File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName))
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, filter)
        }

        // 轮询系统下载器进度（每 0.4 秒），刷新进度条
        val poll = object : Runnable {
            override fun run() {
                if (finished) return
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    if (total > 0) {
                        val pct = (soFar * 100 / total).toInt()
                        bar.progress = pct
                        tvPct.text = Lang.s(
                            "已下载 %.1f / %.1f MB（%d%%）",
                            "%.1f / %.1f MB (%d%%)"
                        ).format(soFar / 1048576.0, total / 1048576.0, pct)
                    } else {
                        tvPct.text = Lang.s("已下载 %.1f MB", "%.1f MB").format(soFar / 1048576.0)
                    }
                    if (status == DownloadManager.STATUS_FAILED && !finished) {
                        finished = true
                        try { activity.unregisterReceiver(receiver) } catch (_: Exception) {}
                        if (dlg.isShowing) dlg.dismiss()
                        Toast.makeText(activity, Lang.s("下载失败，请稍后重试", "Download failed, try again later"), Toast.LENGTH_LONG).show()
                        cursor.close()
                        return
                    }
                    cursor.close()
                }
                // 成功由上面的广播接收器处理
                if (!finished) ui.postDelayed(this, 400)
            }
        }
        ui.postDelayed(poll, 400)
    }

    /** 安装 APK：PackageInstaller 会话提交（无需 FileProvider），触发系统安装界面 */
    private fun installApk(activity: Activity, file: File) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(activity, Lang.s("下载文件不存在", "Downloaded file missing"), Toast.LENGTH_LONG).show()
            return
        }
        // Android 8+ 需要用户先允许"安装未知来源应用"
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                activity,
                Lang.s("请先允许「安装未知来源应用」，然后重新点击下载", "Please allow 'Install unknown apps' first, then download again"),
                Toast.LENGTH_LONG
            ).show()
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }
        try {
            val installer = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            // 提交会话 → 系统弹出安装确认界面
            val intent = Intent(activity, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                activity, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pi.intentSender)
            session.close()
        } catch (e: Exception) {
            Toast.makeText(activity, Lang.s("安装调起失败，已打开系统下载页，可手动点击安装包", "Installer failed; opening Downloads, tap the APK manually"), Toast.LENGTH_LONG).show()
            // 兜底：打开系统下载管理器，用户可手动点击安装
            try {
                activity.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
            } catch (_: Exception) {
            }
        }
    }
}
