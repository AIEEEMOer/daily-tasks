package com.example.dailycheckin

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 每晚 20:00 提醒调度器：
 * 若当天存在已开始但未完成打卡的目标，发送系统通知弹窗提醒。
 * 每次触发后自动预约第二天的提醒；开机自启恢复预约。
 */
object ReminderScheduler {

    const val CHANNEL_ID = "checkin_reminder"
    const val NOTIFY_ID = 2001
    const val REQUEST_CODE = 1001

    /** 预约下一个 20:00 的提醒（已过今天 20:00 则约明天） */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DATE, 1)
            }
        }

        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 优先用精确闹钟；系统不允许时退化为窗口闹钟（误差约 10 分钟内）
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, cal.timeInMillis, 10 * 60_000L, pi)
        }
    }
}

/** 20:00 触发：检查未完成目标 → 弹通知 → 预约明天 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Lang.init(context)
        val habits = HabitStorage.load(context)
        // 已开始但今天没打卡的目标（未来的提前目标不提醒）
        val pending = habits.filter { !it.isNotStarted() && !it.isCheckedToday() }

        if (pending.isNotEmpty()) {
            showNotification(context, pending)
        }
        // 无论是否提醒，继续预约明晚 20:00
        ReminderScheduler.scheduleNext(context)
    }

    private fun showNotification(context: Context, pending: List<Habit>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 通知渠道（Android 8.0+ 必须）
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                ReminderScheduler.CHANNEL_ID,
                Lang.s("打卡提醒", "Check-in reminder"),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = Lang.s("每晚 8 点提醒未完成的打卡目标", "Remind unfinished goals at 8 PM daily")
            }
            nm.createNotificationChannel(channel)
        }

        // 点击通知打开应用
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val names = pending.take(3).joinToString(Lang.s("、", ", ")) { it.name }
        val extra = if (pending.size > 3) Lang.s("等 ${pending.size} 个", "and ${pending.size} more") else ""
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val title = if (pending.size == 1) Lang.s("「$names」还没打卡", "「$names」not done yet")
        else Lang.s("还有 ${pending.size} 个目标未完成", "${pending.size} goals unfinished")
        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("$names $extra · " + Lang.s("点击打开每日任务", "Tap to open Daily Tasks"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        nm.notify(ReminderScheduler.NOTIFY_ID, builder.build())
    }
}

/** 开机自启：恢复每晚提醒预约（闹钟重启后会丢失） */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleNext(context)
        }
    }
}
