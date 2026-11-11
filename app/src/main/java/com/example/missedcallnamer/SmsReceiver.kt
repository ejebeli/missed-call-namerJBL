package com.example.missedcallnamer

import android.app.NotificationChannel
import android.app.NotificationManager
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
        if (text.contains("تماس بی پاسخ") || text.contains("تماس بی‌پاسخ")) {
            processMissedCall(context, text)
        }
    }

    private fun processMissedCall(context: Context, text: String) {
        val pattern = Pattern.compile(
            "(\\d+)\\s+تماس بی\\s*پاسخ از\\s+(?:\\+98|0)?(9\\d{9})\\s+در زمان\\s+([\\d\\-\\:\\s]+?)\\s+دارید",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(text)

        if (matcher.find()) {
            val count = matcher.group(1) ?: "1"
            val fullPhone = "0${matcher.group(2)}"
            val time = matcher.group(3)?.trim() ?: ""
            val name = getContactName(context, fullPhone) ?: fullPhone
            showNotification(context, name, fullPhone, count, time)
        } else {
            val fallback = Pattern.compile("(?:\\+98|0)?(9\\d{9})").matcher(text)
            if (fallback.find()) {
                val fullPhone = "0${fallback.group(1)}"
                val name = getContactName(context, fullPhone) ?: fullPhone
                showNotification(context, name, fullPhone, "1", "")
            }
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
                val idx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (idx != -1) contactName = it.getString(idx)
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

        val details = if (time.isNotEmpty()) "$count تماس بی‌پاسخ • زمان: $time" else "$count تماس بی‌پاسخ"
      val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle("تماس بی‌پاسخ از: $name")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\nشماره تلفن: $number"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
