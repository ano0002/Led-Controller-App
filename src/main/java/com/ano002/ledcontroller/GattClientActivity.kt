package com.ano002.ledcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothDevice.TRANSPORT_LE
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.children
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import me.tankery.lib.circularseekbar.CircularSeekBar
import java.io.IOException
import java.lang.Float.max
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import kotlin.math.log10
import kotlin.math.sqrt


class GattClientActivity : AppCompatActivity(){

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter

    }

    private lateinit var gatt: BluetoothGatt

    private val radio_buttons = arrayOf("rainbow","statique","dual_wave","breath","dual","rainbow_wave",
        "blink","cycle","star","static_double","gradient","glitch","snake","fire","waterfall","strobe",
        "strobe_colors","center_music_mix","start_music_mix","end_music_mix","fireworks","wave","zoom",
        "zoom_rainbow","disappearing","glitter","reveal","reveal_rainbow","starry_sky","ripple")
    private val radio_buttons_texts = arrayOf("Rainbow","Static","Dual Wave","Breath","Dual","Rainbow Wave",
        "Blink","Cycle","Star","Double Static","Gradient","Glitch","Snake","Fire","Waterfall","Strobe",
        "Strobe Colors","Center Music Mix","Start Music Mix","End Music Mix","Fireworks","Wave","Zoom",
        "Zoom Rainbow","Disappearing","Glitter","Reveal","Reveal Rainbow","Starry Sky","Ripple")


    private var mRecorder: MediaRecorder? = MediaRecorder()

    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        val bundle: Bundle? = intent.extras
        showToast(this, bundle?.getString("name") ?: "No device name")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gatt_client)

        val radioGroup = findViewById<RadioGroup>(R.id.modes)
        for (i in radio_buttons.indices) {
            val rdbtn = RadioButton(this)
            rdbtn.id = View.generateViewId()
            rdbtn.setTag(radio_buttons[i])
            rdbtn.text = radio_buttons_texts[i]
            rdbtn.setTextColor(ContextCompat.getColorStateList(this, R.color.white));
            rdbtn.buttonTintList = ContextCompat.getColorStateList(this, R.color.white);
            radioGroup.addView(rdbtn)
        }

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(bundle?.getString("address"))
        gatt = device.connectGatt(this, true, gattCallback, TRANSPORT_LE)


        val editsearch =  findViewById<SearchView>(R.id.search);
        editsearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val radioGroup = findViewById<RadioGroup>(R.id.modes)
                radioGroup.children.forEach {
                    if (it is RadioButton) {
                        if (it.text.toString().toLowerCase().contains(newText.toString().toLowerCase())) {
                            it.visibility = View.VISIBLE
                        } else {
                            it.visibility = View.GONE
                        }
                    }
                }
                return false
            }
        })

        val micCheckBox = findViewById<CheckBox>(R.id.mic)
        micCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked){
                var permission = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO)

                if (permission != PackageManager.PERMISSION_GRANTED) {
                    Log.i("Test", "Permission to record denied")
                    makeRequest()
                }
                else{
                    Log.i("Test", "Permission to record granted")
                }
                startRecording()
            }
            else{
                stopRecording()
            }
        }


    }

    private var audioRecord: AudioRecord? = null

    private var recording = false
    private var recordingThread: Thread? = null
    private var maxVolume = 1.0

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize
        )

        val audioData = ShortArray(minBufferSize)

        audioRecord?.startRecording()

        // Start recording process and handle the audio data

        recording = true

        recordingThread = Thread {
            audioRecord?.startRecording()

            while (recording) {
                val bytesRead = audioRecord?.read(audioData, 0, minBufferSize)
                if (bytesRead != null && bytesRead > 0) {
                    val volume = calculateVolume(audioData, bytesRead)
                    Log.i("Test", "Volume: $volume")
                    // Use the volume value as needed
                    if (gatt!=null){
                        val characteristic = gatt.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")).getCharacteristic(UUID.fromString("6e400008-b5a3-f393-e0a9-e50e24dcca9e"))

                        val data = ByteBuffer.allocate(Int.SIZE_BYTES).putInt((volume*10000).toInt()).array()
                        characteristic.setValue(data)
                        gatt.writeCharacteristic(characteristic)

                    }
                }
            }

            audioRecord?.stop()
            audioRecord?.release()
        }

        recordingThread?.start()
    }

    private fun calculateVolume(audioData: ShortArray, bytesRead: Int): Double {
        var sum = 0.0
        for (i in 0 until bytesRead) {
            sum += audioData[i].toDouble() * audioData[i].toDouble()
        }
        val amplitude = sum / bytesRead
        val volume = sqrt(amplitude)
        if (volume > maxVolume) {
            maxVolume = volume
        }
        return volume/maxVolume
    }
    @SuppressLint("MissingPermission")
    private fun stopRecording() {
        recording = false
        try {
            recordingThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        audioRecord = null
        recordingThread = null
        if(gatt != null){
            val characteristic = gatt.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")).getCharacteristic(UUID.fromString("6e400008-b5a3-f393-e0a9-e50e24dcca9e"))
            val toWrite = "-1"
            val data = toWrite.toByteArray(Charset.defaultCharset())
            characteristic.setValue(data)
            gatt.writeCharacteristic(characteristic)
        }
    }


    private fun makeRequest() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.WRITE_EXTERNAL_STORAGE),
            1)
    }
    override fun onRequestPermissionsResult(requestCode: Int,
                                             permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            requestCode -> {

                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user")
                } else {
                    Log.i(TAG, "Permission has been granted by user")
                }
            }
        }
    }
    @Deprecated("")
    @SuppressLint("MissingPermission")
    override fun onBackPressed() {
        gatt.close()
        finish()
    }


    private val gattCallback = object : BluetoothGattCallback() {
        var publicGatt: BluetoothGatt? = null
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    publicGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        gatt.discoverServices()
                    }

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                    finishActivity(1)
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
                finishActivity(1)
            }
        }
        private fun BluetoothGatt.printGattTable() {
            if (services.isEmpty()) {
                Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
                return
            }
            services.forEach { service ->
                val characteristicsTable = service.characteristics.joinToString(
                    separator = "\n|--",
                    prefix = "|--"
                ) { it.uuid.toString() }
                Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
                )
            }
        }

        fun BluetoothGattCharacteristic.isReadable(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

        fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
            containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

        fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
            return properties and property != 0
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here
                setViewsValues()
                addListeners()
                // Make the buttons visible
                runOnUiThread {
                    findViewById<View>(R.id.layout).visibility = View.VISIBLE
                }
            }
        }
        @SuppressLint("MissingPermission")
        fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
            val writeType = when {
                characteristic.isWritableWithoutResponse() -> {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                else -> error("Characteristic ${characteristic.uuid} cannot be written to")
            }

            publicGatt?.let { gatt ->
                characteristic.writeType = writeType
                characteristic.value = payload
                gatt.writeCharacteristic(characteristic)
            } ?: error("Not connected to a BLE device!")
        }

        @SuppressLint("MissingPermission")
        fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
            if (characteristic.isReadable())
                publicGatt?.readCharacteristic(characteristic)
        }



        private fun setViewsValues(){
            publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                ?.getCharacteristic(UUID.fromString("6e400007-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                    readCharacteristic(it)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        val strUuid = uuid.toString()
                        val value = String(value, Charset.forName("UTF-8"))
                        Log.w("BluetoothGattCallback", "Read characteristic $strUuid: $value")
                        when  (strUuid ){
                            "6e400007-b5a3-f393-e0a9-e50e24dcca9e" -> {
                                runOnUiThread{
                                    findViewById<ToggleButton>(R.id.toggle).isChecked = value.toInt() == 1
                                }

                                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                                    ?.getCharacteristic(UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                                        readCharacteristic(it)
                                    }
                            }
                            "6e400002-b5a3-f393-e0a9-e50e24dcca9e" -> {
                                runOnUiThread{

                                    val brightnessSlider = findViewById<CircularSeekBar>(R.id.brightnessSlider)
                                    brightnessSlider.progress = value.toFloat()
                                }
                                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                                    ?.getCharacteristic(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                                        readCharacteristic(it)
                                    }
                            }
                            "6e400003-b5a3-f393-e0a9-e50e24dcca9e" -> {
                                runOnUiThread{
                                    val speedSlider = findViewById<CircularSeekBar>(R.id.speedSlider)
                                    speedSlider.progress = value.toFloat()
                                }
                                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                                    ?.getCharacteristic(UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                                        readCharacteristic(it)
                                    }
                            }
                            "6e400004-b5a3-f393-e0a9-e50e24dcca9e" -> {
                                runOnUiThread{
                                    val modes = findViewById<RadioGroup>(R.id.modes)
                                    for (i in 0 until modes.childCount) {
                                        val rb = modes.getChildAt(i) as RadioButton
                                        if (rb.getTag() == value) {
                                            rb.isChecked = true
                                        }
                                    }
                                }
                                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                                    ?.getCharacteristic(UUID.fromString("6e400005-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                                        readCharacteristic(it)
                                    }
                            }
                            "6e400005-b5a3-f393-e0a9-e50e24dcca9e" -> {
                                runOnUiThread{
                                    val colorPicker = findViewById<ColorPickerView>(R.id.color1)
                                    colorPicker.setInitialColor(value.toInt(16))
                                }
                                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                                    ?.getCharacteristic(UUID.fromString("6e400006-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                                        readCharacteristic(it)
                                    }
                            }
                            "6e400006-b5a3-f393-e0a9-e50e24dcca9e" -> {
                                runOnUiThread{
                                    val colorPicker = findViewById<ColorPickerView>(R.id.color2)
                                    colorPicker.setInitialColor(value.toInt(16))
                                }
                            }

                            else -> {
                                Log.e("BluetoothGattCallback", "Read characteristic $strUuid:\n$value")
                            }
                        }
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun addListeners(){
            val toggle: ToggleButton = findViewById(R.id.toggle)
            toggle.setOnCheckedChangeListener { _, isChecked ->
                if(isChecked) {
                    publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                        ?.getCharacteristic(UUID.fromString("6e400007-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                               writeCharacteristic(it, "1".toByteArray())
                            }
                    Log.w("BluetoothGattCallback", "Turned on")
                } else {
                    publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                        ?.getCharacteristic(UUID.fromString("6e400007-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                            writeCharacteristic(it, "0".toByteArray())
                        }}
                    Log.w("BluetoothGattCallback", "Turned off")
            }
            val brightnessSlider = findViewById<CircularSeekBar>(R.id.brightnessSlider)
            val speedSlider = findViewById<CircularSeekBar>(R.id.speedSlider)

            brightnessSlider.setOnSeekBarChangeListener(object : CircularSeekBar.OnCircularSeekBarChangeListener {
                override fun onProgressChanged(
                    circularSeekBar: CircularSeekBar?,
                    progress: Float,
                    b: Boolean
                ) {
                    publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                        ?.getCharacteristic(UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                            writeCharacteristic(it, max(progress,1f).toString().toByteArray())
                        }
                    Log.w("BluetoothGattCallback", "Brightness changed to $progress")
                }
                override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {}
            })

            speedSlider.setOnSeekBarChangeListener(object : CircularSeekBar.OnCircularSeekBarChangeListener {
                override fun onProgressChanged(seekBar: CircularSeekBar?, progress: Float, b: Boolean) {
                    publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                        ?.getCharacteristic(UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                            writeCharacteristic(it, max(progress,1f).toString().toByteArray())
                        }
                    Log.w("BluetoothGattCallback", "Speed changed to $progress")
                }
                override fun onStartTrackingTouch(seekBar: CircularSeekBar?) {}
                override fun onStopTrackingTouch(seekBar: CircularSeekBar?) {}
            })

            val modes = findViewById<RadioGroup>(R.id.modes)

            modes.setOnCheckedChangeListener(RadioGroup.OnCheckedChangeListener { _, checkedId ->
                val radio = findViewById<RadioButton>(checkedId)
                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                    ?.getCharacteristic(UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                        val value = radio.getTag() as String
                        writeCharacteristic(it, value.toByteArray())
                    }
                Log.w("BluetoothGattCallback", "Mode changed to ${radio.text}")
            })

            val color1 = findViewById<ColorPickerView>(R.id.color1)

            color1.setColorListener(ColorEnvelopeListener { envelope, _ ->
                val color = envelope.color
                val hex = String.format("%02x%02x%02x", color.red, color.green, color.blue)
                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                    ?.getCharacteristic(UUID.fromString("6e400005-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                        writeCharacteristic(it, hex.toByteArray())
                    }
                Log.w("BluetoothGattCallback", "Color 1 changed to $hex")
            })
            val color2 = findViewById<ColorPickerView>(R.id.color2)

            color2.setColorListener(ColorEnvelopeListener { envelope, _ ->
                val color = envelope.color
                val hex = String.format("%02x%02x%02x", color.red, color.green, color.blue)
                publicGatt?.getService(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"))
                    ?.getCharacteristic(UUID.fromString("6e400006-b5a3-f393-e0a9-e50e24dcca9e"))?.let {
                        writeCharacteristic(it, hex.toByteArray())
                    }
                Log.w("BluetoothGattCallback", "Color 2 changed to $hex")
            })

        }
    }

    private fun showToast(context: Context = applicationContext, message: String, duration: Int = Toast.LENGTH_SHORT) {
        if (!message.contains("null")) {
            Toast.makeText(context, message, duration).show()
        }
    }
}
