package org.tensorflow.lite.examples.poseestimation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageButton

class ExerciseSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_selection)

        // 팔굽혀펴기 화면으로 이동하는 버튼
        findViewById<Button>(R.id.btnPushUp).setOnClickListener {
            startActivity(Intent(this, PushUpActivity::class.java))
        }

        // 플랭크 화면으로 이동하는 버튼
        findViewById<Button>(R.id.btnPlank).setOnClickListener {
            startActivity(Intent(this, PlankActivity::class.java))
        }

        // 윗몸일으키기 화면으로 이동하는 버튼
        findViewById<Button>(R.id.btnSitUp).setOnClickListener {
            startActivity(Intent(this, SitUpActivity::class.java))
        }

        // 런지 화면으로 이동하는 버튼
        findViewById<Button>(R.id.btnLunge).setOnClickListener {
            startActivity(Intent(this, LungeActivity::class.java))
        }

        // 스쿼트 화면으로 이동하는 버튼
        findViewById<Button>(R.id.btnSquat).setOnClickListener {
            startActivity(Intent(this, SquatActivity::class.java))
        }

        // 홈 화면으로 돌아가기 버튼
        findViewById<ImageButton>(R.id.btnGoToHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish() // 현재 액티비티를 종료하여 뒤로 가기와 같은 효과를 제공
        }
    }
}
