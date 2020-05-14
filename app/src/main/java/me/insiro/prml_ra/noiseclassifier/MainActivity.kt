package me.insiro.prml_ra.noiseclassifier

import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.squti.androidwaverecorder.WaveRecorder
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.net.Socket
import java.util.*
import kotlin.concurrent.timer
import org.json.JSONObject
import org.json.JSONStringer


class MainActivity : AppCompatActivity() {
    private val filePath: String =
        Environment.getExternalStorageDirectory().absolutePath + "/recordResult.wav"
    private val jsonPath: String =
        Environment.getExternalStorageDirectory().absolutePath + "/temp.json"
    private val waveRecorder = WaveRecorder(filePath)
    private val writer: Thread = SocketWriter()
    private var timer: Timer? = null
    private val btnOnClickListener = BtnOnClickListener()
    private val delayTime: Long = 6000
    private var host = "";
    private var port = 3389;
    private var inComingValue: String = ""

    //Socket
    private var socket: Socket? = null
    private var isRunning: Boolean = false
    private lateinit var inputFromServer: BufferedReader
    private lateinit var outToServer: BufferedWriter
    private lateinit var outStream: DataOutputStream
    private lateinit var jsonInputStream: ObjectInputStream

    private lateinit var inputStream: InputStream
    private lateinit var json: JSONObject
    private lateinit var jsonString: String
    private lateinit var outputStreams: OutputStream


    //region Audio Variable
    private val SAMPLE_RATE: Int = 22050
    private val RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
    //endregion

    //Main layout Components
    private lateinit var areaValueTextView: TextView
    private lateinit var delayTimeTextView: TextView

