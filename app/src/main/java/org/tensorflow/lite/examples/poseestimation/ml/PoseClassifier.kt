package org.tensorflow.lite.examples.poseestimation.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.examples.poseestimation.data.Person
import org.tensorflow.lite.examples.poseestimation.data.BodyPart
import org.tensorflow.lite.examples.poseestimation.data.KeyPoint
import kotlin.math.atan2
import org.tensorflow.lite.support.common.FileUtil

class PoseClassifier(
    private val interpreter: Interpreter,
    private val labels: List<String>
) {
    private val inputShape = interpreter.getInputTensor(0).shape()
    private val outputShape = interpreter.getOutputTensor(0).shape()

    companion object {
        private const val MODEL_FILENAME = "classifier.tflite"
        private const val LABELS_FILENAME = "labels.txt"
        private const val CPU_NUM_THREADS = 4

        fun create(context: Context): PoseClassifier {
            val options = Interpreter.Options().apply {
                setNumThreads(CPU_NUM_THREADS)
            }

            val labels = try {
                FileUtil.loadLabels(context, LABELS_FILENAME)
            } catch (e: Exception) {
                e.printStackTrace()
                listOf("Unknown pose") // Default label if file loading fails
            }

            val interpreter = Interpreter(FileUtil.loadMappedFile(context, MODEL_FILENAME), options)
            return PoseClassifier(interpreter, labels)
        }
    }

    fun classify(person: Person?): List<Pair<String, Float>> {
        if (person == null || person.keyPoints.isEmpty()) {
            return listOf("Unknown pose" to 0f)
        }

        // Prepare input vector based on Person key points
        val inputVector = FloatArray(inputShape[1])
        person.keyPoints.forEachIndexed { index, keyPoint ->
            inputVector[index * 3] = keyPoint.coordinate.y
            inputVector[index * 3 + 1] = keyPoint.coordinate.x
            inputVector[index * 3 + 2] = keyPoint.score
        }

        // Get the model's output
        val outputTensor = FloatArray(outputShape[1])
        interpreter.run(arrayOf(inputVector), arrayOf(outputTensor))

        return outputTensor.mapIndexed { index, score ->
            val label = labels.getOrNull(index) ?: "Unknown pose"
            label to score
        }
    }

    private fun calculateAngle(pointA: KeyPoint, pointB: KeyPoint, pointC: KeyPoint): Float {
        val deltaY1 = pointC.coordinate.y - pointB.coordinate.y
        val deltaX1 = pointC.coordinate.x - pointB.coordinate.x
        val deltaY2 = pointA.coordinate.y - pointB.coordinate.y
        val deltaX2 = pointA.coordinate.x - pointB.coordinate.x

        val angleRad = atan2(deltaY1.toDouble(), deltaX1.toDouble()) - atan2(deltaY2.toDouble(), deltaX2.toDouble())
        var angle = Math.toDegrees(angleRad).toFloat()

        if (angle < 0) angle += 360f
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    fun evaluatePushUp(person: Person): Pair<String, Boolean> {
        val leftElbowAngle = calculateAngle(
            person.keyPoints[BodyPart.LEFT_SHOULDER.position],
            person.keyPoints[BodyPart.LEFT_ELBOW.position],
            person.keyPoints[BodyPart.LEFT_WRIST.position]
        )
        val rightElbowAngle = calculateAngle(
            person.keyPoints[BodyPart.RIGHT_SHOULDER.position],
            person.keyPoints[BodyPart.RIGHT_ELBOW.position],
            person.keyPoints[BodyPart.RIGHT_WRIST.position]
        )

        val isGoodPosture = leftElbowAngle in 50f..130f && rightElbowAngle in 50f..130f
        val feedback = if (isGoodPosture) "팔굽혀펴기 자세가 좋습니다!" else "팔굽혀펴기 자세를 수정하세요!"
        return feedback to isGoodPosture
    }

    fun evaluatePlank(person: Person): Pair<String, Boolean> {
        val leftHipAngle = calculateAngle(
            person.keyPoints[BodyPart.LEFT_SHOULDER.position],
            person.keyPoints[BodyPart.LEFT_HIP.position],
            person.keyPoints[BodyPart.LEFT_KNEE.position]
        )
        val rightHipAngle = calculateAngle(
            person.keyPoints[BodyPart.RIGHT_SHOULDER.position],
            person.keyPoints[BodyPart.RIGHT_HIP.position],
            person.keyPoints[BodyPart.RIGHT_KNEE.position]
        )

        val isGoodPosture = leftHipAngle in 160f..180f && rightHipAngle in 160f..180f
        val feedback = if (isGoodPosture) "플랭크 자세가 좋습니다!" else "플랭크 자세를 수정하세요!"
        return feedback to isGoodPosture
    }

    fun evaluateSitUp(person: Person): Pair<String, Boolean> {
        val torsoAngle = calculateAngle(
            person.keyPoints[BodyPart.LEFT_HIP.position],
            person.keyPoints[BodyPart.LEFT_SHOULDER.position],
            person.keyPoints[BodyPart.LEFT_ELBOW.position]
        )

        val isGoodPosture = torsoAngle in 30f..50f
        val feedback = if (isGoodPosture) " 윗몸일으키기 자세가 좋습니다!!" else "윗몸일으키기 자세를 수정하세요!"
        return feedback to isGoodPosture
    }

    fun evaluateLunge(person: Person): Pair<String, Boolean> {
        val leftKneeAngle = calculateAngle(
            person.keyPoints[BodyPart.LEFT_HIP.position],
            person.keyPoints[BodyPart.LEFT_KNEE.position],
            person.keyPoints[BodyPart.LEFT_ANKLE.position]
        )
        val rightKneeAngle = calculateAngle(
            person.keyPoints[BodyPart.RIGHT_HIP.position],
            person.keyPoints[BodyPart.RIGHT_KNEE.position],
            person.keyPoints[BodyPart.RIGHT_ANKLE.position]
        )

        val isGoodPosture = leftKneeAngle in 80f..120f && rightKneeAngle in 80f..120f
        val feedback = if (isGoodPosture) "런지 자세가 좋습니다!" else "런지 자세를 수정하세요!"
        return feedback to isGoodPosture
    }

    fun evaluateSquat(person: Person): Pair<String, Boolean> {
        val kneeAngle = calculateAngle(
            person.keyPoints[BodyPart.RIGHT_HIP.position],
            person.keyPoints[BodyPart.RIGHT_KNEE.position],
            person.keyPoints[BodyPart.RIGHT_ANKLE.position]
        )

        val isGoodPosture = kneeAngle in 70f..110f
        val feedback = if (isGoodPosture) "스쿼트 자세가 좋습니다!" else "스쿼트 자세를 수정하세요!"
        return feedback to isGoodPosture
    }

    fun close() {
        interpreter.close()
    }
}
