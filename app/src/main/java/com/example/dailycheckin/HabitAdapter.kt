package com.example.dailycheckin

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 打卡列表适配器（基于框架原生 BaseAdapter，无第三方依赖）
 * 支持两种行：String = 分组标题（三个篮子）；Habit = 目标卡片
 * 动效：卡片入场滑入淡入；打卡按钮弹跳缩放
 * @param onToggle 点击打卡圆圈回调
 * @param onLongClick 长按卡片回调（用于删除）
 */
class HabitAdapter(
    private val context: Context,
    private val items: List<Any>,
    private val onToggle: (Habit) -> Unit,
    private val onLongClick: (Habit) -> Unit
) : BaseAdapter() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    /** 已播放入场动画的目标 id，避免滚动/刷新时重复播放 */
    private val animatedIds = HashSet<Long>()

    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int =
        if (items[position] is String) TYPE_HEADER else TYPE_ITEM
    override fun isEnabled(position: Int): Boolean =
        getItemViewType(position) == TYPE_ITEM

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long {
        val it = items[position]
        return if (it is Habit) it.id else -(it as String).hashCode().toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        // 分组标题行
        if (getItemViewType(position) == TYPE_HEADER) {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#6B7B72"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val dp = context.resources.displayMetrics.density
                setPadding((6 * dp).toInt(), (10 * dp).toInt(), 0, (6 * dp).toInt())
            }
            tv.text = items[position] as String
            return tv
        }

        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_habit, parent, false)
        val habit = items[position] as Habit

        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvType: TextView = view.findViewById(R.id.tvType)
        val tvStreak: TextView = view.findViewById(R.id.tvStreak)
        val tvTotal: TextView = view.findViewById(R.id.tvTotal)
        val btnCheck: TextView = view.findViewById(R.id.btnCheck)
        val dotContainer: LinearLayout = view.findViewById(R.id.dotContainer)

        tvName.text = habit.name
        tvType.text = "${habit.basket.emoji} ${habit.type.label()}"
        // 应用配色主题：标签、连续天数、打卡圆圈
        tvType.setTextColor(Theme.primary)
        tvType.background = Theme.tagBg(context)
        tvStreak.setTextColor(Theme.primary)

        if (habit.isNotStarted()) {
            // 提前设置的未来目标：显示开始日期，打卡按钮置灰
            val d = LocalDate.parse(habit.startDate)
            val label = if (Lang.english) "${d.monthValue}/${d.dayOfMonth}"
            else d.format(DateTimeFormatter.ofPattern("M月d日"))
            tvStreak.text = Lang.s("${label}开始", "Starts $label")
            tvTotal.text = Lang.s("已提前设置", "Scheduled")
            btnCheck.setBackgroundResource(R.drawable.bg_circle_disabled)
            btnCheck.text = ""
            btnCheck.setOnClickListener {
                Toast.makeText(
                    context,
                    Lang.s("「${habit.name}」从 $label 开始", "「${habit.name}」starts $label"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            tvStreak.text = habit.progressText()
            tvTotal.text = Lang.s("共 ${habit.totalCount()} 次", "${habit.totalCount()} check-ins")

            val checked = habit.isCheckedToday()
            btnCheck.background =
                if (checked) Theme.fabBg(context) else Theme.circleUnchecked(context)
            btnCheck.text = if (checked) "✓" else ""
            btnCheck.setOnClickListener { v ->
                // 打卡弹跳动画：放大回弹后再触发状态切换
                v.animate()
                    .scaleX(1.3f).scaleY(1.3f)
                    .setDuration(130)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                        onToggle(habit)
                    }
                    .start()
            }
        }

        view.setOnLongClickListener { onLongClick(habit); true }

        // 最近 7 天打卡圆点：左起为 6 天前，右一为今天
        renderWeekDots(dotContainer, habit)

        // 卡片入场动画：滑入 + 淡入，按位置依次延迟，形成波浪感
        if (!animatedIds.contains(habit.id)) {
            animatedIds.add(habit.id)
            val dp = context.resources.displayMetrics.density
            view.alpha = 0f
            view.translationY = 32 * dp
            view.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay((position * 70).toLong())
                .setDuration(320)
                .start()
        } else {
            // 复用的 view 重置动画属性，避免状态残留
            view.alpha = 1f
            view.translationY = 0f
        }

        return view
    }

    private fun renderWeekDots(container: LinearLayout, habit: Habit) {
        container.removeAllViews()
        val size = (container.resources.displayMetrics.density * 10).toInt()
        val margin = (container.resources.displayMetrics.density * 4).toInt()
        habit.last7Days().forEach { (_, done) ->
            val dot = View(container.context)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, 0, margin, 0)
            dot.layoutParams = lp
            if (done) dot.background = Theme.dotChecked()
            else dot.setBackgroundResource(R.drawable.bg_dot_unchecked) // 未打卡浅灰，与主题无关
            container.addView(dot)
        }
    }
}
