package com.example.dailycheckin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 目标类型：每日目标 / 每周目标 / 每月目标 / 单次目标 */
enum class HabitType {
    DAILY, WEEKLY, MONTHLY, ONCE;

    fun label(): String = when (this) {
        DAILY -> Lang.s("每天", "Daily")
        WEEKLY -> Lang.s("每周", "Weekly")
        MONTHLY -> Lang.s("每月", "Monthly")
        ONCE -> Lang.s("单次", "One-time")
    }
}

/** 三个篮子：狮子（每日必做）/ 牛马（事务性）/ 鸵鸟（可拖延），列表按此顺序分组 */
enum class Basket(val emoji: String) {
    LION("🦁"),
    CATTLE("🐂"),
    OSTRICH("🐦");

    fun label(): String = when (this) {
        LION -> Lang.s("狮子任务", "Lion tasks")
        CATTLE -> Lang.s("牛马任务", "Workhorse tasks")
        OSTRICH -> Lang.s("鸵鸟任务", "Ostrich tasks")
    }

    fun title(): String = "$emoji ${label()}"
}

/**
 * 打卡目标数据模型
 * @param id 唯一标识（创建时间戳）
 * @param name 目标名称，如"早起""背单词"
 * @param type 目标类型（每天/每周/每月）
 * @param target 每周/每月目标次数（DAILY 类型忽略）
 * @param startDate 开始日期 yyyy-MM-dd，可提前设置未来日期
 * @param checkIns 所有打卡日期，格式 yyyy-MM-dd
 */
data class Habit(
    val id: Long,
    var name: String,
    var type: HabitType = HabitType.DAILY,
    var target: Int = 1,
    var startDate: String = today(),
    var basket: Basket = Basket.LION,
    val checkIns: MutableSet<String> = mutableSetOf(),
    /** 心得记录：打卡日期 -> 心得内容 */
    val notes: MutableMap<String, String> = mutableMapOf()
) {
    /** 今天是否已打卡 */
    fun isCheckedToday(): Boolean = checkIns.contains(today())

    /** 今天是否还未到开始日期（提前设置的未来目标） */
    fun isNotStarted(): Boolean = LocalDate.now().isBefore(LocalDate.parse(startDate))

    /** 在某日期打卡/取消打卡，返回操作后是否已打卡 */
    fun toggleToday(): Boolean {
        val t = today()
        return if (checkIns.contains(t)) {
            checkIns.remove(t)
            false
        } else {
            checkIns.add(t)
            true
        }
    }

    /**
     * 连续打卡天数（仅对 DAILY 有实际意义）：
     * 今天已打卡则从今天往前数；否则从昨天往前数
     */
    fun currentStreak(): Int {
        var day = LocalDate.now()
        if (!checkIns.contains(day.format(FMT))) day = day.minusDays(1)
        var streak = 0
        while (checkIns.contains(day.format(FMT))) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** 本周（周一起）已打卡次数 */
    fun weekCount(): Int {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        return (0..6).count { checkIns.contains(monday.plusDays(it.toLong()).format(FMT)) }
    }

    /** 本月已打卡次数 */
    fun monthCount(): Int {
        val prefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        return checkIns.count { it.startsWith(prefix) }
    }

    /** 卡片上的进度文字 */
    fun progressText(): String = when (type) {
        HabitType.DAILY -> {
            val s = currentStreak()
            if (s > 0) Lang.s("连续 $s 天", "$s-day streak") else Lang.s("今天开始打卡吧", "Start today")
        }
        HabitType.WEEKLY -> Lang.s("本周 ${weekCount()}/$target 次", "This week ${weekCount()}/$target")
        HabitType.MONTHLY -> Lang.s("本月 ${monthCount()}/$target 次", "This month ${monthCount()}/$target")
        HabitType.ONCE -> {
            // 单次目标：打完即完成，未打显示待办日期
            if (totalCount() > 0) Lang.s("已完成 ✓", "Done ✓")
            else {
                val d = LocalDate.parse(startDate)
                if (d.isAfter(LocalDate.now()))
                    Lang.s("${d.monthValue}月${d.dayOfMonth}日待办", "Due ${d.monthValue}/${d.dayOfMonth}")
                else Lang.s("今日待办", "Due today")
            }
        }
    }

    /** 周期目标是否已达成（用于卡片高亮） */
    fun periodDone(): Boolean = when (type) {
        HabitType.DAILY -> isCheckedToday()
        HabitType.WEEKLY -> weekCount() >= target
        HabitType.MONTHLY -> monthCount() >= target
        HabitType.ONCE -> totalCount() > 0
    }

    fun totalCount(): Int = checkIns.size

    /** 获取某天的心得，无则返回空串 */
    fun noteFor(date: String): String = notes[date] ?: ""

    /** 保存/更新某天的心得，内容为空则删除该条 */
    fun setNote(date: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) notes.remove(date) else notes[date] = t
    }

    /** 有心得的打卡天数 */
    fun noteCount(): Int = notes.size

    /** 从开始日期到今天的天数（含今天），用于完成率统计 */
    fun daysSinceStart(): Long {
        val start = LocalDate.parse(startDate)
        val now = LocalDate.now()
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, now) + 1
        return if (days > 0) days else 0
    }

    /** 最近 7 天（含今天）的打卡状态，index 0 = 6 天前，index 6 = 今天 */
    fun last7Days(): List<Pair<String, Boolean>> {
        val todayD = LocalDate.now()
        return (6 downTo 0).map { offset ->
            val d = todayD.minusDays(offset.toLong())
            d.format(FMT) to checkIns.contains(d.format(FMT))
        }
    }

    companion object {
        val FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        fun today(): String = LocalDate.now().format(FMT)
    }
}

