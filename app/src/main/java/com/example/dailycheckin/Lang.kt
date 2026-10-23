package com.example.dailycheckin

import android.content.Context

/**
 * 轻量多语言管理：中文 / English
 * 用法：Lang.s("中文", "English")；切换语言后需重建 Activity 生效
 */
object Lang {

    private const val PREF = "settings_pref"
    private const val KEY_EN = "english"

    var english = false
        private set

    fun init(context: Context) {
        english = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_EN, false)
    }

    fun setEnglish(context: Context, value: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_EN, value).apply()
        english = value
    }

    /** 按当前语言取文案 */
    fun s(zh: String, en: String): String = if (english) en else zh

    // —— 常用固定文案 ——
    val appName get() = s("每日任务", "Daily Tasks")
    val cancel get() = s("取消", "Cancel")
    val ok get() = s("好的", "OK")

    /** 目标类型选项（新建对话框 Spinner） */
    val typeOptions
        get() = if (english)
            arrayOf("Daily goal", "Weekly goal", "Monthly goal", "One-time goal")
        else
            arrayOf("每日目标", "每周目标", "每月目标", "单次目标")

    /** 三个篮子选项（新建对话框 Spinner） */
    val basketOptions
        get() = if (english)
            arrayOf("🦁 Lion task", "🐂 Workhorse task", "🐦 Ostrich task")
        else
            arrayOf("🦁 狮子任务", "🐂 牛马任务", "🐦 鸵鸟任务")
}
