package me.insiro.prml_ra.noiseclassifier

import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {
    private var host = "";
    private var port = 3389;
    private var inComingValue: String = ""
    private var tempString: String = ""

    //Socket
    private var socket: Socket? = null
    private var isRunning: Boolean = false
    private var inputFromServer: BufferedReader? = null
    private var outToServer: BufferedWriter? = null


    //Audio Variable
    private var record: AudioRecord? = null
    private var SAMPLE_RATE: Int = 22050
    private var RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO
    private var AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize: Int =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, RECORDER_CHANNELS, AUDIO_ENCODING)

    //Main layout Components
    private var delayTimeTextView: TextView? = null
    private var areaValueTextView: TextView? = null

    //Dialog layout Components
    private var accessDialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        delayTimeTextView = findViewById<TextView>(R.id.DelayTime)
        areaValueTextView = findViewById<TextView>(R.id.result_tv)

        getPermission()

        val detailViewer: LinearLayout = findViewById<LinearLayout>(R.id.detailView)
        val resultView: LinearLayout = findViewById<LinearLayout>(R.id.resultView)
        val recordTrigger: Button = findViewById<Button>(R.id.recordTriggerBTN)
        val accessDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.host_info_layout, null)
        val hostEditText: EditText = dialogView.findViewById<EditText>(R.id.editTxt_ip)
        val portEditText: EditText = dialogView.findViewById<EditText>(R.id.editTxt_Port)
        accessDialogBuilder.setTitle("Information for Access server")
        accessDialogBuilder.setView(dialogView)
        accessDialogBuilder.setPositiveButton("Access") { dialog, id ->
            host = hostEditText.text.toString()
            var pstring = portEditText.text.toString()
            if (pstring.toIntOrNull() == null)
                return@setPositiveButton
            port = portEditText.text.toString().toInt()
            if (host == "") {
                Toast.makeText(applicationContext, "need To insert IP", Toast.LENGTH_SHORT)
            } else {
                try {
                    runOnUiThread { recordTriggerBTN.text = "Stop" }
                    record = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE,
                            RECORDER_CHANNELS,
                            AUDIO_ENCODING,
                            bufferSize
                    )
                    ThreadWriter().start()
                } catch (e: Exception) {
                    turnOffRunning()
                    Log.d("Exception", e.toString())
                }
            }
        }
        accessDialogBuilder.setNegativeButton("Cancel", null)
        accessDialog = accessDialogBuilder.create()
        recordTrigger.setOnClickListener(BtnOnClickListener())
        detailViewer.setOnClickListener(BtnOnClickListener())
        resultView.setOnClickListener(BtnOnClickListener())
    }

    private fun turnOffRunning() {
        Log.d("socketOff", "Closed")
        runOnUiThread { recordTriggerBTN.text = "Record" }
        isRunning = false
        try {
            if (record != null) {
                record!!.stop()
                record!!.release()
            }
        } catch (e: Exception) {
            Log.w("error!!!!", e.toString())
        } finally {
            if (socket != null)
                socket!!.close()
            socket = null
        }
    }

    inner class BtnOnClickListener : View.OnClickListener {
        override fun onClick(v: View?) {
            try {
                when (v?.id) {
                    R.id.recordTriggerBTN -> {
                        if (isRunning) turnOffRunning() else accessDialog!!.show()
                    }
                    R.id.resultView -> {
                        detailView.visibility = View.VISIBLE
                        resultView.visibility = View.GONE
                    }
                    R.id.detailView -> {
                        detailView.visibility = View.GONE
                        resultView.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.w("Error!!!!", e.toString())
            }
        }

    }

    inner class ThreadWriter : Thread() {
        override fun run() {
            try {
                socket = Socket(host, port);
                inputFromServer = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                outToServer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
                isRunning = true
                tempString = inputFromServer!!.readLine()
                runOnUiThread { delayTimeTextView!!.text = tempString }
                var bufferFromMIC: ByteBuffer = ByteBuffer.allocateDirect(bufferSize)
                record!!.startRecording()
                ThreadReader().start()
                while (isRunning && !socket!!.isClosed) {
                    record!!.read(bufferFromMIC, bufferSize)
                    outToServer!!.write(String(bufferFromMIC.array()))

                    outToServer!!.flush()
                }
                turnOffRunning()
            } catch (e: Exception) {
                Log.w("Error!!! ", e.toString())
                turnOffRunning()
            }
        }
    }

    inner class ThreadReader : Thread() {
        override fun run() {
            try {
                Log.d("check123", "Reading From Server")
                while (isRunning && !socket!!.isClosed) {
                    inComingValue = inputFromServer!!.readLine()
                    runOnUiThread(areaViewUpdater())
                    //change TextView
                }
                turnOffRunning()
            } catch (e: Exception) {
                turnOffRunning()
                Log.w("Exception", e.toString())
            }
        }
    }

    inner class areaViewUpdater : Runnable {
        override fun run() {
            areaValueTextView!!.text = inComingValue
        }
    }


    override fun onDestroy() {
        turnOffRunning()
        super.onDestroy()
    }

    private fun getPermission() {
        val permissionList = arrayOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        for (permission in permissionList) {
            if (ContextCompat.checkSelfPermission(
                            this,
                            permission
                    ) == PackageManager.PERMISSION_DENIED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
            }
        }
    }


}


