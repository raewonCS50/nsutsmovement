package org.tensorflow.lite.examples.poseestimation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.examples.poseestimation.databinding.ActivitySettingBinding

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding 설정
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // SharedPreferences 가져오기
        val sharedPref = getSharedPreferences("PoseEstimationPrefs", Context.MODE_PRIVATE)

        // 골격 색상 선택 스피너 설정
        val colorOptions = arrayOf("빨간색", "파란색", "회색")
        val colorValues = arrayOf(Color.RED, Color.BLUE, Color.GRAY)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spnSkeletonColor.adapter = adapter

        // 현재 저장된 골격 색상을 설정 (기본값은 빨간색)
        val currentColor = sharedPref.getInt("skeleton_color", Color.RED)
        val currentColorIndex = colorValues.indexOf(currentColor)
        binding.spnSkeletonColor.setSelection(currentColorIndex)

        // Spinner에서 선택한 색상을 SharedPreferences에 저장
        binding.spnSkeletonColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // 선택한 골격 색상에 따른 로직 추가
                val selectedColor = colorValues[position]
                with(sharedPref.edit()) {
                    putInt("skeleton_color", selectedColor)
                    apply()
                }
            }
        }

        // 뒤로 가기 버튼 클릭 리스너 추가
        binding.btnGoBack.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish() // 현재 액티비티를 종료하여 사용자가 돌아가기 버튼을 누른 것처럼 보이게 함
        }
    }
}
