package com.pocketpet

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.pocketpet.pet.Pets

/**
 * 여러 마리가 나와 있을 때 ‘소환 해제’를 누르면 뜨는 팝업.
 * 펫 이름을 누르면 그 펫만 돌려보내고 목록에서 지운다.
 * 아래에는 ‘모두 돌려보내기’와 ‘나가기’가 있다.
 */
class DismissDialog(
    private val activity: Activity,
    pets: List<String>,
    private val onDismissOne: (id: String) -> Unit,
    private val onDismissAll: () -> Unit
) {
    private val density = activity.resources.displayMetrics.density
    private val dialog = Dialog(activity)
    private val remaining = pets.toMutableList()
    private val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildContent())
        dialog.window?.apply {
            // 둥근 모서리가 보이게 창 배경은 투명하게, 뒤 화면은 살짝 어둡게
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (activity.resources.displayMetrics.widthPixels * 0.86f).toInt()
                .coerceAtMost(dp(400))
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    private fun buildContent(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(14))
            background = rounded(BG_COLOR, 22f)
        }
        root.addView(TextView(activity).apply {
            setText(R.string.dismiss_popup_title)
            setTextColor(TEXT_COLOR)
            textSize = 20f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        })
        root.addView(TextView(activity).apply {
            setText(R.string.dismiss_popup_hint)
            setTextColor(HINT_COLOR)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        })

        remaining.forEach { list.addView(petRow(it)) }
        root.addView(list)

        // 모두 돌려보내기 (주황 버튼)
        root.addView(TextView(activity).apply {
            setText(R.string.dismiss_all)
            setTextColor(Color.WHITE)
            textSize = 17f
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            background = pressable(ACCENT_COLOR, 16f)
            setOnClickListener {
                dialog.dismiss()
                onDismissAll()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(10)
        })

        // 나가기
        root.addView(TextView(activity).apply {
            setText(R.string.dismiss_popup_close)
            setTextColor(HINT_COLOR)
            textSize = 16f
            gravity = Gravity.CENTER
            background = pressable(BG_COLOR, 16f)
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(6)
        })
        return root
    }

    /** 펫 한 줄: 작은 그림 + 이름 + ✕ */
    private fun petRow(id: String): View {
        val pet = Pets.byId(id)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(14), dp(6))
            background = pressable(ROW_COLOR, 14f)
        }
        row.addView(ImageView(activity).apply {
            setImageResource(pet.idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(TextView(activity).apply {
            setText(pet.nameRes)
            setTextColor(TEXT_COLOR)
            textSize = 17f
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(activity).apply {
            text = "✕"
            setTextColor(HINT_COLOR)
            textSize = 18f
        })
        row.setOnClickListener {
            remaining.remove(id)
            list.removeView(row)
            onDismissOne(id)
            if (remaining.isEmpty()) dialog.dismiss()
        }
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        return row
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    /** 누르면 살짝 물결이 퍼지는 둥근 배경 */
    private fun pressable(color: Int, radiusDp: Float) = RippleDrawable(
        ColorStateList.valueOf(RIPPLE_COLOR), rounded(color, radiusDp), null
    )

    private fun dp(value: Int) = (value * density).toInt()

    companion object {
        private val BG_COLOR = Color.rgb(0xFF, 0xF8, 0xEE)
        private val ROW_COLOR = Color.rgb(0xFF, 0xEC, 0xD6)
        private val ACCENT_COLOR = Color.rgb(0xF2, 0x8C, 0x38)
        private val TEXT_COLOR = Color.rgb(0x3B, 0x2F, 0x2F)
        private val HINT_COLOR = Color.rgb(0x7A, 0x6A, 0x5A)
        private val RIPPLE_COLOR = Color.argb(0x40, 0x7A, 0x6A, 0x5A)
    }
}
