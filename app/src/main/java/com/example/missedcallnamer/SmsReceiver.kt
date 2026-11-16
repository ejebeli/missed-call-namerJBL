package com.ehsanjebeli.missedcallnamer

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

        // تشخیص پیامک تماس از دست رفته برای همراه اول یا ایرانسل
        val isMci = text.contains("تماس بی پاسخ") || text.contains("تماس بی‌پاسخ")
        val isIrancell = (text.contains("تعداد") && text.contains("تماس از"))

        if (isMci || isIrancell) {
            processMissedCall(context, text)
        }
    }

    private fun processMissedCall(context: Context, text: String) {
        // ۱. الگوی همراه اول:
        // "3 تماس بی پاسخ از 09204193208 در زمان 24-07-1405 13:02 دارید"
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
            showNotification(context, name, fullPhone, count, time)
            return
        }

        // ۲. الگوی ایرانسل:
        // "شما در تاريخ 25/08/1405 21:04 تعداد 1 تماس از 09127309397 داشته ايد"
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
            showNotification(context, name, fullPhone, count, time)
            return
        }

        // ۳. حالت رزرو (Fallback): اگر ساختار جمله کمی تغییر کرد، شماره موبایل را پیدا کند
        val fallback = Pattern.compile("(?:\\+98|0)?(9\\d{9})").matcher(text)
        if (fallback.find()) {
            val fullPhone = "0${fallback.group(1)}"
            val name = getContactName(context, fullPhone) ?: fullPhone
            showNotification(context, name, fullPhone, "1", "")
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
        name: String,
        number: String,
        count: String,
        time: String
    ) {
        val channelId = "missed_call_sms"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تماس‌های بی‌پاسخ",
                NotificationManager.IMPORTANCE_HIGH
            )
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

        val details = if (time.isNotEmpty()) "$count تماس بی‌پاسخ | زمان: $time" else "$count تماس بی‌پاسخ"

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle("تماس بی‌پاسخ: $name")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\nشماره: $number (برای تماس لمس کنید)"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(dialPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "تماس مجدد", dialPendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(notificationId, notif)
    }
}
