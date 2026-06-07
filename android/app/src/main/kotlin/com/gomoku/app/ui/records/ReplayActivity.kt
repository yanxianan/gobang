package com.gomoku.app.ui.records

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gomoku.app.databinding.ActivityReplayBinding
import com.gomoku.app.net.ApiService
import com.gomoku.app.net.Move
import com.gomoku.app.net.Stone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReplayActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_ID = "game_id"
    }

    private lateinit var binding: ActivityReplayBinding
    private val handler = Handler(Looper.getMainLooper())
    private var moves: List<Move> = emptyList()
    private var current = 0
    private val board = Array(15) { IntArray(15) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReplayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.boardView.isReplay = true
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlay.setOnClickListener { togglePlay() }
        binding.btnPrev.setOnClickListener { step(-1) }
        binding.btnNext.setOnClickListener { step(1) }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    current = progress
                    renderToCurrent()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0) {
            finish(); return
        }
        lifecycleScope.launch {
            val detail = withContext(Dispatchers.IO) { ApiService.getGame(id) }
            if (detail == null) { finish(); return@launch }
            moves = detail.moves
            binding.tvInfo.text = "${detail.blackName}  VS  ${detail.whiteName}    结果: ${detail.reason}"
            binding.seekBar.max = moves.size
            renderToCurrent()
        }
    }

    private val playRunnable = object : Runnable {
        override fun run() {
            if (current < moves.size) {
                step(1)
                handler.postDelayed(this, 800)
            } else {
                binding.btnPlay.text = "播放"
            }
        }
    }

    private fun togglePlay() {
        if (current >= moves.size) current = 0
        binding.btnPlay.text = "暂停"
        handler.removeCallbacks(playRunnable)
        handler.post(playRunnable)
    }

    private fun step(delta: Int) {
        val target = (current + delta).coerceIn(0, moves.size)
        // 重置并重放到 target
        for (y in 0 until 15) for (x in 0 until 15) board[y][x] = 0
        for (i in 0 until target) {
            val m = moves[i]
            if (m.y in 0 until 15 && m.x in 0 until 15) board[m.y][m.x] = m.seat
        }
        current = target
        binding.seekBar.progress = current
        val last = if (current > 0) moves[current - 1] else null
        binding.boardView.setData(
            board = board.map { it.toList() },
            lastMove = last?.let { it.x to it.y },
            mySeat = 0,
            turn = Stone.NONE,
            status = "finished",
            winner = 0,
            winLine = null
        )
        binding.tvStep.text = "$current / ${moves.size}"
    }

    private fun renderToCurrent() {
        step(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(playRunnable)
    }
}
