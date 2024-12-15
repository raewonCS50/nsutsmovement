package org.tensorflow.lite.examples.poseestimation

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.SharedPreferences

class StatisticsActivity : AppCompatActivity() {

    private lateinit var tvTotalPlankTime: TextView
    private lateinit var btnResetPlankTime: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics) // XML 파일 이름이 정확히 맞는지 확인하세요

        // UI 초기화
        tvTotalPlankTime = findViewById(R.id.tvTotalPlankTime)
        btnResetPlankTime = findViewById(R.id.btnResetPlankTime)
        sharedPreferences = getSharedPreferences("ExerciseStats", Context.MODE_PRIVATE)

        // 현재 총 플랭크 시간 표시
        val totalPlankTime = sharedPreferences.getLong("TotalPlankTime", 0L)
        tvTotalPlankTime.text = "총 플랭크 시간: ${totalPlankTime / 1000} 초"

        // 초기화 버튼 클릭 리스너
        btnResetPlankTime.setOnClickListener {
            resetPlankTime()
        }
    }

    private fun resetPlankTime() {
        // SharedPreferences에서 플랭크 시간 초기화
        sharedPreferences.edit().putLong("TotalPlankTime", 0L).apply()
        tvTotalPlankTime.text = "총 플랭크 시간: 0초"
    }
}
