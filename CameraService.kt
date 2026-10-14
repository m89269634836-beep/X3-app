package com.example.x3flight

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import java.util.UUID

/** Experimental BLE transport; not yet verified with physical X3. */
class CameraService : Service() {
    companion object {
        const val ACTION_CONNECT = "x3.CONNECT"
        const val ACTION_START = "x3.START"
        const val ACTION_STOP = "x3.STOP"
        const val ACTION_STATUS = "x3.STATUS"
        const val ACTION_DISCONNECT = "x3.DISCONNECT"
        const val ACTION_UPDATE = "x3.UPDATE"
        const val EXTRA_MESSAGE = "message"
        private fun uuid(s: String) = UUID.fromString("0000${s}-0000-1000-8000-00805f9b34fb")
        val SERVICE = uuid("be80"); val WRITE = uuid("be81"); val NOTIFY = uuid("be82")
        val CCCD = uuid("2902")
    }
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var seq = 1
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val manager by lazy { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private fun update(s: String) { sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).putExtra(EXTRA_MESSAGE,s)); notification(s) }
    private fun notification(s: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("x3", "X3 connection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this,"x3").setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("X3 Flight Remote").setContentText(s).setContentIntent(open).setOngoing(true).build()
        startForeground(1,n)
    }
    override fun onCreate() { super.onCreate(); notification("Готов к подключению") }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_CONNECT -> scan()
            ACTION_START -> command(X3Protocol.START_CAPTURE)
            ACTION_STOP -> command(X3Protocol.STOP_CAPTURE)
            ACTION_STATUS -> command(X3Protocol.GET_CAPTURE_STATUS)
            ACTION_DISCONNECT -> { disconnect(); update("Отключено") }
        }
        return START_STICKY
    }
    @SuppressLint("MissingPermission")
    private fun scan() {
        if (!hasPermissions()) { update("Разрешите Bluetooth в приложении"); return }
        if (scanning || gatt != null) return
        val scanner = manager.adapter?.bluetoothLeScanner ?: run { update("Bluetooth выключен"); return }
        scanning = true; update("Поиск X3…")
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        handler.postDelayed({ if(scanning) { scanner.stopScan(scanCallback); scanning=false; update("X3 не найдена") } }, 10000)
    }
    private fun hasPermissions(): Boolean = Build.VERSION.SDK_INT < 31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)
    private val scanCallback = object: ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: return
            if (!name.startsWith("X3 ") && !name.startsWith("ONE X3 ")) return
            manager.adapter.bluetoothLeScanner?.stopScan(this); scanning=false
            update("Подключение к $name…")
            gatt = result.device.connectGatt(this@CameraService,false,gattCallback,BluetoothDevice.TRANSPORT_LE)
        }
    }
    private val gattCallback = object: BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if(status==BluetoothGatt.GATT_SUCCESS && newState==BluetoothProfile.STATE_CONNECTED) { update("Поиск BLE-сервисов…"); g.discoverServices() }
            else { writeChar=null; g.close(); if(gatt===g) gatt=null; update("Соединение потеряно") }
        }
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if(status!=BluetoothGatt.GATT_SUCCESS) { update("Ошибка обнаружения сервисов"); return }
            val service=g.getService(SERVICE); writeChar=service?.getCharacteristic(WRITE)
            val notify=service?.getCharacteristic(NOTIFY)
            if(writeChar==null || notify==null) { update("BE80/BE81/BE82 не найдены"); return }
            g.setCharacteristicNotification(notify,true)
            val cccd=notify.getDescriptor(CCCD)
            if(cccd!=null) {
                if(Build.VERSION.SDK_INT>=33) g.writeDescriptor(cccd,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else { @Suppress("DEPRECATION") cccd.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; @Suppress("DEPRECATION") g.writeDescriptor(cccd) }
            } else update("Подключено; уведомления недоступны")
        }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { update(if(status==BluetoothGatt.GATT_SUCCESS) "BLE подключён (авторизация не проверена)" else "Ошибка подписки на ответы") }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) { update("Ответ камеры: "+value.joinToString(" ") { "%02X".format(it.toInt() and 255) }.take(70)) }
    }
    @SuppressLint("MissingPermission")
    private fun command(code:Int) {
        val g=gatt; val c=writeChar
        if(g==null || c==null) { update("Нет соединения с X3"); return }
        val bytes=X3Protocol.packet(code,seq); seq=if(seq==254) 1 else seq+1
        val result=if(Build.VERSION.SDK_INT>=33) g.writeCharacteristic(c,bytes,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
          else { @Suppress("DEPRECATION") c.value=bytes; @Suppress("DEPRECATION") c.writeType=BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; @Suppress("DEPRECATION") if(g.writeCharacteristic(c)) BluetoothStatusCodes.SUCCESS else -1 }
        update(if(result==BluetoothStatusCodes.SUCCESS) "Команда $code отправлена; ждём ответ" else "Ошибка отправки $result")
    }
    @SuppressLint("MissingPermission")
    private fun disconnect() { gatt?.disconnect(); gatt?.close(); gatt=null; writeChar=null }
    override fun onDestroy() { disconnect(); super.onDestroy() }
}
