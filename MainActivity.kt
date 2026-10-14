package com.example.x3flight

import android.Manifest
import android.app.Activity
import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.*

class MainActivity: Activity() {
    private lateinit var status: TextView
    private var lastStart = false
    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { status.text=intent?.getStringExtra(CameraService.EXTRA_MESSAGE) ?: "" }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(36,48,36,20) }
        status=TextView(this).apply { text="X3 Flight Remote — экспериментальная версия"; textSize=19f }
        layout.addView(status)
        fun add(label:String, action:String) { layout.addView(Button(this).apply { text=label; textSize=20f; minHeight=110; setOnClickListener { send(action) } },LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)) }
        add("ПОДКЛЮЧИТЬ X3",CameraService.ACTION_CONNECT)
        add("● НАЧАТЬ ЗАПИСЬ",CameraService.ACTION_START)
        add("■ ОСТАНОВИТЬ ЗАПИСЬ",CameraService.ACTION_STOP)
        add("ЗАПРОСИТЬ СТАТУС",CameraService.ACTION_STATUS)
        add("ОТКЛЮЧИТЬ",CameraService.ACTION_DISCONNECT)
        layout.addView(TextView(this).apply { text="Кнопки громкости при открытом приложении: + запись, − стоп. Пока ответы показаны в HEX; подтверждение состояния и батарея будут добавлены после проверки на X3." })
        setContentView(ScrollView(this).apply { addView(layout) })
        if(Build.VERSION.SDK_INT>=31) {
            val permissions=mutableListOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)
            if(Build.VERSION.SDK_INT>=33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(permissions.toTypedArray(),42)
        }
    }
    private fun send(action:String) { val i=Intent(this,CameraService::class.java).setAction(action); startForegroundService(i) }
    override fun onKeyDown(keyCode:Int,event:KeyEvent?):Boolean = when(keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> { send(CameraService.ACTION_START); true }
        KeyEvent.KEYCODE_VOLUME_DOWN -> { send(CameraService.ACTION_STOP); true }
        else -> super.onKeyDown(keyCode,event)
    }
    override fun onStart() { super.onStart(); val filter=IntentFilter(CameraService.ACTION_UPDATE); if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,filter,RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(receiver,filter) }
    override fun onStop() { unregisterReceiver(receiver); super.onStop() }
}
