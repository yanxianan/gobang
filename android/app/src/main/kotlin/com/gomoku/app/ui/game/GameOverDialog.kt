package com.gomoku.app.ui.game

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.gomoku.app.R
import com.gomoku.app.databinding.DialogGameOverBinding
import com.gomoku.app.net.Stone

/**
 * 结算对话框
 * - 双方都弹
 * - 显示对战信息 (时长 / 步数 / 阵营)
 * - 赢家/平局: 按钮 "返回主页" + "再来一局"
 * - 败家:     按钮 "返回主页" + "不服再战"
 */
class GameOverDialog(
    context: Context,
    private val mySeat: Int,
    private val winner: Int,
    private val reason: String,
    private val durationSec: Int,
    private val totalMoves: Int,
    private val onBackHome: () -> Unit,
    private val onRematch: () -> Unit
) : Dialog(context, R.style.Theme_Gomoku_Dialog) {

    private val b: DialogGameOverBinding = DialogGameOverBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(b.root)
        setCancelable(false)

        val isWinner = winner == mySeat
        val isDraw = winner == Stone.NONE

        val (title, subtitle) = buildText(isWinner, isDraw)
        b.tvTitle.text = title
        b.tvSubtitle.text = subtitle
        b.tvDuration.text = formatDuration(durationSec)
        b.tvMoves.text = "$totalMoves 步"
        b.tvMySide.text = when (mySeat) {
            Stone.BLACK -> "执黑 (先手)"
            Stone.WHITE -> "执白 (后手)"
            else -> "观战"
        }

        // 按钮文案: 赢家/平局 -> "再来一局", 败家 -> "不服再战"
        b.btnRematch.text = if (isWinner || isDraw) "再来一局" else "不服再战"

        b.btnBackHome.setOnClickListener {
            dismiss()
            onBackHome()
        }
        b.btnRematch.setOnClickListener {
            // 切换到"等待中"状态
            b.btnRematch.isEnabled = false
            b.btnRematch.text = "已发出挑战"
            b.btnBackHome.isEnabled = false
            b.tvWaiting.visibility = View.VISIBLE
            onRematch()
        }
    }

    /** 外部可调用: 新一局开始, 自动关闭对话框 */
    fun onRematchAccepted() {
        dismiss()
    }

    private fun buildText(isWinner: Boolean, isDraw: Boolean): Pair<String, String> {
        if (isDraw) {
            return "🤝 和棋" to "势均力敌呀~"
        }
        if (isWinner) {
            return when (reason) {
                "black_win", "white_win" -> "🏆 你赢啦!" to "打得真漂亮, 心服口服~"
                "resign"                 -> "🏆 你赢啦!" to "对方认输了, 挺识趣的~"
                "timeout"                -> "🏆 你赢啦!" to "对方超时啦, 慢慢来嘛~"
                else                     -> "🏆 你赢啦!" to "耶耶耶!"
            }
        }
        return when (reason) {
            "black_win", "white_win" -> "💔 输啦" to "哼, 下次给我等着!"
            "resign"                 -> "💔 输啦" to "我...我认输了..."
            "timeout"                -> "💔 输啦" to "啊我超时了, 这次不算!"
            else                     -> "💔 输啦" to "呜呜呜, 对方欺负人~"
        }
    }

    private fun formatDuration(sec: Int): String {
        if (sec < 60) return "${sec}秒"
        val m = sec / 60
        val s = sec % 60
        return "${m}分${s}秒"
    }
}
