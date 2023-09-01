package com.ano002.ledcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val allDevices = findViewById<CheckBox>(R.id.alldevices)
        allDevices.setOnCheckedChangeListener { _, _ ->
            if (isScanning) {
                stopBleScan()
            }
            startBleScan()
        }
        findViewById<Button>(R.id.scan_button).setOnClickListener {
            if (isScanning) {
                stopBleScan()
            }
            startBleScan()
        }
        startBleScan()


    }

    private val context: Context by lazy {
        applicationContext
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    override fun onResume() {
        super.onResume()
        startBleScan()
    }

    private fun promptEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)


        val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                print("Bluetooth enabled")
                startBleScan()
            }
        }

        resultLauncher.launch(enableBtIntent)
    }

    private val scanResults = mutableListOf<ScanResult>()

    private var isScanning = false

    private fun startBleScan() {
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }else {
            scanResults.clear()
            findViewById<LinearLayout>(R.id.devices).removeAllViews()

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                    1
                )
            }
            bleScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
            findViewById<Button>(R.id.scan_button).text = "Restart Scan"
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    1
                )
                return
            }
            val allDevices = findViewById<CheckBox>(R.id.alldevices).isChecked
            if (!allDevices){
                if (result.device.name != "LED"){
                    return
                }
            }
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            val devicesList = findViewById<LinearLayout>(R.id.devices)
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                val radio = devicesList.getChildAt(indexQuery) as RadioButton
                with(result.device) {
                    radio.text = "$address"
                }
            } else {
                with(result.device) {
                    Log.i(
                        "ScanCallback",
                        "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address"
                    )
                    scanResults.add(result)
                    val radio = RadioButton(this@MainActivity)
                    val name = "$address"
                    radio.text = name
                    radio.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            if (isScanning) {
                                stopBleScan()
                            }
                            val gattClientIntent = Intent(context, GattClientActivity::class.java)
                            gattClientIntent.putExtra("name", name)
                            gattClientIntent.putExtra("address", address)
                            startActivity(gattClientIntent)

                        }
                    }
                    radio.setTextColor(ContextCompat.getColorStateList(context, R.color.white));
                    radio.buttonTintList = ContextCompat.getColorStateList(context, R.color.white);
                    devicesList.addView(radio)
                }
            }
        }


        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }
}