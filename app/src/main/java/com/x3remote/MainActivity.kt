package com.x3remote

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {
    private fun id(s: String) = UUID.fromString("0000$s-0000-1000-8000-00805f9b34fb")
    private val manager by lazy { getSystemService(BLUETOOTH_SERVICE) as BluetoothManager }
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var logText: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var scanning = false
    private var sequence = 1
    private fun log(s: String) = runOnUiThread {
        logText.append("\\n$s")
        if (logText.length() > 7000) logText.text = logText.text.takeLast(4500)
    }
    private fun state(s: String) = runOnUiThread { status.text = s }
    private fun allowed() = Build.VERSION.SDK_INT < 31 ||
        (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
         checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        status = TextView(this).apply { text = "X3: disconnected"; textSize = 20f }
        start = Button(this).apply { text = "START recording (test)"; isEnabled = false; setOnClickListener { send(4) } }
        stop = Button(this).apply { text = "STOP recording (test)"; isEnabled = false; setOnClickListener { send(5) } }
        logText = TextView(this).apply { text = "BLE log"; textSize = 12f }
        val connect = Button(this).apply { text = "Find and connect X3"; setOnClickListener { connect() } }
        root.addView(status); root.addView(connect); root.addView(start); root.addView(stop)
        root.addView(ScrollView(this).apply { addView(logText) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 31 && !allowed())
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 11)
    }
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) {
            val name = try { result.device.name ?: result.scanRecord?.deviceName ?: "" } catch (_: SecurityException) { "" }
            if (name.startsWith("X3 ")) {
                log("Found $name")
                stopScan()
                gatt = result.device.connectGatt(this@MainActivity, false, callbacks, BluetoothDevice.TRANSPORT_LE)
            }
        }
        override fun onScanFailed(errorCode: Int) { scanning = false; log("Scan error $errorCode") }
    }
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try { manager.adapter.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: SecurityException) {}
    }
    private fun connect() {
        if (!allowed()) { log("Grant Bluetooth permissions first"); return }
        if (!manager.adapter.isEnabled) { log("Enable Bluetooth first"); return }
        try {
            gatt?.close(); gatt = null; tx = null
            start.isEnabled = false; stop.isEnabled = false
            state("Scanning...")
            scanning = true
            manager.adapter.bluetoothLeScanner.startScan(null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            ui.postDelayed({ if (scanning) { stopScan(); state("X3 not found") } }, 12000)
        } catch (e: SecurityException) { log("Permission error: ${e.message}") }
    }
    private val callbacks = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, code: Int, state: Int) {
            log("Connection state=$state status=$code")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                state("Discovering services")
                try { g.discoverServices() } catch (_: SecurityException) {}
            } else {
                tx = null
                runOnUiThread { start.isEnabled = false; stop.isEnabled = false }
                state("Disconnected ($code)")
                g.close()
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, code: Int) {
            val svc = g.getService(id("be80"))
            tx = svc?.getCharacteristic(id("be81"))
            val rx = svc?.getCharacteristic(id("be82"))
            log("Services code=$code BE81=${tx != null} BE82=${rx != null}")
            if (rx != null) {
                try {
                    g.setCharacteristicNotification(rx, true)
                    val descriptor = rx.getDescriptor(id("2902"))
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= 33)
                            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            g.writeDescriptor(descriptor)
                        }
                    }
                } catch (e: SecurityException) { log("Notify error: ${e.message}") }
            }
            runOnUiThread { start.isEnabled = tx != null; stop.isEnabled = tx != null }
            state(if (tx != null) "X3 connected" else "BE81 not found")
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, data: ByteArray) { receive(data) }
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) receive(c.value ?: byteArrayOf())
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, code: Int) { log("Write status=$code") }
    }
    private fun receive(data: ByteArray) {
        val hex = data.joinToString("-") { "%02X".format(it.toInt() and 255) }
        if (hex != "07-00-00-00-05-00-00") log("RX $hex")
    }
    private fun send(code: Int) {
        val g = gatt ?: return
        val c = tx ?: return
        val packet = byteArrayOf(0,0,0,0,4,0,0,code.toByte(),0,2,sequence.toByte(),0,0,0x80.toByte(),0,0)
        sequence = if (sequence == 254) 1 else sequence + 1
        log("TX code=$code " + packet.joinToString("-") { "%02X".format(it.toInt() and 255) })
        try {
            val result = if (Build.VERSION.SDK_INT >= 33)
                g.writeCharacteristic(c, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            else {
                @Suppress("DEPRECATION")
                c.value = packet
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                if (g.writeCharacteristic(c)) BluetoothStatusCodes.SUCCESS else -1
            }
            log("Queue result=$result (not camera confirmation)")
        } catch (e: SecurityException) { log("Write error: ${e.message}") }
    }
    override fun onDestroy() {
        stopScan()
        try { gatt?.disconnect(); gatt?.close() } catch (_: SecurityException) {}
        super.onDestroy()
    }
}
