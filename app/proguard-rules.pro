# جلوگیری از حذف یا تغییر نام گیرنده پیامک توسط R8
-keep class com.example.missedcallnamer.SmsReceiver { *; }
-keep class com.example.missedcallnamer.MainActivity { *; }
