package com.example.dailycheckin

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable

/**
 * 全局配色主题：每套配色 = 主色 + 深色，所有界面元素在代码中动态取色
 * 切换配色后重启 Activity 生效（与语言切换同理）
 * 默认配色为「清新绿」（即 v1.17 及以前的配色）
 */
object Theme {

    data class Palette(val key: String, val zh: String, val en: String, val primary: Int, val dark: Int)

    val PALETTES = listOf(
        Palette("green",  "清新绿", "Fresh Green", 0xFF2EBD85.toInt(), 0xFF1FA371.toInt()),
        Palette("blue",   "晴空蓝", "Sky Blue",   0xFF55A8F5.toInt(), 0xFF2E86DE.toInt()),
        Palette("orange", "活力橙", "Orange",     0xFFFF9F5A.toInt(), 0xFFF27E2E.toInt()),
        Palette("purple", "梦幻紫", "Purple",     0xFFA88BEB.toInt(), 0xFF8B5CF6.toInt()),
        Palette("pink",   "樱花粉", "Pink",       0xFFF58BB1.toInt(), 0xFFE8639A.toInt()),
        Palette("teal",   "湖水青", "Teal",       0xFF3BC7C0.toInt(), 0xFF1DA39B.toInt()),
        Palette("red",    "番茄红", "Tomato",     0xFFF2736A.toInt(), 0xFFE0524C.toInt()),
    )

    private const val PREF = "settings_pref"
    private const val KEY = "theme_key"

    private var current: Palette = PALETTES[0]

    fun init(context: Context) {
        val key = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, "green")
        current = PALETTES.firstOrNull { it.key == key } ?: PALETTES[0]
    }

    fun set(context: Context, palette: Palette) {
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, palette.key).apply()
        current = palette
    }

    /**
     * 桌面图标跟随配色：7 个 activity-alias 启动别名各挂一张图标，
     * 只启用当前配色对应的别名（DONT_KILL_APP 不杀进程）
     * 注：部分桌面会缓存图标，可能稍等片刻才刷新
     */
    fun applyIcon(context: Context) {
        val pm = context.packageManager
        for (p in PALETTES) {
            val alias = "com.example.dailycheckin.Launcher" +
                p.key.replaceFirstChar { it.uppercase() }
            val cn = android.content.ComponentName(context, alias)
            pm.setComponentEnabledSetting(
                cn,
                if (p == current) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }

    val primary get() = current.primary
    val dark get() = current.dark
    fun current() = current

    /** 标签底色：主色与白色按 88% 混合（替代原 #E3F7EE 浅绿） */
    fun tint(): Int = blend(primary, Color.WHITE, 0.88f)

    /** 头部渐变起始色：主色微微调亮（替代原 #3ED598） */
    private fun headerStart(): Int = blend(primary, Color.WHITE, 0.15f)

    private fun blend(c1: Int, c2: Int, ratio: Float): Int {
        val r = Color.red(c1) * (1 - ratio) + Color.red(c2) * ratio
        val g = Color.green(c1) * (1 - ratio) + Color.green(c2) * ratio
        val b = Color.blue(c1) * (1 - ratio) + Color.blue(c2) * ratio
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }

    /** 头部渐变背景（底部圆角 24dp，对角线渐变到深色） */
    fun headerBg(context: Context): GradientDrawable {
        val dp = context.resources.displayMetrics.density
        val r = 24 * dp
        return GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(headerStart(), dark)
        ).apply {
            // 顺序：左上、右上、右下、左下（每个角 x,y 两个值）
            setCornerRadii(floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r))
        }
    }

    /** 圆形主色按钮背景 + 白色按压涟漪（FAB、已打卡圆圈通用） */
    fun fabBg(context: Context): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(primary)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), content, mask)
    }

    /** 未打卡空心圆：白底 + 主色描边 + 主色涟漪 */
    fun circleUnchecked(context: Context): RippleDrawable {
        val dp = context.resources.displayMetrics.density
        val content = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke((2 * dp).toInt(), primary)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        val ripple = (0x33000000 or (primary and 0x00FFFFFF)).toInt()
        return RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
    }

    /** 最近 7 天已打卡小圆点 */
    fun dotChecked(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(primary)
    }

    /** 目标类型标签背景：主题浅色圆角 */
    fun tagBg(context: Context): GradientDrawable {
        val dp = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(tint())
            cornerRadius = 8 * dp
        }
    }

    /** 输入框背景：聚焦时深色描边，默认浅灰描边 */
    fun inputBg(context: Context): StateListDrawable {
        val dp = context.resources.displayMetrics.density
        val focused = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 10 * dp
            setStroke((1.5f * dp).toInt(), dark)
        }
        val normal = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 10 * dp
            setStroke((1 * dp).toInt(), 0xFFDDE5E0.toInt())
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    /** 对话框标题装饰竖条 */
    fun titleBarBg(context: Context): GradientDrawable {
        val dp = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(dark)
            cornerRadius = 2 * dp
        }
    }
}
