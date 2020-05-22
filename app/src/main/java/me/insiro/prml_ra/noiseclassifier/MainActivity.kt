@file:Suppress("DEPRECATION")

package me.insiro.prml_ra.noiseclassifier

import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Environment
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


class MainActivity : AppCompatActivity() {
    private val filePath: String =
        Environment.getExternalStorageDirectory().absolutePath + "/recordResult.wav";
    private val waveRecorder = WaveRecorder(filePath);
    private lateinit var writer: Thread;
    private var timer: Timer? = null;
    private val btnOnClickListener = BtnOnClickListener();
    private val delayTime: Long = 6000;
    private var host = "";
    private var port = 3389;

    //region Socket
    private var socket: Socket? = null;
    private var isRunning: Boolean = false;
    private lateinit var inputFromServer: BufferedReader;
    private lateinit var outStream: DataOutputStream;
    //endregion

    //region Audio Variable
    private val SAMPLE_RATE: Int = 22050;
    private val RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO;
    private val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    //endregion

    //Main layout Components
    private lateinit var areaValueTextView: TextView;
    private lateinit var delayTimeTextView: TextView;
    private lateinit var subResultTextView : TextView;

    //Dialog layout Components
    private lateinit var accessDialog: AlertDialog;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //region mapping Components
        areaValueTextView = findViewById<TextView>(R.id.result_tv);
        subResultTextView = findViewById<TextView>(R.id.subResultTxt);
        delayTimeTextView = findViewById<TextView>(R.id.DelayTime);
        val detailViewer: LinearLayout = findViewById<LinearLayout>(R.id.detailView);
        val resultView: LinearLayout = findViewById<LinearLayout>(R.id.resultView);
        val recordTrigger: Button = findViewById<Button>(R.id.recordTriggerBTN);
        val accessDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this);
        val dialogView = layoutInflater.inflate(R.layout.host_info_layout, null);
        val hostEditText: EditText = dialogView.findViewById<EditText>(R.id.editTxt_ip);
        val portEditText: EditText = dialogView.findViewById<EditText>(R.id.editTxt_Port);
        recordTrigger.setOnClickListener(btnOnClickListener);
        detailViewer.setOnClickListener(btnOnClickListener);
        resultView.setOnClickListener(btnOnClickListener);
        //endregion

        //region waveRecorder Config
        waveRecorder.waveConfig.sampleRate = SAMPLE_RATE;
        waveRecorder.waveConfig.channels = RECORDER_CHANNELS;
        waveRecorder.waveConfig.audioEncoding = AUDIO_ENCODING;
        //endregion

        getPermission();
        //region accessDialog
        accessDialogBuilder.setTitle("Information for Access server");
        accessDialogBuilder.setView(dialogView);
        accessDialogBuilder.setPositiveButton("Access") { _, _ ->
            host = hostEditText.text.toString();
            val pstring:String = portEditText.text.toString();
            if (pstring.toIntOrNull() == null) return@setPositiveButton;
            port = pstring.toInt();
            if (host == "") {
                Toast.makeText(applicationContext, "need To insert IP", Toast.LENGTH_SHORT).show();
            } else {
                writer = SocketWriter();
                writer.start();
            }
        }
        accessDialogBuilder.setNegativeButton("Cancel", null);
        accessDialog = accessDialogBuilder.create();
        //endregion

    }

    private fun turnOffRunning(e: Exception? = null) {
        e?.printStackTrace();
        Log.d("state", "stop");
        writer.interrupt();
        timer?.cancel();
        try {
            timer?.cancel();
            if (socket != null) {
                socket?.close();
                socket = null;
            }

        } catch (e: Exception) {
            e.printStackTrace();
        } finally {
            waveRecorder.stopRecording();
            runOnUiThread { recordTriggerBTN.text = "Record" };
            isRunning = false;
        }
    }

    inner class BtnOnClickListener : View.OnClickListener {
        override fun onClick(v: View?) {
            try {
                when (v?.id) {
                    R.id.recordTriggerBTN -> {
                        if (isRunning) turnOffRunning() else accessDialog.show();
                    }
                    R.id.resultView -> {
                        detailView.visibility = View.VISIBLE;
                        resultView.visibility = View.GONE;
                    }
                    R.id.detailView -> {
                        detailView.visibility = View.GONE;
                        resultView.visibility = View.VISIBLE;
                    }
                }
            } catch (e: Exception) {
                Log.w("Error!!!!", e.toString());
            }
        }
    }

    inner class SocketWriter : Thread() {
        private var tempString: String = "";
        override fun run() {
            isRunning = true;
            try {
                //region socket init
                socket = Socket(host, port);
                outStream = DataOutputStream(socket!!.getOutputStream());
                inputFromServer = BufferedReader(InputStreamReader(socket!!.getInputStream()));
                tempString = inputFromServer.readLine();
                runOnUiThread {
                    recordTriggerBTN.text = "Stop";
                    delayTimeTextView.text = tempString;
                    return@runOnUiThread;
                }
                //endregion

                Log.d("Record", "Start");
                //region record
                waveRecorder.startRecording();
                sleep(delayTime);
                while (isRunning || !socket!!.isClosed) {
                    waveRecorder.stopRecording();
                    if (!fileSender())
                        break;
                    detailUpdater();
                    waveRecorder.startRecording();
                    sleep(delayTime);
                }
                //endregion
            } catch (e: InterruptedException) {
                waveRecorder.stopRecording();
                println("interrupt");
                return;
            } catch (e: Exception) {
                turnOffRunning(e);
                return;
            }
            return;
        }
    }

    fun detailUpdater() {
        try {
            val readiedString = inputFromServer.readLine();
            Log.d("ReadData  \n", readiedString);
            val seperater=readiedString.split(' ')
            runOnUiThread{
                areaValueTextView.text =seperater[0]
                subResultTextView.text = seperater.subList(1,seperater.size).joinToString("\n")
            }
        } catch (e: Exception) {
            turnOffRunning(e);
        }
    }

    fun fileSender(): Boolean {
        try {
            val wavFile = File(filePath);
            val dataSize: Int = wavFile.length().toInt();
            val bufferFromFile: BufferedInputStream = BufferedInputStream(FileInputStream(wavFile));
            var data: ByteArray = ByteArray(dataSize);

            bufferFromFile.read(data, 0, data.size);
            outStream.writeUTF(data.size.toString());
            outStream.flush();
            Thread.sleep(500);
            outStream.write(data);
            outStream.flush();
            Log.d("Check", "All Send $dataSize datas");
            Thread.sleep(500);
            bufferFromFile.close();
            return true;
        } catch (e: Exception) {
            turnOffRunning(e);
            return false;
        }
    }



    override fun onDestroy() {
        turnOffRunning();
        super.onDestroy();
    }

    private fun getPermission() {
        val permissionList = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        );
        //region check permission
        var hasPermission: Boolean = true;
        for (permission in permissionList) {
            hasPermission = hasPermission && (ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED);
        }
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this, permissionList, 1);
        }

    }

}


