package org.tensorflow.lite.examples.poseestimation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.examples.poseestimation.camera.CameraSource
import org.tensorflow.lite.examples.poseestimation.data.Device
import org.tensorflow.lite.examples.poseestimation.data.Person
import org.tensorflow.lite.examples.poseestimation.ml.MoveNet
import org.tensorflow.lite.examples.poseestimation.ml.ModelType
import org.tensorflow.lite.examples.poseestimation.ml.PoseClassifier
import org.tensorflow.lite.examples.poseestimation.ml.TrackerType
import java.util.Locale

class PlankActivity : AppCompatActivity(), CameraSource.CameraSourceListener {

    private lateinit var surfaceView: SurfaceView
    private var cameraSource: CameraSource? = null
    private lateinit var tvGuideMessage: TextView
    private lateinit var btnStartTimer: Button
    private lateinit var tvTimer: TextView
    private lateinit var tvSetCompleteMessage: TextView
    private lateinit var tvRestTimer: TextView
    private lateinit var sbRestTime: SeekBar
    private lateinit var btnSkipRest: Button
    private lateinit var btnStartNextSet: Button
    private lateinit var btnViewStatistics: Button
    private var countDownTimer: CountDownTimer? = null
    private var restTimer: CountDownTimer? = null
    private var restTimeInMillis: Long = 15000 // 기본 휴식 시간 15초
    private var isResting = false
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var sharedPreferences: SharedPreferences

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plank)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // UI 초기화
        surfaceView = findViewById(R.id.surfaceView)
        tvGuideMessage = findViewById(R.id.tvGuideMessage)
        btnStartTimer = findViewById(R.id.btnStartTimer)
        tvTimer = findViewById(R.id.tvTimer)
        tvSetCompleteMessage = findViewById(R.id.tvSetCompleteMessage)
        tvRestTimer = findViewById(R.id.tvRestTimer)
        sbRestTime = findViewById(R.id.sbRestTime)
        btnSkipRest = findViewById(R.id.btnSkipRest)
        btnStartNextSet = findViewById(R.id.btnStartNextSet)
        btnViewStatistics = findViewById(R.id.btnViewStatistics)

        tvSetCompleteMessage.visibility = android.view.View.GONE
        tvRestTimer.visibility = android.view.View.GONE
        btnSkipRest.visibility = android.view.View.GONE
        btnStartNextSet.visibility = android.view.View.GONE

        sbRestTime.max = 60
        sbRestTime.progress = (restTimeInMillis / 1000).toInt()
        sbRestTime.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                restTimeInMillis = progress * 1000L
                tvRestTimer.text = "휴식 시간: ${progress}초"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnStartTimer.setOnClickListener {
            startPlankTimer(30000)
        }

        btnSkipRest.setOnClickListener {
            if (isResting) {
                restTimer?.cancel()
                startNextSet()
            }
        }

        btnStartNextSet.setOnClickListener {
            startPlankTimer(30000)
        }

        btnViewStatistics.setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java)
            startActivity(intent)
        }

        showGuideMessage("카메라에서 떨어져 자세를 취하세요")

        if (!isCameraPermissionGranted()) {
            requestPermission()
        } else {
            openCamera()
        }

        // TextToSpeech 초기화
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.KOREAN
            }
        }

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("ExerciseStats", Context.MODE_PRIVATE)
    }

    private fun startPlankTimer(durationInMillis: Long) {
        resetUIForPlank()
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                tvTimer.text = "남은 시간: $secondsRemaining 초"

                // 특정 시간에 음성 피드백 제공
                if (secondsRemaining == 10L) {
                    speakOut("10초 남았습니다!")
                } else if (secondsRemaining == 5L) {
                    speakOut("마무리 준비를 하세요!")
                }
            }

            override fun onFinish() {
                tvTimer.text = "시간 종료!"
                showSetCompleteMessage()
                recordExerciseTime(durationInMillis)
                speakOut("한 세트 완료!")
                startRestTimer()
            }
        }.start()
    }

    private fun startRestTimer() {
        isResting = true
        tvRestTimer.visibility = android.view.View.VISIBLE
        btnSkipRest.visibility = android.view.View.VISIBLE
        btnStartNextSet.visibility = android.view.View.VISIBLE

        restTimer?.cancel()
        restTimer = object : CountDownTimer(restTimeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                tvRestTimer.text = "휴식 남은 시간: $secondsRemaining 초"
            }

            override fun onFinish() {
                tvRestTimer.text = "휴식 완료! 다음 세트를 시작하세요."
                btnSkipRest.visibility = android.view.View.GONE
            }
        }.start()
    }

    private fun startNextSet() {
        tvRestTimer.visibility = android.view.View.GONE
        btnSkipRest.visibility = android.view.View.GONE
        btnStartNextSet.visibility = android.view.View.GONE
        startPlankTimer(30000)
    }

    private fun resetUIForPlank() {
        tvRestTimer.visibility = android.view.View.GONE
        btnSkipRest.visibility = android.view.View.GONE
        btnStartNextSet.visibility = android.view.View.GONE
        isResting = false
    }

    private fun showSetCompleteMessage() {
        tvSetCompleteMessage.text = "한 세트 완료!"
        tvSetCompleteMessage.visibility = android.view.View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            tvSetCompleteMessage.visibility = android.view.View.GONE
        }, 5000)
    }

    private fun showGuideMessage(message: String) {
        tvGuideMessage.text = message
        tvGuideMessage.visibility = android.view.View.VISIBLE
        Handler(Looper.getMainLooper()).postDelayed({
            tvGuideMessage.visibility = android.view.View.GONE
        }, 10000)
    }

    private fun openCamera() {
        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource = CameraSource(surfaceView, this, PoseClassifier.create(this)).apply {
                    setExerciseFlags(pushUp = false, plank = true, sitUp = false, lunge = false, squat = false)
                    prepareCamera()
                }
                lifecycleScope.launch(Dispatchers.Main) {
                    cameraSource?.initCamera()
                }
            }
            createPoseEstimator()
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun createPoseEstimator() {
        val poseDetector = MoveNet.create(this, Device.CPU, ModelType.Thunder)
        cameraSource?.setDetector(poseDetector)
        cameraSource?.setTracker(TrackerType.KEYPOINTS)
    }

    private fun speakOut(message: String) {
        if (textToSpeech.isSpeaking) {
            textToSpeech.stop()
        }
        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun recordExerciseTime(durationInMillis: Long) {
        val totalPlankTime = sharedPreferences.getLong("TotalPlankTime", 0L)
        val newTotalTime = totalPlankTime + durationInMillis
        sharedPreferences.edit().putLong("TotalPlankTime", newTotalTime).apply()
    }

    override fun onPoseFeedback(feedback: String) {}

    override fun onFPSListener(fps: Int) {}

    override fun onSetCountUpdated(setCount: Int) {}

    override fun onDetectedInfo(
        personScore: Float?,
        poseLabels: List<Pair<String, Float>>?,
        persons: List<Person>,
        inputBitmap: Bitmap
    ) {}

    override fun onResume() {
        super.onResume()
        cameraSource?.resume()
    }

    override fun onPause() {
        super.onPause()
        cameraSource?.close()
        cameraSource = null
        countDownTimer?.cancel()
        restTimer?.cancel()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}
