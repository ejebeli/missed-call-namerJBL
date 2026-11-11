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
import java.util.Locale
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

        val text = normalizeSmsText(sb.toString())
        if (looksLikeMissedCall(text)) {
            processMissedCall(context, text)
        }
    }

    private fun looksLikeMissedCall(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return lower.contains("تماس") &&
            (lower.contains("بی پاسخ") || lower.contains("بی‌پاسخ") || lower.contains("بی پاسخ از") || lower.contains("بی‌پاسخ از"))
    }

    private fun normalizeSmsText(raw: String): String {
        return raw
            .replace("\u200C", "") // zero-width non-joiner
            .replace("\u200D", "") // zero-width joiner
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun processMissedCall(context: Context, text: String) {
        val patterns = listOf(
            Pattern.compile(
                "(\\d+)\\s*تماس\\s*بی\\s*پاسخ\\s*از\\s*(?:\\+98|0)?(9\\d{9})\\s*(?:در\\s*زمان\\s*|در\\s*|زمان\\s*)([\\d\\-\\:\\s]+?)(?:\\s+دارید|\\s*$)",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "(\\d+)\\s*تماس\\s*بی\\s*پاسخ\\s*از\\s*(?:\\+98|0)?(9\\d{9})",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                "(?:\\+98|0)?(9\\d{9})",
                Pattern.CASE_INSENSITIVE
            )
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val count = matcher.groupCount() >= 1 && matcher.group(1) != null
                    ? matcher.group(1)
                    : "1"

                val fullPhone = when {
                    matcher.groupCount() >= 2 && matcher.group(2) != null -> "0${matcher.group(2)}"
                    matcher.groupCount() >= 1 && matcher.group(1) != null && matchesPhoneNumber(matcher.group(1)) -> "0${matcher.group(1)}"
                    else -> null
                }

                if (fullPhone != null) {
                    val time = if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                        matcher.group(3)?.trim() ?: ""
                    } else {
                        ""
                    }

                    val name = getContactName(context, fullPhone) ?: fullPhone
                    showNotification(context, name, fullPhone, count, time)
                    return
                }
            }
        }
    }

    private fun matchesPhoneNumber(value: String?): Boolean {
        if (value == null) return false
        return value.matches(Regex("(?:\\+98|0)?9\\d{9}"))
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

        val details = if (time.isNotEmpty()) {
            "$count تماس بی‌پاسخ • زمان: $time"
        } else {
            "$count تماس بی‌پاسخ"
        }

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
