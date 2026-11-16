package com.EhsanJebeli.missedcallnamer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsMessage
import androidx.core.app.NotificationCompat
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        val sb = StringBuilder()
        for (pdu in pdus) {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SmsMessage.createFromPdu(pdu as ByteArray, format)
            } else {
                @Suppress("DEPRECATION")
                SmsMessage.createFromPdu(pdu as ByteArray)
            }
            sb.append(sms.displayMessageBody)
        }

        val text = sb.toString()

        // تشخیص انواع پیامک‌های تماس از دست رفته و در دسترس بودن
        val isMciMissed = text.contains("تماس بی پاسخ") || text.contains("تماس بی‌پاسخ")
        val isIrancellMissed = text.contains("تعداد") && text.contains("تماس از")
        val isAvailable = text.contains("در دسترس می باشد") || text.contains("در دسترس می‌باشد")

        if (isMciMissed || isIrancellMissed || isAvailable) {
            processSms(context, text, isAvailable)
        }
    }

    private fun processSms(context: Context, text: String, isAvailable: Boolean) {
        // ۱. الگوی پیامک «در دسترس بودن شماره» (همراه اول و ایرانسل):
        // "مشترک گرامی، شماره همراه 09121984453 اکنون در دسترس می باشد."
        if (isAvailable) {
            val availPattern = Pattern.compile(
                "(?:شماره\\s+همراه|شماره)\\s+(?:\\+98|0)?(9\\d{9})\\s+اکنون\\s+در\\s+دسترس",
                Pattern.CASE_INSENSITIVE
            )
            val availMatcher = availPattern.matcher(text)
            if (availMatcher.find()) {
                val fullPhone = "0${availMatcher.group(1)}"
                val name = getContactName(context, fullPhone) ?: fullPhone
                showNotification(
                    context = context,
                    title = "در دسترس شد: $name",
                    number = fullPhone,
                    details = "شماره $fullPhone اکنون در دسترس است"
                )
                return
            }
        }

        // ۲. الگوی تماس بی‌پاسخ همراه اول:
        val mciPattern = Pattern.compile(
            "(\\d+)\\s+تماس بی\\s*پاسخ از\\s+(?:\\+98|0)?(9\\d{9})\\s+در زمان\\s+([\\d\\-\\/\\:\\s]+?)\\s+دارید",
            Pattern.CASE_INSENSITIVE
        )
        val mciMatcher = mciPattern.matcher(text)

        if (mciMatcher.find()) {
            val count = mciMatcher.group(1) ?: "1"
            val fullPhone = "0${mciMatcher.group(2)}"
            val time = mciMatcher.group(3)?.trim() ?: ""
            val name = getContactName(context, fullPhone) ?: fullPhone
            val details = if (time.isNotEmpty()) "$count تماس بی‌پاسخ | زمان: $time" else "$count تماس بی‌پاسخ"
            showNotification(context, "تماس بی‌پاسخ: $name", fullPhone, details)
            return
        }

        // ۳. الگوی تماس بی‌پاسخ ایرانسل:
        val irancellPattern = Pattern.compile(
            "در\\s+تار[یي]خ\\s+([\\d\\/\\-\\:\\s]+?)\\s+تعداد\\s+(\\d+)\\s+تماس\\s+از\\s+(?:\\+98|0)?(9\\d{9})",
            Pattern.CASE_INSENSITIVE
        )
        val irancellMatcher = irancellPattern.matcher(text)

        if (irancellMatcher.find()) {
            val time = irancellMatcher.group(1)?.trim() ?: ""
            val count = irancellMatcher.group(2) ?: "1"
            val fullPhone = "0${irancellMatcher.group(3)}"
            val name = getContactName(context, fullPhone) ?: fullPhone
            val details = if (time.isNotEmpty()) "$count تماس بی‌پاسخ | زمان: $time" else "$count تماس بی‌پاسخ"
            showNotification(context, "تماس بی‌پاسخ: $name", fullPhone, details)
            return
        }

        // ۴. حالت رزرو (Fallback): استخراج شماره موبایل در صورت تغییر جزئی متن اپراتور
        val fallback = Pattern.compile("(?:\\+98|0)?(9\\d{9})").matcher(text)
        if (fallback.find()) {
            val fullPhone = "0${fallback.group(1)}"
            val name = getContactName(context, fullPhone) ?: fullPhone
            val title = if (isAvailable) "در دسترس شد: $name" else "تماس بی‌پاسخ: $name"
            showNotification(context, title, fullPhone, "شماره: $fullPhone")
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )
        var contactName: String? = null
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (index != -1) contactName = it.getString(index)
            }
        }
        return contactName
    }

    private fun showNotification(
        context: Context,
        title: String,
        number: String,
        details: String
    ) {
        val channelId = "missed_call_sms_v2"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "هشدار تماس و در دسترس بودن",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val notificationId = System.currentTimeMillis().toInt()
        val dialPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            dialIntent,
            pendingIntentFlags
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\nشماره: $number (برای تماس لمس کنید)"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(dialPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "تماس", dialPendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(notificationId, notif)
    }
}
