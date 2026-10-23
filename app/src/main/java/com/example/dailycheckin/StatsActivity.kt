package com.example.dailycheckin

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 统计页：总览 + 各目标明细 + 每日打卡历史
 * 全部统计基于 HabitStorage 中的历史打卡记录实时计算
 */
class StatsActivity : Activity() {

    private val dayFmt
        get() = if (Lang.english) DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
        else DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        Theme.init(this)
        setContentView(R.layout.activity_stats)

        // 应用配色主题：状态栏 + 头部渐变 + 总览大数字
        window.statusBarColor = Theme.dark
        findViewById<View>(R.id.headerBox).background = Theme.headerBg(this)
        findViewById<TextView>(R.id.tvSumTotal).setTextColor(Theme.primary)
        findViewById<TextView>(R.id.tvSumDays).setTextColor(Theme.primary)
        findViewById<TextView>(R.id.tvSumToday).setTextColor(Theme.primary)

        // 双语文案
        findViewById<TextView>(R.id.tvStatsTitle).text = Lang.s("打卡统计", "Statistics")
        findViewById<TextView>(R.id.tvLblSumTotal).text = Lang.s("累计打卡", "Check-ins")
        findViewById<TextView>(R.id.tvLblSumDays).text = Lang.s("打卡天数", "Active days")
        findViewById<TextView>(R.id.tvLblSumToday).text = Lang.s("今日完成", "Today")
        findViewById<TextView>(R.id.tvLblDetail).text = Lang.s("各目标明细", "Goal details")
        findViewById<TextView>(R.id.tvLblHistory).text = Lang.s("每日打卡记录", "Daily history")

        val habits = HabitStorage.load(this)

        findViewById<View>(R.id.tvBack).setOnClickListener { finish() }

        renderSummary(habits)
        renderHabitDetail(habits)
        renderHistory(habits)
    }

    /** 总览：累计打卡次数、打卡天数、今日完成情况 */
    private fun renderSummary(habits: List<Habit>) {
        val total = habits.sumOf { it.totalCount() }
        val activeDays = habits.flatMap { it.checkIns }.distinct().size
        val active = habits.filter { !it.isNotStarted() }
        val todayDone = active.count { it.isCheckedToday() }

        findViewById<TextView>(R.id.tvSumTotal).text = total.toString()
        findViewById<TextView>(R.id.tvSumDays).text = activeDays.toString()
        findViewById<TextView>(R.id.tvSumToday).text =
            if (active.isEmpty()) "-" else "$todayDone/${active.size}"
    }

    /** 各目标明细：类型、总次数、本周/本月进度、完成率 */
    private fun renderHabitDetail(habits: List<Habit>) {
        val container: LinearLayout = findViewById(R.id.habitStatContainer)
        container.removeAllViews()

        if (habits.isEmpty()) {
            container.addView(makeText(Lang.s("暂无目标，先去首页创建一个吧", "No goals yet, create one first"), secondary = true))
            return
        }

        habits.forEach { h ->
            val days = h.daysSinceStart()
            // 完成率：打卡天数 / 从开始到今天的天数
            val rate = if (days > 0) (h.totalCount() * 100 / days).coerceAtMost(100) else 0
            val detail = buildString {
                if (Lang.english) {
                    append("${h.type.label()} · Total ${h.totalCount()}")
                    append(" · Week ${h.weekCount()} · Month ${h.monthCount()}")
                    if (days > 0) append(" · Rate $rate%")
                    if (h.isNotStarted()) append(" · Starts ${h.startDate}")
                } else {
                    append("${h.type.label()}目标 · 共 ${h.totalCount()} 次")
                    append(" · 本周 ${h.weekCount()} 次 · 本月 ${h.monthCount()} 次")
                    if (days > 0) append(" · 完成率 $rate%")
                    if (h.isNotStarted()) append(" · ${h.startDate} 开始")
                }
            }
            container.addView(makeCard(h.name, detail))
        }
    }

    /** 每日打卡历史：按日期倒序，列出当天打卡的目标及心得 */
    private fun renderHistory(habits: List<Habit>) {
        val container: LinearLayout = findViewById(R.id.historyContainer)
        container.removeAllViews()

        // 汇总：日期 -> [(目标名, 心得)]，按日期倒序
        val byDate = sortedMapOf<String, MutableList<Pair<String, String>>>(
            compareByDescending { it }
        )
        habits.forEach { h ->
            h.checkIns.forEach { date ->
                byDate.getOrPut(date) { mutableListOf() }
                    .add(h.name to h.noteFor(date))
            }
        }

        if (byDate.isEmpty()) {
            container.addView(makeText(Lang.s("还没有打卡记录", "No check-ins yet"), secondary = true))
            return
        }

        byDate.forEach { (date, entries) ->
            val d = LocalDate.parse(date)
            val title = d.format(dayFmt)
            // 每行：✓ 目标名，有心得则另起一行缩进显示
            val content = entries.joinToString("\n") { (name, note) ->
                if (note.isEmpty()) "✓ $name" else "✓ $name\n　· $note"
            }
            container.addView(makeCard(title, content))
        }
    }

    /** 生成一条记录卡片（标题 + 内容） */
    private fun makeCard(title: String, content: String): View {
        val dp = resources.displayMetrics.density
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = (10 * dp).toInt()
        card.layoutParams = lp

        card.addView(makeText(title))
        card.addView(makeText(content, secondary = true))
        return card
    }

    private fun makeText(text: String, secondary: Boolean = false): TextView {
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            this.text = text
            textSize = if (secondary) 13f else 15f
            setTextColor(
                if (secondary) 0xFF6B7B8C.toInt() else 0xFF1F2D3D.toInt()
            )
            if (!secondary) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
        }
    }
}
