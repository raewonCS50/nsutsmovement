package org.tensorflow.lite.examples.poseestimation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.WindowManager
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

class SquatActivity : AppCompatActivity(), CameraSource.CameraSourceListener {

    private lateinit var surfaceView: SurfaceView
    private var cameraSource: CameraSource? = null
    private lateinit var poseClassifier: PoseClassifier
    private lateinit var tvGuideMessage: TextView // TextView 추가

    // 권한 요청 설정
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
        setContentView(R.layout.activity_squat)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.surfaceView)
        tvGuideMessage = findViewById(R.id.tvGuideMessage) // TextView 초기화

        // 안내 메시지를 설정하고 보이도록 처리
        showGuideMessage("카메라에서 떨어져 옆으로 서세요")

        // PoseClassifier 초기화
        poseClassifier = PoseClassifier.create(this)

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

    private fun openCamera() {
        if (isCameraPermissionGranted()) {
            if (cameraSource == null) {
                cameraSource = CameraSource(surfaceView, this, poseClassifier).apply {
                    setExerciseFlags(pushUp = false, plank = false, sitUp = false, lunge = false, squat = true)
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
        // MoveNet을 PoseDetector로 설정
        val poseDetector = MoveNet.create(this, Device.CPU, ModelType.Thunder)
        cameraSource?.setDetector(poseDetector)
        cameraSource?.setTracker(TrackerType.KEYPOINTS)
    }

    override fun onPoseFeedback(feedback: String) {
        // 피드백을 UI에 표시하지 않음
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
    }
}
