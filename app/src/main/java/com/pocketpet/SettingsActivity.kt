package com.pocketpet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.pocketpet.data.PetSettingsRepository
import com.pocketpet.data.ScreenInfo
import kotlin.math.roundToInt

class SettingsActivity : Activity() {

    private lateinit var settings: PetSettingsRepository
    private lateinit var floorWarning: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = PetSettingsRepository(this)
        floorWarning = findViewById(R.id.floorWarning)

        findViewById<Button>(R.id.backButton).setOnClickListener { finish() }

        bindRow(
            inputId = R.id.sizeInput,
            barId = R.id.sizeSeekBar,
            barMin = 50,
            barMax = 200,
            minValue = PetSettingsRepository.MIN_SIZE_PERCENT,
            initial = settings.sizePercent
        ) { settings.sizePercent = it }

        bindRow(
            inputId = R.id.floorInput,
            barId = R.id.floorSeekBar,
            barMin = 0,
            barMax = 500,
            minValue = 0,
            initial = settings.floorDp
        ) {
            settings.floorDp = it
            updateFloorWarning()
        }

        bindRow(
            inputId = R.id.walkFreqInput,
            barId = R.id.walkFreqSeekBar,
            barMin = 0,
            barMax = 10,
            minValue = 0,
            initial = settings.walkFrequency
        ) { settings.walkFrequency = it }

        findViewById<Button>(R.id.resetButton).setOnClickListener { confirmReset() }
    }

    /** 정말 초기화할지 한 번 물어본 뒤 기본값으로 되돌린다 */
    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_confirm_title)
            .setMessage(R.string.reset_confirm_message)
            .setPositiveButton(R.string.reset_confirm_ok) { _, _ ->
                settings.resetToDefaults()
                Toast.makeText(this, R.string.toast_reset_done, Toast.LENGTH_SHORT).show()
                // 화면을 새로 열어서 기본값을 보여 준다
                // (recreate()는 칸에 적힌 옛 숫자를 되살려서 다시 저장해 버린다)
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateFloorWarning()
    }

    /** 펫이 화면 밖으로 완전히 나가면 빨간 경고를 보여 준다 */
    private fun updateFloorWarning() {
        val hidden = ScreenInfo.isPetHidden(this, settings.floorDp)
        floorWarning.visibility = if (hidden) View.VISIBLE else View.GONE
    }

    /**
     * 숫자 칸과 막대를 연동한다.
     * 막대 범위를 넘는 숫자도 입력할 수 있고, 그때 막대는 끝에 멈춰 있다.
     */
    private fun bindRow(
        inputId: Int,
        barId: Int,
        barMin: Int,
        barMax: Int,
        minValue: Int,
        initial: Int,
        save: (Int) -> Unit
    ) {
        val input = findViewById<EditText>(inputId)
        val bar = findViewById<SeekBar>(barId)
        var syncing = false   // 서로 고치다가 무한 반복하지 않도록

        bar.min = barMin
        bar.max = barMax
        bar.progress = initial.coerceIn(barMin, barMax)
        input.setText(initial.toString())

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || syncing) return
                syncing = true
                input.setText(progress.toString())
                input.setSelection(input.text.length)
                syncing = false
                save(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (syncing) return
                val typed = s?.toString()?.toFloatOrNull() ?: return   // 비어 있으면 무시
                val value = maxOf(typed.roundToInt(), minValue)
                syncing = true
                bar.progress = value.coerceIn(barMin, barMax)
                syncing = false
                save(value)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}
