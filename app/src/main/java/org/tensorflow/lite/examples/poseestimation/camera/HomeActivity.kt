package org.tensorflow.lite.examples.poseestimation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.tensorflow.lite.examples.poseestimation.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding 설정
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-Edge 모드 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 운동 선택 화면(ExerciseSelectionActivity)으로 이동하는 버튼 클릭 리스너 추가
        binding.btnGoToExerciseSelection.setOnClickListener {
            startActivity(Intent(this, ExerciseSelectionActivity::class.java))
        }

        // 설정 화면(SettingActivity)으로 이동하는 버튼 클릭 리스너 추가
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }
    }
}
