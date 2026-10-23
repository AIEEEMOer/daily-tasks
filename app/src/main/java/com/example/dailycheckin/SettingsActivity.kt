package com.example.dailycheckin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 设置页（代码构建 UI，无第三方依赖）
 * 功能一：软件介绍；功能二：语言设置（中文 / English）
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        Theme.init(this)
        window.statusBarColor = Theme.dark
        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F7F5"))
        }

        // 头部：返回 + 标题
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(8.dp(), 40.dp(), 16.dp(), 14.dp())
        }
        val btnBack = TextView(this).apply {
            text = "‹"
            textSize = 32f
            setTextColor(Theme.dark)
            setPadding(12.dp(), 0, 12.dp(), 0)
            setOnClickListener { finish() }
        }
        val tvTitle = TextView(this).apply {
            text = Lang.s("设置", "Settings")
            textSize = 20f
            setTextColor(Color.parseColor("#222B26"))
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(btnBack)
        header.addView(tvTitle)
        root.addView(header)
        // 头部分隔线
        root.addView(android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#E6EDE9"))
        }, LinearLayout.LayoutParams(-1, 1.dp()))

        // 内容：设置项列表
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
        }

        content.addView(settingItem(
            Lang.s("📖 软件介绍", "📖 App Guide"),
            Lang.s("功能用法与「三个篮子」任务管理法详解", "How to use · The Three Baskets method")
        ) { showIntroDialog() })

        content.addView(settingItem(
            Lang.s("🌐 语言设置", "🌐 Language"),
            if (Lang.english) "English" else "中文",
            marginTop = true
        ) { showLanguageDialog() })

        content.addView(settingItem(
            Lang.s("🎨 配色选择", "🎨 Color Theme"),
            Lang.s("当前：${Theme.current().zh}", "Current: ${Theme.current().en}"),
            marginTop = true
        ) { showThemeDialog() })

        content.addView(settingItem(
            Lang.s("❤️ 名言收藏", "❤️ Favorite Quotes"),
            Lang.s("已收藏 ${FavQuotes.list(this).size} 条，长按主页名言可收藏",
                "${FavQuotes.list(this).size} saved · long-press a quote on home page"),
            marginTop = true
        ) { showFavDialog() })

        content.addView(settingItem(
            Lang.s("🧹 清理缓存", "🧹 Clear Cache"),
            Lang.s("更新安装包占用：${UpdateManager.cacheSizeText(this)}",
                "Update packages: ${UpdateManager.cacheSizeText(this)}"),
            marginTop = true
        ) { showClearCacheDialog() })

        // 检查更新固定为最后一项
        content.addView(settingItem(
            Lang.s("🔄 检查更新", "🔄 Check for Updates"),
            Lang.s("当前版本 v${UpdateManager.currentVersion(this)}", "Current: v${UpdateManager.currentVersion(this)}"),
            marginTop = true
        ) { UpdateManager.checkAsync(this, manual = true) })

        root.addView(ScrollView(this).apply { addView(content) })
        setContentView(root)
    }

    /** 设置项卡片：标题 + 副标题 + 右箭头 */
    private fun settingItem(
        title: String, subtitle: String,
        marginTop: Boolean = false, onClick: () -> Unit
    ): android.view.View {
        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#222B26"))
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@SettingsActivity).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#8A9890"))
                setPadding(0, 4.dp(), 0, 0)
            })
        }
        val arrow = TextView(this).apply {
            text = "›"
            textSize = 24f
            setTextColor(Color.parseColor("#B6C4BC"))
        }
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            addView(arrow)
            setOnClickListener { onClick() }
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        if (marginTop) lp.topMargin = 12.dp()
        item.layoutParams = lp
        return item
    }

    /** 语言选择：中文 / English，切换后重启 App 生效 */
    private fun showLanguageDialog() {
        val options = arrayOf("中文", "English")
        val checked = if (Lang.english) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(Lang.s("语言设置", "Language"))
            .setSingleChoiceItems(options, checked) { dlg, which ->
                val toEnglish = which == 1
                if (toEnglish != Lang.english) {
                    Lang.setEnglish(this, toEnglish)
                    // 重启 App 回到主页，全部文案按新语言渲染
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
                dlg.dismiss()
            }
            .setNegativeButton(Lang.cancel, null)
            .show()
    }

    /** 配色选择：7 套主题色单选，就地刷新生效（不重启 App）；图标退出后才切换 */
    private fun showThemeDialog() {
        val names = Theme.PALETTES.map { if (Lang.english) it.en else it.zh }.toTypedArray()
        val checked = Theme.PALETTES.indexOf(Theme.current())
        AlertDialog.Builder(this)
            .setTitle(Lang.s("配色选择", "Color Theme"))
            .setSingleChoiceItems(names, checked) { dlg, which ->
                val p = Theme.PALETTES[which]
                if (p != Theme.current()) {
                    Theme.set(this, p) // 只存偏好；图标在退出 App 后由 MainActivity 切换
                    recreate() // 设置页就地刷新为新配色
                }
                dlg.dismiss()
            }
            .setNegativeButton(Lang.cancel, null)
            .show()
    }

    /** 清理缓存：删除残留的更新安装包（不影响打卡数据） */
    private fun showClearCacheDialog() {
        val size = UpdateManager.cacheSizeText(this)
        AlertDialog.Builder(this)
            .setTitle(Lang.s("清理缓存", "Clear Cache"))
            .setMessage(
                Lang.s("将删除已下载的更新安装包（当前占用：$size）。\n打卡数据、收藏和设置不受影响。",
                    "This deletes downloaded update packages ($size).\nYour check-ins, favorites and settings are safe.")
            )
            .setPositiveButton(Lang.s("清理", "Clear")) { _, _ ->
                UpdateManager.clearCache(this)
                android.widget.Toast.makeText(
                    this, Lang.s("缓存已清理", "Cache cleared"), android.widget.Toast.LENGTH_SHORT
                ).show()
                recreate() // 刷新占用显示
            }
            .setNegativeButton(Lang.cancel, null)
            .show()
    }

    /** 名言收藏：查看已收藏的名言，点击可移除 */
    private fun showFavDialog() {
        val favs = FavQuotes.list(this)
        if (favs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(Lang.s("名言收藏", "Favorite Quotes"))
                .setMessage(
                    Lang.s("还没有收藏名言。\n回到主页，长按上方的名言即可收藏。",
                        "No favorites yet.\nLong-press the quote on the home page to save one.")
                )
                .setPositiveButton(Lang.ok, null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(Lang.s("名言收藏（${favs.size}）", "Favorites (${favs.size})"))
            .setItems(favs.toTypedArray()) { _, which ->
                val q = favs[which]
                AlertDialog.Builder(this)
                    .setMessage(Lang.s("移除这条收藏？\n\n$q", "Remove this quote?\n\n$q"))
                    .setPositiveButton(Lang.s("移除", "Remove")) { _, _ ->
                        FavQuotes.remove(this, q)
                        recreate() // 刷新设置页上的收藏数量
                    }
                    .setNegativeButton(Lang.cancel, null)
                    .show()
            }
            .setNegativeButton(Lang.s("关闭", "Close"), null)
            .show()
    }

    /** 软件介绍弹窗：内容与主页首次启动弹窗共用（见 Intro.kt） */
    private fun showIntroDialog() {
        Intro.showDialog(this)
    }
}
