package com.pocketpet

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import com.pocketpet.data.PetSettingsRepository
import kotlin.math.roundToInt

class SettingsActivity : Activity() {

    private lateinit var settings: PetSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = PetSettingsRepository(this)

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
        ) { settings.floorDp = it }

        bindRow(
            inputId = R.id.walkFreqInput,
            barId = R.id.walkFreqSeekBar,
            barMin = 0,
            barMax = 10,
            minValue = 0,
            initial = settings.walkFrequency
        ) { settings.walkFrequency = it }
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
