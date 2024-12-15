package org.tensorflow.lite.examples.poseestimation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
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

class LungeActivity : AppCompatActivity(), CameraSource.CameraSourceListener {

    private lateinit var surfaceView: SurfaceView
    private var cameraSource: CameraSource? = null
    private lateinit var tvGuideMessage: TextView
    private lateinit var btnStartTimer: Button // 타이머 시작 버튼
    private lateinit var tvTimer: TextView // 타이머 표시 TextView
    private var countDownTimer: CountDownTimer? = null

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

        surfaceView = findViewById(R.id.surfaceView)
        tvGuideMessage = findViewById(R.id.tvGuideMessage)
        btnStartTimer = findViewById(R.id.btnStartTimer) // Button 초기화
        tvTimer = findViewById(R.id.tvTimer) // 타이머 표시 TextView 초기화

        // 타이머 시작 버튼 클릭 리스너
        btnStartTimer.setOnClickListener {
            startTimer(30000) // 30초 타이머 시작
        }

        // 안내 메시지를 설정하고 보이도록 처리
        showGuideMessage("카메라에서 떨어져 옆으로 서세요")

        if (!isCameraPermissionGranted()) {
            requestPermission()
        } else {
            openCamera()
        }
    }

    private fun showGuideMessage(message: String) {
        // TextView에 메시지를 설정하고 보이게 함
        tvGuideMessage.text = message
        tvGuideMessage.visibility = android.view.View.VISIBLE

        // 10초 후에 메시지를 숨기기
        Handler(Looper.getMainLooper()).postDelayed({
            tvGuideMessage.visibility = android.view.View.GONE
        }, 10000) // 10000ms = 10초
    }

    private fun startTimer(durationInMillis: Long) {
        countDownTimer?.cancel() // 기존 타이머가 있으면 취소
        countDownTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                tvTimer.text = "남은 시간: $secondsRemaining 초" // 남은 시간을 TextView에 표시
            }

            override fun onFinish() {
                tvTimer.text = "시간 종료!"
                // 시간 종료 시 추가 행동을 정의할 수 있음
            }
        }.start()
    }

    private fun openCamera() {
        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                // PoseClassifier 생성 및 CameraSource 초기화
                cameraSource = CameraSource(surfaceView, this, PoseClassifier.create(this)).apply {
                    setExerciseFlags(pushUp = false, plank = false, sitUp = false, lunge = true, squat = false)
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
        // MoveNet 모델을 PoseDetector로 설정
        val poseDetector = MoveNet.create(this, Device.CPU, ModelType.Thunder)
        cameraSource?.setDetector(poseDetector)
        cameraSource?.setTracker(TrackerType.KEYPOINTS)
    }

    override fun onPoseFeedback(feedback: String) {
        // UI 피드백을 제공할 TextView가 제거됨에 따라 이 메서드의 구현을 삭제
    }

    override fun onFPSListener(fps: Int) {
        // FPS UI 업데이트 코드가 필요하다면 추가 가능
    }

    override fun onSetCountUpdated(setCount: Int) {
        // 세트 카운트를 UI에 업데이트할 필요가 없다면 코드 삭제 가능
    }

    override fun onDetectedInfo(
        personScore: Float?,
        poseLabels: List<Pair<String, Float>>?,
        persons: List<Person>,
        inputBitmap: Bitmap
    ) {
        // 사람 감지 정보 처리 코드 추가 가능
    }

    override fun onResume() {
        super.onResume()
        cameraSource?.resume()
    }

    override fun onPause() {
        super.onPause()
        cameraSource?.close()
        cameraSource = null
        countDownTimer?.cancel() // 타이머 취소
    }
}
