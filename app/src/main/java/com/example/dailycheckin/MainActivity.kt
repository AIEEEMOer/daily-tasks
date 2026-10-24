package com.example.dailycheckin

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var habits: MutableList<Habit>
    private lateinit var adapter: HabitAdapter
    /** 列表显示数据：String=篮子分组标题，Habit=目标卡片 */
    private val displayItems = mutableListOf<Any>()

    private lateinit var tvDate: TextView
    private lateinit var tvProgress: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvQuote: TextView
    private lateinit var ringProgress: RingProgressView
    private lateinit var emptyBox: View

    // 名言轮播（乱序播放：洗牌顺序，播完一轮重新洗牌）
    private val quoteHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var quoteIndex = 0
    private var quoteOrder = mutableListOf<Int>()
    private var quotePos = 0
    /** 进入主页时的配色，用于从设置页返回时检测配色变化 */
    private var themeOnCreate: Theme.Palette? = null
    private val quoteRunnable = object : Runnable {
        override fun run() {
            nextQuote(animate = true)
            quoteHandler.postDelayed(this, 10000)
        }
    }
    /** 每分钟刷新一次：跨打卡时段边界自动置灰/恢复，未来目标到开始日期自动出现 */
    private val timeTickRunnable = object : Runnable {
        override fun run() {
            if (::habits.isInitialized) refresh()
            quoteHandler.postDelayed(this, 60000)
        }
    }

    companion object {
        // 名言库见 Quotes.kt（420+ 条：古诗词 + 名人名言 + 歌词 + 影视台词 + 励志流行语）
        val QUOTES = Quotes.LIST
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        Theme.init(this)
        themeOnCreate = Theme.current()
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.tvAppTitle).text = Lang.appName

        // —— 应用配色主题：状态栏 + 头部渐变 + 三个底部圆按钮 ——
        window.statusBarColor = Theme.dark
        findViewById<View>(R.id.headerBox).background = Theme.headerBg(this)

        tvDate = findViewById(R.id.tvDate)
        tvProgress = findViewById(R.id.tvProgress)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvQuote = findViewById(R.id.tvQuote)
        ringProgress = findViewById(R.id.ringProgress)
        emptyBox = findViewById(R.id.emptyBox)
        val listView: ListView = findViewById(R.id.listView)
        val fabAdd: TextView = findViewById(R.id.fabAdd)
        val tvStats: TextView = findViewById(R.id.tvStats)
        // 底部三按钮统一使用主题色圆形背景
        fabAdd.background = Theme.fabBg(this)
        tvStats.background = Theme.fabBg(this)
        findViewById<View>(R.id.tvSettings).background = Theme.fabBg(this)

        // 头部装饰圆漂浮动画 + 按钮按压动效
        startFloatAnim(findViewById(R.id.deco1), 22f, 2800)
        startFloatAnim(findViewById(R.id.deco2), -16f, 3400)
        startFloatAnim(findViewById(R.id.tvEmptyIcon), 10f, 2000)
        attachPressAnim(fabAdd)
        attachPressAnim(tvStats)

        // 名言：乱序洗牌播放，10 秒淡入淡出；短按下一条，长按收藏
        quoteOrder = QUOTES.indices.shuffled().toMutableList()
        showQuote(quoteOrder[quotePos], animate = false)
        tvQuote.setOnClickListener {
            quoteHandler.removeCallbacks(quoteRunnable)
            nextQuote(animate = true)
            quoteHandler.postDelayed(quoteRunnable, 10000)
        }
        tvQuote.setOnLongClickListener {
            val added = FavQuotes.add(this, QUOTES[quoteIndex])
            Toast.makeText(
                this,
                if (added) Lang.s("已收藏 ❤️ 可在设置中查看", "Saved ❤️ See it in Settings")
                else Lang.s("这条已在收藏中", "Already in favorites"),
                Toast.LENGTH_SHORT
            ).show()
            true
        }
        quoteHandler.postDelayed(quoteRunnable, 10000)

        habits = HabitStorage.load(this)

        adapter = HabitAdapter(this, displayItems,
            onToggle = { habit ->
                // 打卡按钮在时段外已置灰禁用；这里再静默校验一次，不弹提示
                // 已打卡的记录即使过了时段也允许取消
                if (habit.isCheckedToday() || habit.isInWindowNow()) {
                    val checked = habit.toggleToday()
                    HabitStorage.save(this, habits)
                    refresh()
                    // 打卡成功后弹出心得记录（可跳过）
                    if (checked) showNoteDialog(habit)
                }
            },
            onLongClick = { habit -> showOptionsDialog(habit) }
        )
        listView.adapter = adapter

        fabAdd.setOnClickListener { showAddDialog() }
        tvStats.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<View>(R.id.tvSettings).apply {
            attachPressAnim(this)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        // 请求通知权限（Android 13+），保证每晚 8 点提醒能弹窗
        requestNotificationPermission()
        // 预约今晚/明晚 8 点的打卡提醒
        ReminderScheduler.scheduleNext(this)
        // 每天首次打开静默检查一次更新（有新版本才弹窗）
        UpdateManager.autoCheck(this)

        refresh()

        // 新安装或升级到新版本后，首次打开先展示软件介绍（每个版本只弹一次）
        Intro.showIfNewVersion(this)
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回时若配色已变，就地重建主页（无需重启整个 App）
        if (themeOnCreate != null && themeOnCreate != Theme.current()) {
            themeOnCreate = Theme.current()
            recreate()
            return
        }
        // 更新安装完成回到 App 时，自动删除残留安装包
        UpdateManager.cleanupIfUpdated(this)
        if (::habits.isInitialized) refresh()
        quoteHandler.removeCallbacks(timeTickRunnable)
        quoteHandler.postDelayed(timeTickRunnable, 60000)
    }

    override fun onPause() {
        quoteHandler.removeCallbacks(timeTickRunnable)
        super.onPause()
    }

    /** 请求通知权限（Android 13+ 需运行时授权，否则提醒无法弹窗） */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    /** 刷新头部统计与列表 */
    private fun refresh() {
        tvDate.text = if (Lang.english)
            SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(Date())
        else SimpleDateFormat("M月d日 EEEE", Locale.CHINESE).format(Date())

        val active = habits.filter { !it.isNotStarted() }
        val done = active.count { it.isCheckedToday() }
        tvProgress.text = if (habits.isEmpty())
            Lang.s("点击下方 + 创建第一个任务目标", "Tap + below to create your first goal")
        else Lang.s("今日已打卡 $done / ${active.size}", "Today: $done / ${active.size} done")
        tvEmpty.text = if (habits.isEmpty())
            Lang.s("种下第一个小目标吧\n点击右下角 + 开始", "Plant your first goal\nTap + to start")
        else Lang.s(
            "今天暂无打卡任务\n未来目标会在开始日期自动出现",
            "No goals for today\nScheduled goals will appear on their start date"
        )
        // 右上角圆环同步今日完成度
        ringProgress.setProgress(done, active.size)

        // 空状态按"今天的任务"判断：只有未来任务时也显示说明，不留空白页
        emptyBox.visibility = if (active.isEmpty()) View.VISIBLE else View.GONE

        // 按三个篮子分组排布：狮子 → 牛马 → 鸵鸟，各组带标题
        // 仅显示今天已到开始日期的任务，未来目标不占当天列表
        displayItems.clear()
        for (b in Basket.values()) {
            val group = active.filter { it.basket == b }
            if (group.isNotEmpty()) {
                displayItems.add(b.title())
                displayItems.addAll(group)
            }
        }
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        quoteHandler.removeCallbacks(quoteRunnable)
        quoteHandler.removeCallbacks(timeTickRunnable)
        super.onDestroy()
    }

    /** 乱序推进到下一条名言，播完一轮后重新洗牌 */
    private fun nextQuote(animate: Boolean) {
        quotePos++
        if (quotePos >= quoteOrder.size) {
            quoteOrder = QUOTES.indices.shuffled().toMutableList()
            quotePos = 0
        }
        showQuote(quoteOrder[quotePos], animate)
    }

    /** 显示第 index 条名言（循环）；animate=true 时淡入淡出切换 */
    private fun showQuote(index: Int, animate: Boolean) {
        quoteIndex = ((index % QUOTES.size) + QUOTES.size) % QUOTES.size
        val text = QUOTES[quoteIndex]
        if (!animate) {
            tvQuote.text = text
            return
        }
        tvQuote.animate().alpha(0f).setDuration(250).withEndAction {
            tvQuote.text = text
            tvQuote.animate().alpha(1f).setDuration(350).start()
        }.start()
    }

    /** 上下漂浮循环动画（装饰圆、空状态图标） */
    private fun startFloatAnim(target: View, distance: Float, duration: Long) {
        ObjectAnimator.ofFloat(target, "translationY", 0f, distance).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }
    }

    /** 按钮按压缩放动效：按下缩小，松开回弹 */
    private fun attachPressAnim(v: View) {
        v.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.animate()
                    .scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                    .scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            false
        }
    }

    /** 新建目标对话框：名称 + 类型（每天/每周/每月）+ 目标次数 + 开始日期选择 */
    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_habit, null)
        val etName: EditText = view.findViewById(R.id.etName)
        val spType: Spinner = view.findViewById(R.id.spType)
        val spBasket: Spinner = view.findViewById(R.id.spBasket)
        val etTarget: EditText = view.findViewById(R.id.etTarget)
        val targetRow: View = view.findViewById(R.id.targetRow)
        val tvStartDate: TextView = view.findViewById(R.id.tvStartDate)
        val cbWindow: android.widget.CheckBox = view.findViewById(R.id.cbWindow)
        val windowRow: View = view.findViewById(R.id.windowRow)
        val tvWinStart: TextView = view.findViewById(R.id.tvWinStart)
        val tvWinEnd: TextView = view.findViewById(R.id.tvWinEnd)

        // 应用配色主题：标题竖条 + 输入框聚焦描边
        view.findViewById<View>(R.id.tvTitleBar).background = Theme.titleBarBg(this)
        etName.background = Theme.inputBg(this)
        spType.background = Theme.inputBg(this)
        spBasket.background = Theme.inputBg(this)
        etTarget.background = Theme.inputBg(this)
        tvStartDate.background = Theme.inputBg(this)
        tvWinStart.background = Theme.inputBg(this)
        tvWinEnd.background = Theme.inputBg(this)
        cbWindow.buttonTintList = android.content.res.ColorStateList.valueOf(Theme.primary)

        // 双语文案与下拉选项（对话框标题已内嵌在布局中）
        view.findViewById<TextView>(R.id.tvDlgTitle).text = Lang.s("✨ 新建任务目标", "✨ New Goal")
        view.findViewById<TextView>(R.id.tvDlgSubtitle).text =
            Lang.s("设定一个小目标，坚持就会开花", "Set a small goal — consistency makes it bloom")
        view.findViewById<TextView>(R.id.tvLblName).text = Lang.s("✏️ 目标名称", "✏️ Goal name")
        etName.hint = Lang.s("如：早起、背单词", "e.g. Running, Reading")
        view.findViewById<TextView>(R.id.tvLblType).text = Lang.s("🎯 目标类型", "🎯 Goal type")
        view.findViewById<TextView>(R.id.tvLblBasket).text = Lang.s("🧺 任务篮子", "🧺 Basket")
        view.findViewById<TextView>(R.id.tvLblTarget).text = Lang.s("🔢 目标次数", "🔢 Times")
        view.findViewById<TextView>(R.id.tvLblStart).text = Lang.s("📅 开始日期", "📅 Start date")
        view.findViewById<TextView>(R.id.tvLblWindow).text = Lang.s("⏰ 任务时段", "⏰ Time window")
        cbWindow.text = Lang.s("仅在指定时段内允许打卡（默认全天）", "Only allow check-in within a time window (default: all day)")
        view.findViewById<TextView>(R.id.tvWinTo).text = Lang.s("至", "to")
        spType.adapter = spinnerAdapter(Lang.typeOptions)
        spBasket.adapter = spinnerAdapter(Lang.basketOptions)

        // 目标次数仅在选择"每周/每月目标"时显示，每日/单次目标隐藏
        spType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                targetRow.visibility =
                    if (pos == 1 || pos == 2) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }

        // 开始日期默认今天，可通过日期选择器提前设置未来日期
        var startDate = LocalDate.now()
        val df = DateTimeFormatter.ofPattern(Lang.s("yyyy年M月d日", "yyyy-MM-dd"))
        tvStartDate.text = startDate.format(df)
        tvStartDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    startDate = LocalDate.of(y, m + 1, d)
                    tvStartDate.text = startDate.format(df)
                },
                startDate.year, startDate.monthValue - 1, startDate.dayOfMonth
            ).show()
        }

        // 任务时段：默认不勾选=全天；勾选后展示起止时间选择（24 小时制）
        var winStart = 8 * 60   // 默认 08:00
        var winEnd = 22 * 60    // 默认 22:00
        fun fmtMin(min: Int) = String.format("%02d:%02d", min / 60, min % 60)
        tvWinStart.text = fmtMin(winStart)
        tvWinEnd.text = fmtMin(winEnd)
        cbWindow.setOnCheckedChangeListener { _, checked ->
            windowRow.visibility = if (checked) View.VISIBLE else View.GONE
        }
        tvWinStart.setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _, h, m -> winStart = h * 60 + m; tvWinStart.text = fmtMin(winStart) },
                winStart / 60, winStart % 60, true
            ).show()
        }
        tvWinEnd.setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _, h, m -> winEnd = h * 60 + m; tvWinEnd.text = fmtMin(winEnd) },
                winEnd / 60, winEnd % 60, true
            ).show()
        }

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(Lang.s("创建", "Create")) { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, Lang.s("名称不能为空", "Name required"), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val type = when (spType.selectedItemPosition) {
                    1 -> HabitType.WEEKLY
                    2 -> HabitType.MONTHLY
                    3 -> HabitType.ONCE
                    else -> HabitType.DAILY
                }
                // 单次打卡固定 1 次；开始日期即待办日期
                val target = if (type == HabitType.ONCE) 1
                else etTarget.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                // 任务时段：起止时间不能相同（相同等同于全天无意义，提示用户修改）
                if (cbWindow.isChecked && winStart == winEnd) {
                    Toast.makeText(
                        this,
                        Lang.s("开始与结束时间不能相同", "Start and end time must differ"),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                habits.add(
                    Habit(
                        id = System.currentTimeMillis(),
                        name = name,
                        type = type,
                        target = target,
                        startDate = startDate.format(Habit.FMT),
                        basket = Basket.values()[spBasket.selectedItemPosition],
                        windowStart = if (cbWindow.isChecked) winStart else -1,
                        windowEnd = if (cbWindow.isChecked) winEnd else -1
                    )
                )
                HabitStorage.save(this, habits)
                refresh()
            }
            .setNegativeButton(Lang.cancel, null)
            .show().also { tintButtons(it) }
    }

    /** 对话框按钮文字着主题色（默认 colorAccent 不会随配色切换） */
    private fun tintButtons(dlg: AlertDialog) {
        dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Theme.dark)
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Theme.dark)
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Theme.dark)
    }

    /** Spinner 下拉适配器（选项随语言切换） */
    private fun spinnerAdapter(items: Array<String>) =
        android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    /** 长按目标：菜单（查看详情 / 删除） */
    private fun showOptionsDialog(habit: Habit) {
        AlertDialog.Builder(this)
            .setTitle(habit.name)
            .setItems(arrayOf(Lang.s("查看详情与心得", "Details & notes"), Lang.s("删除任务目标", "Delete goal"))) { _, which ->
                when (which) {
                    0 -> showDetailDialog(habit)
                    1 -> showDeleteDialog(habit)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 目标详情：基本信息 + 月历热力图（可翻月）+ 全部打卡记录和心得（按日期倒序） */
    private fun showDetailDialog(habit: Habit) {
        val dp = resources.displayMetrics.density
        val pad = (24 * dp).toInt()
        val sb = StringBuilder()

        sb.append(Lang.s("类型：", "Type: ") + habit.type.label())
        if (habit.type == HabitType.WEEKLY || habit.type == HabitType.MONTHLY) {
            sb.append(Lang.s("（目标 ${habit.target} 次）", " (goal: ${habit.target}×)"))
        }
        sb.append(Lang.s("\n任务篮子：", "\nBasket: ") + habit.basket.title())
        sb.append(Lang.s("\n开始日期：", "\nStart: ") + habit.startDate)
        sb.append(Lang.s("\n任务时段：", "\nTime window: ") + habit.windowText())
        sb.append(Lang.s("\n累计打卡：${habit.totalCount()} 次", "\nTotal check-ins: ${habit.totalCount()}"))
        when (habit.type) {
            HabitType.DAILY -> sb.append(Lang.s(" · 连续 ${habit.currentStreak()} 天", " · ${habit.currentStreak()}-day streak"))
            HabitType.WEEKLY -> sb.append(Lang.s(" · 本周 ${habit.weekCount()}/${habit.target} 次", " · This week ${habit.weekCount()}/${habit.target}"))
            HabitType.MONTHLY -> sb.append(Lang.s(" · 本月 ${habit.monthCount()}/${habit.target} 次", " · This month ${habit.monthCount()}/${habit.target}"))
            HabitType.ONCE -> sb.append(Lang.s(" · 单次目标", " · One-time"))
        }
        sb.append(Lang.s(" · 心得 ${habit.noteCount()} 篇", " · ${habit.noteCount()} notes"))

        val sbRecords = StringBuilder(Lang.s("—— 打卡记录（按日期查看） ——\n", "—— Check-in history ——\n"))
        if (habit.checkIns.isEmpty()) {
            sbRecords.append(Lang.s("暂无打卡记录", "No records yet"))
        } else {
            habit.checkIns.sortedDescending().forEach { date ->
                sbRecords.append(date)
                val note = habit.noteFor(date)
                sbRecords.append(if (note.isEmpty()) Lang.s("\n（未写心得）", "\n(no note)") else Lang.s("\n心得：", "\nNote: ") + note)
                sbRecords.append("\n\n")
            }
        }

        // —— 热力图区域：月份切换行 + 月历 ——
        var heatYm = java.time.YearMonth.now()
        val heat = HeatmapView(this)
        val tvMonth = TextView(this).apply {
            textSize = 15f
            setTextColor(android.graphics.Color.parseColor("#333333"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        fun updateHeat() {
            tvMonth.text = if (Lang.english) "📅 ${heatYm}" else "📅 ${heatYm.year}年${heatYm.monthValue}月"
            heat.setData(habit.checkIns, heatYm, habit.startDate)
        }
        fun navBtn(t: String) = TextView(this).apply {
            text = t
            textSize = 20f
            setTextColor(Theme.dark)
            val p = (14 * dp).toInt()
            setPadding(p, 0, p, 0)
        }
        val btnPrev = navBtn("‹")
        val btnNext = navBtn("›")
        btnPrev.setOnClickListener { heatYm = heatYm.minusMonths(1); updateHeat() }
        btnNext.setOnClickListener { heatYm = heatYm.plusMonths(1); updateHeat() }
        val heatHeader = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(tvMonth, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            addView(btnPrev)
            addView(btnNext)
        }
        updateHeat()

        val tv = TextView(this).apply {
            text = sbRecords.toString()
            textSize = 14f
        }
        val tvInfo = TextView(this).apply {
            text = sb.toString()
            textSize = 14f
        }

        // 整体：外滚动 + 竖排容器（基本信息 → 热力图 → 打卡记录）
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, (8 * dp).toInt(), pad, 0)
            addView(tvInfo)
            addView(heatHeader, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (14 * dp).toInt()
            })
            addView(heat, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (8 * dp).toInt()
            })
            addView(tv, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (12 * dp).toInt()
            })
        }
        val scroll = ScrollView(this).apply { addView(container) }

        AlertDialog.Builder(this)
            .setTitle(habit.name)
            .setView(scroll)
            .setPositiveButton(Lang.ok, null)
            .show().also { tintButtons(it) }
    }

    /** 打卡成功后记录心得（可跳过；再次打卡可修改） */
    private fun showNoteDialog(habit: Habit) {
        val today = Habit.today()
        val dp = resources.displayMetrics.density
        val edit = EditText(this).apply {
            hint = Lang.s("记录今天的心得，如：今天状态不错，跑完了 3 公里", "How did it go today? e.g. Finished a 3km run")
            setText(habit.noteFor(today))
            setTextIsSelectable(false)
            minLines = 3
            gravity = android.view.Gravity.TOP
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), 0)
        }
        AlertDialog.Builder(this)
            .setTitle(Lang.s("「${habit.name}」打卡成功", "「${habit.name}」done!"))
            .setView(edit)
            .setPositiveButton(Lang.s("保存心得", "Save note")) { _, _ ->
                habit.setNote(today, edit.text.toString())
                HabitStorage.save(this, habits)
                val msg = if (habit.noteFor(today).isEmpty()) Lang.s("已跳过心得", "Skipped") else Lang.s("心得已保存", "Note saved")
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(Lang.s("跳过", "Skip"), null)
            .show().also { tintButtons(it) }
    }

    /** 删除确认 */
    private fun showDeleteDialog(habit: Habit) {
        AlertDialog.Builder(this)
            .setTitle(Lang.s("删除「${habit.name}」？", "Delete 「${habit.name}」?"))
            .setMessage(Lang.s("该目标的所有打卡记录和心得将一并删除", "All check-ins and notes of this goal will be deleted"))
            .setPositiveButton(Lang.s("删除", "Delete")) { _, _ ->
                habits.remove(habit)
                HabitStorage.save(this, habits)
                refresh()
            }
            .setNegativeButton(Lang.cancel, null)
            .show().also { tintButtons(it) }
    }
}
