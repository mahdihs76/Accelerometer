package ir.sharif.accelerometer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.support.wearable.activity.ConfirmationActivity
import android.support.wearable.activity.WearableActivity
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.jakewharton.fliptables.FlipTableConverters
import com.novoda.merlin.Merlin
import kotlinx.android.synthetic.main.activity_main.*
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit


class MainActivity : WearableActivity(), SensorEventListener {
    private lateinit var manager: SensorManager

    private var enableTrack = false
    private val datas = arrayListOf<Data>()
    private var currentX : Float = 0F
    private var currentY : Float = 0F
    private var currentZ : Float = 0F
    private lateinit var executor : ScheduledThreadPoolExecutor
    private lateinit var merlin: Merlin
    private var connected = false

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (!enableTrack) return
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            currentX = event.values[0]
            currentY = event.values[1]
            currentZ = event.values[2]
            Log.i("TAG", "DATA -> {$currentX, $currentY, $currentZ}")
        }
    }

    private fun saveData() {
        val data = Data((datas.size * 10).toLong(), currentX, currentY, currentZ)
        datas.add(data)
    }

    override fun onResume() {
        super.onResume()
        merlin.bind()
    }

    override fun onPause() {
        super.onPause()
        merlin.unbind()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enables Always-on
        setAmbientEnabled()

        merlin = Merlin.Builder().withConnectableCallbacks().withDisconnectableCallbacks().build(this)
        merlin.registerConnectable {
            connected = true
        }
        merlin.registerDisconnectable {
            connected = false
        }


        button.setOnClickListener {
            if (!enableTrack) {
                if (!connected) {
                    val intent = Intent(this, ConfirmationActivity::class.java)
                    intent.putExtra(
                        ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                        ConfirmationActivity.FAILURE_ANIMATION
                    )
                    intent.putExtra(
                        ConfirmationActivity.EXTRA_MESSAGE,
                        "Check Your Network"
                    )
                    startActivity(intent)
                } else {
                    button.text = getString(R.string.stop)
                    button.setBackgroundColor(Color.RED)
                    enableTrack = true
                    executor = ScheduledThreadPoolExecutor(1)
                    executor.scheduleAtFixedRate({
                        runOnUiThread {
                            saveData()
                        }
                    }, 0, 10, TimeUnit.MILLISECONDS)
                }
            } else {
                executor.shutdown()
                enableTrack = false
                sendDataToFireBase()
            }
        }

        manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        manager.registerListener(
            this,
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_FASTEST
        )

    }


    private fun writeToFile(data: String, context: Context): String {
        return try {
            val date = PersianDateFormat("Y-m-d | H:i:s").format(PersianDate(System.currentTimeMillis()))
            val fileName = "report:$date.txt"
            val outputStreamWriter = OutputStreamWriter(context.openFileOutput(fileName, Context.MODE_PRIVATE))
            outputStreamWriter.write(data)
            outputStreamWriter.close()
            fileName
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
            ""
        }

    }

    private fun sendDataToFireBase() {
        AlertDialog.Builder(this)
            .setTitle("Are you sure to save this report")
            .setNegativeButton("No") { _, _ ->
                button.text = getString(R.string.start)
                button.setBackgroundColor(Color.GREEN)
            }
            .setPositiveButton("Yes") { _, _ ->
                send(writeToFile(generateDataString(datas), this))
                datas.clear()
            }
            .show()
    }

    private fun generateDataString(list: ArrayList<Data>) : String {
        return list.joinToString(separator = "\n")
    }


    private fun send(fileName: String) {
        button.text = getString(R.string.wait)
        button.setBackgroundColor(Color.GRAY)

        val reference = FirebaseStorage.getInstance().reference.child(fileName)

        val task = reference.putFile(Uri.fromFile(File(filesDir.path + "/$fileName")))
        task.addOnSuccessListener {
            val intent = Intent(this, ConfirmationActivity::class.java)
            intent.putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.SUCCESS_ANIMATION
            )
            intent.putExtra(
                ConfirmationActivity.EXTRA_MESSAGE,
                "Report Completed"
            )
            startActivity(intent)
            button.text = getString(R.string.start)
            button.setBackgroundColor(Color.GREEN)
            Log.i("TAG", "Success -> ${it.metadata?.name}")
        }.addOnFailureListener {
            val intent = Intent(this, ConfirmationActivity::class.java)
            intent.putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.FAILURE_ANIMATION
            )
            intent.putExtra(
                ConfirmationActivity.EXTRA_MESSAGE,
                "Check Your Network"
            )
            startActivity(intent)
            button.text = getString(R.string.start)
            button.setBackgroundColor(Color.GREEN)
            Log.i("TAG", "Exception -> $it")
        }
    }

    override fun onStop() {
        super.onStop()
        manager.unregisterListener(this)
    }

    data class Data(val Time: Long, val X: Float, val Y: Float, val Z: Float){
        override fun toString() = "$X $Y $Z $Time"
    }
}
