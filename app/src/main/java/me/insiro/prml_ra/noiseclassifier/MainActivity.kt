@file:Suppress("DEPRECATION")

package me.insiro.prml_ra.noiseclassifier

import android.content.DialogInterface
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
    private val delayTime: Long = 6000;
    private val accessPin: Int = 5523;
    private val socWait: Long = 200;
    private var saveMode: Int = 0;
    private val envList: List<String> = listOf(
        "Cancel",
        "Restaurant",
        "market",
        "Bus",
        "Road",
        "Factory",
        "Coffee_shop",
        "SubWay",
        "Office",
        "Bicycle"
    );
    private var isSelectWaiting: Boolean = false;
    private var envValue: Int = 0;

    //region Audio Variable
    private val SAMPLE_RATE: Int = 22050;
    private val filePath: String =
        Environment.getExternalStorageDirectory().absolutePath + "/recordResult.wav";
    private val waveRecorder = WaveRecorder(filePath);
    private val RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO;
    private val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    //endregion

    //region Socket
    private var host = "";
    private var port = 0;
    private var socket: Socket? = null;
    private var isRunning: Boolean = false;
    private lateinit var inputFromServer: BufferedReader;
    private lateinit var outStream: DataOutputStream;
    //endregion

    //region Main layout Components
    private lateinit var areaValueTextView: TextView;
    private lateinit var delayTimeTextView: TextView;
    private lateinit var subResultTextView: TextView;
    //endregion

    private var timer: Timer? = null;
    private val btnOnClickListener = BtnOnClickListener();
    private lateinit var accessDialog: AlertDialog;
    private lateinit var writer: Thread;
    private lateinit var data: ByteArray;

    private lateinit var setListDialogBuilder: AlertDialog.Builder


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

        recordTrigger.setOnClickListener(btnOnClickListener);
        detailViewer.setOnClickListener(btnOnClickListener);
        resultView.setOnClickListener(btnOnClickListener);
        //endregion

        //region accessDialog
        val accessDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this);
        val dialogView = layoutInflater.inflate(R.layout.host_info_layout, null);
        val hostEditText: EditText = dialogView.findViewById(R.id.editTxt_ip) as EditText;
        val portEditText: EditText = dialogView.findViewById(R.id.editTxt_Port) as EditText;
        val saveCheckBox: CheckBox = dialogView.findViewById<CheckBox>(R.id.saveMode);
        accessDialogBuilder.setTitle("Information for Access server");
        accessDialogBuilder.setView(dialogView);
        accessDialogBuilder.setPositiveButton("Access") { _, _ ->
            host = hostEditText.text.toString();
            val pstring: String = portEditText.text.toString();
            if (pstring.toIntOrNull() == null) return@setPositiveButton;
            port = pstring.toInt();
            saveMode = if (saveCheckBox.isChecked) 1 else 0;
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

        //region setListDialog
        setListDialogBuilder = AlertDialog.Builder(this)
        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, R.layout.list_item_layout, R.id.list_item, envList)
        setListDialogBuilder.setCancelable(false)
        setListDialogBuilder.setAdapter(adapter, DialogItemClickListener())
        //endregion

        //region waveRecorder Config
        waveRecorder.waveConfig.sampleRate = SAMPLE_RATE;
        waveRecorder.waveConfig.channels = RECORDER_CHANNELS;
        waveRecorder.waveConfig.audioEncoding = AUDIO_ENCODING;
        //endregion

        getPermission();
    }

    inner class DialogItemClickListener : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface?, which: Int) {
            envValue = which;
            isSelectWaiting = false;
        }

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
            runOnUiThread {
                recordTriggerBTN.text = "Record";
                delayTimeTextView.text = "-";
            };
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
                Log.w("Error!!!!", "$e");
            }
        }
    }

    inner class SocketWriter : Thread() {
        private var tempString: String = "";
        override fun run() {
            isRunning = true;
            try {
                //region socket init
                runOnUiThread {
                    recordTriggerBTN.text = "Stop";
                    delayTimeTextView.text = "waiting for Server";
                    return@runOnUiThread;
                }
                socket = Socket(host, port);
                outStream = DataOutputStream(socket!!.getOutputStream());
                inputFromServer = BufferedReader(InputStreamReader(socket!!.getInputStream()));
                sendStrBySoc("$saveMode");
                sendStrBySoc("$accessPin");
                tempString = inputFromServer.readLine();
                runOnUiThread {
                    delayTimeTextView.text = tempString;
                    return@runOnUiThread;
                }
                if (tempString == "fail") {
                    turnOffRunning()
                    return
                }
                //endregion

                Log.d("Record", "Start");
                //region record
                waveRecorder.startRecording();
                sleep(delayTime);
                while (isRunning || !socket!!.isClosed) {
                    waveRecorder.stopRecording();
                    if (saveMode == 1) {
                        isSelectWaiting = true;
                        runOnUiThread { setListDialogBuilder.show(); }
                        while (isSelectWaiting) sleep(200);
                        sendStrBySoc("$envValue");
                        if (envValue ==0) break;
                    }
                    if (!fileSender()) break;
                    detailUpdater();
                    waveRecorder.startRecording();
                    sleep(delayTime);
                }
                //endregion
            } catch (e: InterruptedException) {
                waveRecorder.stopRecording();
                turnOffRunning()
                println("interrupt");
                return;
            } catch (e: Exception) {
                turnOffRunning(e);
                return;
            }
            turnOffRunning()
            return;
        }

        private fun sendStrBySoc(str: String) {
            outStream.writeUTF(str)
            outStream.flush()
            sleep(socWait);
        }

        private fun fileSender(): Boolean {
            try {
                val wavFile = File(filePath);
                data = ByteArray(wavFile.length().toInt());
                val dataSize: Int = data.size;
                val bufferFromFile: BufferedInputStream =
                    BufferedInputStream(FileInputStream(wavFile));
                bufferFromFile.read(data, 0, dataSize);
                sendStrBySoc("$dataSize")
                outStream.write(data);
                outStream.flush();
                Log.d("Check", "All Send $dataSize data");
                sleep(socWait);
                bufferFromFile.close();
                return true;
            } catch (e: Exception) {
                turnOffRunning(e);
                return false;
            }
        }

        private fun detailUpdater() {
            try {
                val readiedString = inputFromServer.readLine();
                Log.d("ReadData  \n", readiedString);
                val separated = readiedString.split(' ')
                runOnUiThread {
                    areaValueTextView.text = separated[0]
                    subResultTextView.text = separated.subList(1, separated.size).joinToString("\n")
                }
            } catch (e: Exception) {
                turnOffRunning(e);
            }
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