    //Dialog layout Components
    private lateinit var accessDialog: AlertDialog


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //region mapping Components
        areaValueTextView = findViewById<TextView>(R.id.result_tv)
        delayTimeTextView = findViewById<TextView>(R.id.DelayTime)
        val detailViewer: LinearLayout = findViewById<LinearLayout>(R.id.detailView)
        val resultView: LinearLayout = findViewById<LinearLayout>(R.id.resultView)
        val recordTrigger: Button = findViewById<Button>(R.id.recordTriggerBTN)
        val accessDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.host_info_layout, null)
        val hostEditText: EditText = dialogView.findViewById<EditText>(R.id.editTxt_ip)
        val portEditText: EditText = dialogView.findViewById<EditText>(R.id.editTxt_Port)
        recordTrigger.setOnClickListener(btnOnClickListener)
        detailViewer.setOnClickListener(btnOnClickListener)
        resultView.setOnClickListener(btnOnClickListener)
        //endregion

        //region waveRecorder Config
        waveRecorder.waveConfig.sampleRate = SAMPLE_RATE;
        waveRecorder.waveConfig.channels = RECORDER_CHANNELS;
        waveRecorder.waveConfig.audioEncoding = AUDIO_ENCODING;
        waveRecorder.onAmplitudeListener = { Log.i("TAG", "Amplitude : $it") }
        //endregion

        getPermission()
        //region accessDialog
        accessDialogBuilder.setTitle("Information for Access server")
        accessDialogBuilder.setView(dialogView)
        accessDialogBuilder.setPositiveButton("Access") { dialog, id ->
            host = hostEditText.text.toString()
            var pstring = portEditText.text.toString()
            if (pstring.toIntOrNull() == null) return@setPositiveButton
            port = pstring.toInt()
            if (host == "") {
                Toast.makeText(applicationContext, "need To insert IP", Toast.LENGTH_SHORT)
            } else {
                writer.start()
            }
        }
        accessDialogBuilder.setNegativeButton("Cancel", null)
        accessDialog = accessDialogBuilder.create()
        //endregion

    }

    private fun turnOffRunning(e: Exception? = null) {
        e?.printStackTrace()
        Log.d("state", "stop")
        runOnUiThread { recordTriggerBTN.text = "Record" }
        timer?.cancel()
        isRunning = false
        try {
            timer?.cancel()
            if (socket != null) {
                socket?.close()
                socket = null
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            waveRecorder.stopRecording()
        }
    }

    inner class BtnOnClickListener : View.OnClickListener {
        override fun onClick(v: View?) {
            try {
                when (v?.id) {
                    R.id.recordTriggerBTN -> {
//                        Log.d("runningState",isRunning.toString())
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

    inner class SocketWriter : Thread() {
        private var tempString: String = ""
        override fun run() {
            isRunning = true
            try {
                //region socket init
                socket = Socket(host, port);
                outStream = DataOutputStream(socket!!.getOutputStream())
                inputFromServer = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                tempString = inputFromServer.readLine()
                runOnUiThread {
                    recordTriggerBTN.text = "Stop";
                    delayTimeTextView.text = tempString;
                }
                //endregion
                Log.d("Record", "Start")
                var xString:String = "1234"

                outStream.writeUTF(xString)
                outStream.flush()
                /**
                waveRecorder.startRecording()
                runOnUiThread {
                    Handler().postDelayed({
                        timer = timer(period = delayTime) {
                            Log.d("asst", "stop")
                            waveRecorder.stopRecording()
                            if (!isRunning) {
                                turnOffRunning()
                                return@timer
                            }
                            fileSender()
                            detailUpdater()
                            if (!isRunning) {
                                turnOffRunning()
                                return@timer
                            }
                            Log.d("Record", "Start")
                            waveRecorder.startRecording()
                        }
                    }, delayTime)
                }
**/
            } catch (e: Exception) {
                turnOffRunning(e)
            }
        }
    }

    fun detailUpdater() {
        var inputString: String
        var ReadedString: String = ""
        try {

            inputString = inputFromServer.readLine()
            while (inputString.equals("endData")) {
                ReadedString += inputString
            }
            Log.d("ReadData", ReadedString)
        } catch (e: Exception) {
            turnOffRunning(e)
        }
    }

    fun fileSender() {
        try {
            var fileInputStream: FileInputStream = FileInputStream(filePath)
            var buffer: ByteArray = ByteArray(1024)
            var readBytes: Int
            var len: Int = 1
            var lines: Int = 0
            var lineString:String
            while (len > 0) {
                len = fileInputStream.read(buffer)
                lines++
            }
            fileInputStream.close()
            fileInputStream = FileInputStream(filePath)

            lineString = lines.toString()
            Log.d("Lines",lineString)
            outStream.writeUTF(lineString)
            outStream.flush()
            readBytes = fileInputStream.read(buffer)
            /**
            while (readBytes > 0) {
                outStream.write(buffer, 0, readBytes)
                readBytes = fileInputStream.read(buffer)
            }**/
            while(lines>1) {
                lines--
                outStream.write(buffer, 0, readBytes)
                readBytes = fileInputStream.read(buffer)
            }
            outStream.flush()
            fileInputStream.close()
        } catch (e: Exception) {
            turnOffRunning(e)
        }

    }
/**
    fun fileSender() {
        try {
            var fileReader: DataInputStream = DataInputStream(FileInputStream(File(filePath)))
            var buffer: ByteArray = ByteArray(1024)
            var readBytes: Int = fileReader.read(buffer)


            var len: Int = 1
            var lines: Int = 0
            while (len > 0) {
                len = fileReader.read(buffer)
                lines++
            }
            Log.d("Lines",lines.toString())
            fileReader.close()
            fileReader = DataInputStream(FileInputStream(File(filePath)))

            while (readBytes > 0) {
                outStream.write(buffer, 0, readBytes)
                readBytes = fileReader.read(buffer)
            }
            fileReader.close()
        } catch (e: Exception) {
            turnOffRunning(e)
        }
    }
**/
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
                turnOffRunning(e)
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
        //region check permission
        var hasPermission: Boolean = true
        for (permission in permissionList) {
            hasPermission = hasPermission && (ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED)
        }
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this, permissionList, 1)
        }

    }

}


