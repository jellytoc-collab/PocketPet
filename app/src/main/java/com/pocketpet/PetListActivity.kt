package com.pocketpet

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.pet.PetDefinition
import com.pocketpet.pet.Pets

/**
 * ≡ 메뉴 → 펫 목록.
 * 펫이 [그림 | 이름] 한 줄씩 세로로 늘어서 있고, 누르면 그 펫을 고르고 메인 화면으로 돌아간다.
 */
class PetListActivity : Activity() {

    private lateinit var settings: PetSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_list)
        settings = PetSettingsRepository(this)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        val list = findViewById<LinearLayout>(R.id.petList)
        val selectedId = settings.selectedPetId
        Pets.ALL.forEach { pet ->
            list.addView(createRow(pet, pet.id == selectedId))
        }
    }

    /** 목록 한 줄: 왼쪽에 펫 그림, 오른쪽에 이름 */
    private fun createRow(pet: PetDefinition, selected: Boolean): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
            isFocusable = true
            // 지금 고른 펫은 주황 테두리, 나머지는 누를 때 반짝이는 기본 효과
            if (selected) {
                background = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(0xFFFFE9D2.toInt())
                    setStroke(dp(2), 0xFFF28C38.toInt())
                }
            } else {
                val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                background = attrs.getDrawable(0)
                attrs.recycle()
            }
            setOnClickListener {
                settings.selectedPetId = pet.id
                finish()   // 메인 화면이 돌아오면서 이 펫을 보여 준다
            }
        }

        row.addView(
            ImageView(this).apply {
                setImageResource(pet.idle)
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(dp(ROW_IMAGE_DP), dp(ROW_IMAGE_DP))
        )
        row.addView(
            TextView(this).apply {
                setText(pet.nameRes)
                setTextColor(0xFF3B2F2F.toInt())
                textSize = 20f
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(20)
            }
        )

        // 줄과 줄 사이 간격
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) }
        return row
    }

    companion object {
        private const val ROW_IMAGE_DP = 72   // 목록의 펫 그림 크기
    }
}
