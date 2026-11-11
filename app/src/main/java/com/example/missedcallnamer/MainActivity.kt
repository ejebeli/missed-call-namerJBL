package com.example.missedcallnamer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tv = TextView(this).apply {
            text = "برنامه فعال است.\nمجوزهای پیامک و مخاطبین را تأیید کنید."
            textSize = 18f
            setPadding(40, 40, 40, 40)
        }
        setContentView(tv)

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed, 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101) {
            val denied = permissions.indices.any {
                grantResults[it] != PackageManager.PERMISSION_GRANTED
            }

            if (denied) {
                Toast.makeText(
                    this,
                    "برای عملکرد درست برنامه، مجوزهای لازم را فعال کنید.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "مجوزها فعال شدند.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
