package org.tensorflow.lite.examples.poseestimation.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tensorflow.lite.examples.poseestimation.VisualizationUtils
import org.tensorflow.lite.examples.poseestimation.YuvToRgbConverter
import org.tensorflow.lite.examples.poseestimation.data.Person
import org.tensorflow.lite.examples.poseestimation.ml.*
import java.util.Timer
import java.util.TimerTask
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraSource(
    private val surfaceView: SurfaceView,
    private val listener: CameraSourceListener? = null,
    private val poseClassifier: PoseClassifier
) {

    companion object {
        private const val PREVIEW_WIDTH = 640
        private const val PREVIEW_HEIGHT = 480
        private const val MIN_CONFIDENCE = .2f
        private const val TAG = "Camera Source"
    }

    private val lock = Any()
    private var detector: PoseDetector? = null
    private var isTrackerEnabled = false
    private var yuvConverter: YuvToRgbConverter = YuvToRgbConverter(surfaceView.context)
    private lateinit var imageBitmap: Bitmap

    private var fpsTimer: Timer? = null
    private var frameProcessedInOneSecondInterval = 0
    private var framesPerSecond = 0

    private val cameraManager: CameraManager by lazy {
        val context = surfaceView.context
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var imageReader: ImageReader? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null
    private var cameraId: String = ""

    private var isDoingPushUp = false
    private var isDoingPlank = false
    private var isDoingSitUp = false
    private var isDoingLunge = false
    private var isDoingSquat = false

    // 세트 카운트 관련 변수
    private var currentSetCount = 0
    private var isGoodPosture = false
    private var goodPostureStartTime: Long = 0L
    private val goodPostureHoldDuration = 200L // 자세 유지 시간 0.2초로 줄임

    // 골격 색상 변수
    private var skeletonColor: Int = Color.RED

    fun setExerciseFlags(
        pushUp: Boolean,
        plank: Boolean,
        sitUp: Boolean,
        lunge: Boolean,
        squat: Boolean
    ) {
        isDoingPushUp = pushUp
        isDoingPlank = plank
        isDoingSitUp = sitUp
        isDoingLunge = lunge
        isDoingSquat = squat
    }

    // 세트 카운트를 초기화하는 메서드
    fun resetSetCount() {
        currentSetCount = 0
        listener?.onSetCountUpdated(currentSetCount) // 초기화된 세트 카운트를 리스너에 전달
    }

    suspend fun initCamera() {
        camera = openCamera(cameraManager, cameraId)
        imageReader = ImageReader.newInstance(PREVIEW_WIDTH, PREVIEW_HEIGHT, ImageFormat.YUV_420_888, 3)
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                if (!::imageBitmap.isInitialized) {
                    imageBitmap = Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
                }
                yuvConverter.yuvToRgb(image, imageBitmap)
                val rotateMatrix = Matrix()
                rotateMatrix.postRotate(getPreviewRotation().toFloat()) // 회전 값을 적용

                val rotatedBitmap = Bitmap.createBitmap(imageBitmap, 0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT, rotateMatrix, false)
                processImage(rotatedBitmap)
                image.close()
            }
        }, imageReaderHandler)

        imageReader?.surface?.let { surface ->
            session = createSession(listOf(surface))
            val cameraRequest = camera?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(surface)
            }
            cameraRequest?.build()?.let {
                session?.setRepeatingRequest(it, null, null)
            }
        }
    }

    private fun getPreviewRotation(): Int {
        val windowManager = surfaceView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val degrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

        val isFrontFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        return if (isFrontFacing) {
            (sensorOrientation + degrees) % 360
        } else {
            (sensorOrientation - degrees + 360) % 360
        }
    }

    private suspend fun createSession(targets: List<Surface>): CameraCaptureSession =
        suspendCancellableCoroutine { cont ->
            camera?.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(captureSession: CameraCaptureSession) =
                    cont.resume(captureSession)

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    cont.resumeWithException(Exception("Session error"))
                }
            }, null)
        }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(manager: CameraManager, cameraId: String): CameraDevice =
        suspendCancellableCoroutine { cont ->
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) = cont.resume(camera)

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    if (cont.isActive) cont.resumeWithException(Exception("Camera error"))
                }
            }, imageReaderHandler)
        }

    fun prepareCamera() {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                continue
            }
            this.cameraId = cameraId
        }
    }

    fun setDetector(detector: PoseDetector) {
        synchronized(lock) {
            if (this.detector != null) {
                this.detector?.close()
                this.detector = null
            }
            this.detector = detector
        }
    }

    fun setTracker(trackerType: TrackerType) {
        isTrackerEnabled = trackerType != TrackerType.OFF
        (this.detector as? MoveNetMultiPose)?.setTracker(trackerType)
    }

    fun resume() {
        imageReaderThread = HandlerThread("imageReaderThread").apply { start() }
        imageReaderHandler = Handler(imageReaderThread!!.looper)
        fpsTimer = Timer()
        fpsTimer?.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    framesPerSecond = frameProcessedInOneSecondInterval
                    frameProcessedInOneSecondInterval = 0
                }
            },
            0,
            1000
        )

        // 골격 색상 SharedPreferences에서 불러오기
        val sharedPref = surfaceView.context.getSharedPreferences("PoseEstimationPrefs", Context.MODE_PRIVATE)
        skeletonColor = sharedPref.getInt("skeleton_color", Color.RED) // 기본값은 빨간색
    }

    fun close() {
        session?.close()
        session = null
        camera?.close()
        camera = null
        imageReader?.close()
        imageReader = null
        stopImageReaderThread()
        detector?.close()
        detector = null
        fpsTimer?.cancel()
        fpsTimer = null
        frameProcessedInOneSecondInterval = 0
        framesPerSecond = 0
    }

    // 이미지 처리 및 자세 평가
    private fun processImage(bitmap: Bitmap) {
        val persons = mutableListOf<Person>()
        synchronized(lock) {
            detector?.estimatePoses(bitmap)?.let {
                persons.addAll(it)
            }
        }
        frameProcessedInOneSecondInterval++
        if (frameProcessedInOneSecondInterval == 1) {
            listener?.onFPSListener(framesPerSecond)
        }

        if (persons.isNotEmpty()) {
            listener?.onDetectedInfo(persons[0].score, null, persons, bitmap)
            val feedback = provideFeedbackAndCountSets(persons[0])
            listener?.onPoseFeedback(feedback)
        }

        visualize(persons, bitmap)
    }

    private fun provideFeedbackAndCountSets(person: Person): String {
        val (feedback, detectedGoodPosture) = when {
            isDoingPushUp -> poseClassifier.evaluatePushUp(person)
            isDoingPlank -> poseClassifier.evaluatePlank(person)
            isDoingSitUp -> poseClassifier.evaluateSitUp(person)
            isDoingLunge -> poseClassifier.evaluateLunge(person)
            isDoingSquat -> poseClassifier.evaluateSquat(person)
            else -> "운동을 선택하세요." to false
        }

        if (detectedGoodPosture) {
            if (!isGoodPosture) {
                // 좋은 자세로 인식되면 시작 시간 기록
                isGoodPosture = true
                goodPostureStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - goodPostureStartTime >= goodPostureHoldDuration) {
                // 좋은 자세가 일정 시간 유지되면 세트 카운트 증가
                currentSetCount++
                listener?.onSetCountUpdated(currentSetCount)
                isGoodPosture = false // 세트 완료 후 플래그 초기화
            }
        } else {
            isGoodPosture = false
            goodPostureStartTime = 0L
        }
        return feedback
    }

    private fun visualize(persons: List<Person>, bitmap: Bitmap) {
        val feedbacks = persons.map { person ->
            when {
                isDoingPushUp -> poseClassifier.evaluatePushUp(person).first
                isDoingPlank -> poseClassifier.evaluatePlank(person).first
                isDoingSitUp -> poseClassifier.evaluateSitUp(person).first
                isDoingLunge -> poseClassifier.evaluateLunge(person).first
                isDoingSquat -> poseClassifier.evaluateSquat(person).first
                else -> "운동을 선택하세요."
            }
        }

        val isGoodPostureList = feedbacks.map { feedback -> feedback.contains("좋습니다") }

        // 각 사람에 대해 골격 색상을 결정합니다.
        val personsWithColors = persons.mapIndexed { index, person ->
            if (isGoodPostureList.getOrNull(index) == true) {
                // 올바른 자세일 경우 설정된 기본 골격 색상을 사용
                person to skeletonColor
            } else {
                // 잘못된 자세일 경우 기본 색상 (예: 빨간색) 사용
                person to Color.RED
            }
        }

        // VisualizationUtils에서 각 사람마다 지정된 색상을 사용하여 골격을 그립니다.
        val outputBitmap = VisualizationUtils.drawBodyKeypoints(
            bitmap,
            personsWithColors.map { it.first },
            personsWithColors.map { it.second },
            isTrackerEnabled
        )

        val holder = surfaceView.holder
        val surfaceCanvas = holder.lockCanvas()
        surfaceCanvas?.let { canvas ->
            // 캔버스를 지워서 중첩 텍스트를 방지
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val screenWidth: Int
            val screenHeight: Int
            val left: Int
            val top: Int

            if (canvas.height > canvas.width) {
                val ratio = outputBitmap.height.toFloat() / outputBitmap.width
                screenWidth = canvas.width
                left = 0
                screenHeight = (canvas.width * ratio).toInt()
                top = (canvas.height - screenHeight) / 2
            } else {
                val ratio = outputBitmap.width.toFloat() / outputBitmap.height
                screenHeight = canvas.height
                top = 0
                screenWidth = (canvas.height * ratio).toInt()
                left = (canvas.width - screenWidth) / 2
            }
            val right: Int = left + screenWidth
            val bottom: Int = top + screenHeight

            canvas.drawBitmap(
                outputBitmap, Rect(0, 0, outputBitmap.width, outputBitmap.height),
                Rect(left, top, right, bottom), null
            )

            // 세트 카운트 텍스트 추가
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 60f
                isAntiAlias = true
            }
            canvas.drawText("횟수: $currentSetCount", 50f, 100f, paint)

            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun stopImageReaderThread() {
        imageReaderThread?.quitSafely()
        try {
            imageReaderThread?.join()
            imageReaderThread = null
            imageReaderHandler = null
        } catch (e: InterruptedException) {
            Log.d(TAG, e.message.toString())
        }
    }

    interface CameraSourceListener {
        fun onFPSListener(fps: Int)
        fun onDetectedInfo(personScore: Float?, poseLabels: List<Pair<String, Float>>?, persons: List<Person>, inputBitmap: Bitmap)
        fun onPoseFeedback(feedback: String)
        fun onSetCountUpdated(setCount: Int)  // 셋트 카운트를 업데이트하는 메서드 추가
    }
}
