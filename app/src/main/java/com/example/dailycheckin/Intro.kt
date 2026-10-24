package com.example.dailycheckin

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.widget.ScrollView
import android.widget.TextView

/**
 * 软件介绍内容 + 弹窗（主页与设置页共用）
 * showIfNewVersion()：新安装或覆盖升级到新版本后，首次打开主页时自动弹出一次介绍
 */
object Intro {

    private const val PREF = "intro_pref"
    private const val KEY_SHOWN = "shown_version_code"

    /** 当前版本号（versionCode） */
    private fun versionCode(activity: Activity): Int =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode

    /** 若本版本还没展示过介绍，则弹出介绍并记录（覆盖安装升级后也会再次弹出） */
    fun showIfNewVersion(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        val vc = versionCode(activity)
        if (prefs.getInt(KEY_SHOWN, 0) < vc) {
            prefs.edit().putInt(KEY_SHOWN, vc).apply()
            showDialog(activity)
        }
    }

    /** 弹出软件介绍（可滚动，随语言切换，标题栏染主题色） */
    fun showDialog(activity: Activity) {
        val dp = activity.resources.displayMetrics.density
        val tv = TextView(activity).apply {
            text = if (Lang.english) TEXT_EN else TEXT_ZH
            textSize = 14f
            setLineSpacing(0f, 1.35f)
            setTextColor(Color.parseColor("#33403A"))
            val p = (22 * dp).toInt()
            setPadding(p, (12 * dp).toInt(), p, (4 * dp).toInt())
        }
        AlertDialog.Builder(activity)
            .setTitle(Lang.s("软件介绍", "App Guide"))
            .setView(ScrollView(activity).apply { addView(tv) })
            .setPositiveButton(Lang.s("开始使用", "Get Started"), null)
            .show()
    }

    /** 中文介绍 */
    private val TEXT_ZH = """
【这是什么】
「每日任务」是一款结合「三个篮子」任务管理法的打卡应用：每天打卡、记录心得、统计回顾，帮你把时间花在真正重要的事情上。

【快速上手】
1. 点右下角「+」新建任务目标：填写名称、选择目标类型、选择任务篮子、设定开始日期（可提前设置未来目标）；可选「任务时段」，设置后只能在指定时段内打卡，时段外打卡按钮会置灰，不设置则全天可打卡。
2. 点卡片右侧圆圈完成打卡；打卡成功后可记录当天心得。未到开始日期的未来目标不会出现在当天列表，会在开始日期自动显示。
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

【个性化设置】
· 配色选择：7 套主题色随心换，界面即时生效
· 语言设置：中文 / English 一键切换
· 名言收藏：主页顶部每天自动播放名言，短按换下一条，长按收藏
· 检查更新：在线检测新版本，App 内直接下载安装
· 清理缓存：一键删除更新安装包残留，释放存储空间

【核心理念】
忙不等于产出，累不等于价值。真正的效率提升，不在于做得更多，而在于把最宝贵的注意力，押注在真正推动生活的事情上。
    """.trimIndent()

    /** English intro */
    private val TEXT_EN = """
[What is this]
Daily Tasks is a check-in app built on the "Three Baskets" task management method: check in daily, keep notes, review statistics — spend your time on what truly matters.

[Quick start]
1. Tap "+" (bottom right) to create a goal: name, type, basket, and start date (future dates allowed). Optional "time window": once set, check-in is only allowed within that window and the button turns gray outside it; otherwise any time works.
2. Tap the circle on a card to check in; you can add a note after each check-in. Future goals are hidden from today's list and appear automatically on their start date.
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

[Personalization]
· Color theme: 7 palettes, applied instantly
· Language: switch between 中文 and English
· Favorite quotes: quotes rotate on the home page — tap for the next one, long-press to save
· Check for updates: download and install new versions right inside the app
· Clear cache: remove leftover update packages to free up storage

[Core idea]
Busy is not productive, tired is not valuable. Real efficiency means betting your scarcest attention on what truly moves your life forward.
    """.trimIndent()
}
