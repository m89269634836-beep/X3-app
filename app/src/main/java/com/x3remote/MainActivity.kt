package com.x3remote

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val status = TextView(this).apply {
            text = "Insta360 X3 — Not connected"
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val battery = TextView(this).apply {
            text = "Recording: unknown | Battery: unknown"
            gravity = Gravity.CENTER
        }
        val connect = Button(this).apply {
            text = "Connect (coming soon)"
            isEnabled = false
        }
        val record = Button(this).apply {
            text = "Start / Stop (coming soon)"
            isEnabled = false
        }
        layout.addView(status)
        layout.addView(battery)
        layout.addView(connect)
        layout.addView(record)
        setContentView(layout)
    }
}
