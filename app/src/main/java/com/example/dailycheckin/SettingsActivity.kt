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

    /** 软件介绍弹窗：完整使用指南（可滚动，随语言切换） */
    private fun showIntroDialog() {
        val dp = resources.displayMetrics.density

        val introZh = """
【这是什么】
「每日任务」是一款结合「三个篮子」任务管理法的打卡应用：每天打卡、记录心得、统计回顾，帮你把时间花在真正重要的事情上。

【快速上手】
1. 点右下角「+」新建任务目标：填写名称、选择目标类型、选择任务篮子、设定开始日期（可提前设置未来目标）。
2. 点卡片右侧圆圈完成打卡；打卡成功后可记录当天心得。
3. 长按卡片可查看详情（含月历热力图、全部打卡记录与心得）或删除目标。
4. 点左下角日历按钮进入统计页：总览、完成率、每日记录一目了然。
5. 每晚 20:00 若仍有未完成目标，系统会弹通知提醒你。

【目标类型】
· 每日目标：每天打卡，自动统计连续天数
· 每周目标：本周完成指定次数即可
· 每月目标：本月完成指定次数即可
· 单次目标：临时事项，打卡一次即完成

【三个篮子任务管理法】
源自钱婧教授《新收入》。把所有任务按价值分进三个篮子，区别对待：

🦁 狮子任务 —— 每日必做
今天必须办的、能带来核心成长或收入的重要任务。每天最多 1~3 件，把精力最好的黄金时段（通常上午）留给它，像狮子捕猎一样一击即中，期间不回消息、不被打扰。

🐂 牛马任务 —— 限时做完
不得不做但对成长帮助不大的事务性工作：回邮件、填报表、例行会议等。要诀是「紧」：给它限定时间，到点交差，不追求完美——工作会自动膨胀填满你给的全部时间（帕金森法则）。

🐦 鸵鸟任务 —— 可拖则拖
可做可不做、别人甩过来的锅、无意义的纠结。像鸵鸟一样把头埋进沙子：很多事拖几天会自动消失。判断标准：「不做会有什么实质后果吗？」没有，就进鸵鸟篮子。

【排布逻辑】
主页任务按篮子分三组排布：狮子在上、牛马居中、鸵鸟垫底——一眼看清今天的精力该投向哪里。

【核心理念】
忙不等于产出，累不等于价值。真正的效率提升，不在于做得更多，而在于把最宝贵的注意力，押注在真正推动生活的事情上。
        """.trimIndent()

        val introEn = """
[What is this]
Daily Tasks is a check-in app built on the "Three Baskets" task management method: check in daily, keep notes, review statistics — spend your time on what truly matters.

[Quick start]
1. Tap "+" (bottom right) to create a goal: name, type, basket, and start date (future dates allowed).
2. Tap the circle on a card to check in; you can add a note after each check-in.
3. Long-press a card for details (calendar heatmap, history & notes) or to delete.
4. Tap the calendar button (bottom left) for statistics: overview, completion rate, daily history.
5. At 8:00 PM, you'll get a notification if any goals remain unfinished.

[Goal types]
· Daily goal: check in every day, streak counted automatically
· Weekly goal: complete N times this week
· Monthly goal: complete N times this month
· One-time goal: a one-off task, done once and finished

[The Three Baskets method]
From Prof. Qian Jing's book. Sort every task by value into three baskets:

🦁 Lion tasks — today's must-dos
Important tasks that drive growth or income. At most 1~3 per day. Give them your peak-energy hours (usually mornings), hunt like a lion — no messages, no interruptions.

🐂 Workhorse tasks — time-boxed chores
Necessary but low-value routine work: emails, reports, regular meetings. Keep them tight: set a strict time limit and stop when done. Work expands to fill the time you give it (Parkinson's Law).

🐦 Ostrich tasks — safe to delay
Optional trivia, other people's dumped work, meaningless fussing. Bury your head like an ostrich: many such tasks simply disappear after a few days. Ask: "Any real consequence if I skip this?" If not, it's an ostrich task.

[Layout logic]
Goals on the home page are grouped by basket: lions on top, workhorses in the middle, ostriches at the bottom — see at a glance where your energy should go.

[Core idea]
Busy is not productive, tired is not valuable. Real efficiency means betting your scarcest attention on what truly moves your life forward.
        """.trimIndent()

        val tv = TextView(this).apply {
            text = if (Lang.english) introEn else introZh
            textSize = 14f
            setLineSpacing(0f, 1.35f)
            setTextColor(Color.parseColor("#33403A"))
            val p = (22 * dp).toInt()
            setPadding(p, (12 * dp).toInt(), p, (4 * dp).toInt())
        }
        AlertDialog.Builder(this)
            .setTitle(Lang.s("软件介绍", "App Guide"))
            .setView(ScrollView(this).apply { addView(tv) })
            .setPositiveButton(Lang.s("知道了", "Got it"), null)
            .show()
    }
}