/** 基于 SharedPreferences + JSON 的本地存储，无需额外依赖 */
object HabitStorage {

    private const val PREF = "habits_pref"
    private const val KEY = "habits_json"

    fun load(context: Context): MutableList<Habit> {
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<Habit>()
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val dates = mutableSetOf<String>()
            val dArr = o.getJSONArray("checkIns")
            for (j in 0 until dArr.length()) dates.add(dArr.getString(j))
            val type = try {
                HabitType.valueOf(o.optString("type", "DAILY"))
            } catch (e: Exception) {
                HabitType.DAILY
            }
            // 任务篮子（旧版本数据无此字段，默认狮子任务）
            val basket = try {
                Basket.valueOf(o.optString("basket", "LION"))
            } catch (e: Exception) {
                Basket.LION
            }
            // 心得记录（旧版本数据无此字段，自动兼容）
            val noteMap = mutableMapOf<String, String>()
            o.optJSONObject("notes")?.let { no ->
                for (j in 0 until no.length()) {
                    no.names().getString(j)?.let { key ->
                        noteMap[key] = no.getString(key)
                    }
                }
            }
            list.add(
                Habit(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    type = type,
                    target = o.optInt("target", 1).coerceAtLeast(1),
                    startDate = o.optString("startDate", Habit.today()),
                    basket = basket,
                    checkIns = dates,
                    notes = noteMap
                )
            )
        }
        return list
    }

    fun save(context: Context, habits: List<Habit>) {
        val arr = JSONArray()
        habits.forEach { h ->
            val o = JSONObject()
            o.put("id", h.id)
            o.put("name", h.name)
            o.put("type", h.type.name)
            o.put("target", h.target)
            o.put("startDate", h.startDate)
            o.put("basket", h.basket.name)
            o.put("checkIns", JSONArray(h.checkIns.toList()))
            val no = JSONObject()
            h.notes.forEach { (d, t) -> no.put(d, t) }
            o.put("notes", no)
            arr.put(o)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
