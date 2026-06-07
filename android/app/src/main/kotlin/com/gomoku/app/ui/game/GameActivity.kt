package com.gomoku.app.ui.game

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gomoku.app.R
import com.gomoku.app.databinding.ActivityGameBinding
import com.gomoku.app.databinding.ItemChatBinding
import com.gomoku.app.net.ChatItem
import com.gomoku.app.net.Stone
import com.gomoku.app.net.WebSocketClient
import kotlinx.coroutines.launch
import java.util.Date

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AS_HOST = "as_host"
        const val EXTRA_ROOM_ID = "room_id"
    }

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()
    private val chatAdapter = ChatAdapter()
    private var lastErrorShown: String? = null
    private var lastGameOverReason: String? = null
    private var hasSelectionNow: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.boardView.onCellTap = { x, y ->
            WebSocketClient.sendMove(x, y)
        }
        binding.boardView.onSelectionChanged = { hasSelection ->
            hasSelectionNow = hasSelection
            if (hasSelection) {
                binding.tvStatus.text = "已选位置, 再点一次确认落子 (点其他位置可改选)"
            } else {
                // 状态栏会在下次 render() 时被重置, 这里不需要操作
            }
        }

        binding.rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvChat.adapter = chatAdapter

        binding.btnUndo.setOnClickListener { WebSocketClient.sendUndoRequest() }
        binding.btnResign.setOnClickListener { confirmResign() }
        binding.btnDraw.setOnClickListener { WebSocketClient.sendDrawOffer() }
        binding.btnSend.setOnClickListener {
            val text = binding.etChat.text.toString().trim()
            if (text.isNotEmpty()) {
                WebSocketClient.sendChat(text)
                binding.etChat.setText("")
            }
        }
        binding.btnEmoji.isClickable = true
        binding.btnEmoji.setOnClickListener { showEmojiDialog() }

        // 表情快捷: 给前8个按钮(布局里已写死)设置点击, 第9个是"+"按钮用于显示更多
        for (i in 0 until binding.emojiBar.childCount) {
            val v = binding.emojiBar.getChildAt(i)
            if (v.id == R.id.btn_emoji) continue
            v.isClickable = true
            v.setOnClickListener {
                val emoji = (v as android.widget.TextView).text.toString()
                if (emoji.isNotEmpty()) WebSocketClient.sendEmoji(emoji)
            }
        }

        lifecycleScope.launch {
            viewModel.state.collect { render(it) }
        }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
            .setTitle("返回")
            .setMessage("确认离开对局? 离开将直接判负。")
            .setPositiveButton("确认离开") { _, _ ->
                WebSocketClient.sendLeaveRoom()
                WebSocketClient.disconnect()
                super.onBackPressed()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun render(s: GameUiState) {
        binding.tvRoomId.text = "房间号: ${s.roomId}"
        binding.tvBlackName.text = if (s.blackName.isBlank()) "等待黑方" else s.blackName
        binding.tvWhiteName.text = if (s.whiteName.isBlank()) "等待白方" else s.whiteName

        // 计时
        if (s.timeLimit > 0) {
            binding.tvBlackTime.text = formatTime(s.blackTime)
            binding.tvWhiteTime.text = formatTime(s.whiteTime)
            binding.tvBlackTime.visibility = View.VISIBLE
            binding.tvWhiteTime.visibility = View.VISIBLE
        } else {
            binding.tvBlackTime.visibility = View.GONE
            binding.tvWhiteTime.visibility = View.GONE
        }

        // 状态条
        val statusText = when {
            s.status == "waiting" && s.mySeat == Stone.BLACK && s.whiteName.isBlank() -> "等待白方加入..."
            s.status == "waiting" && s.mySeat == Stone.WHITE && s.blackName.isBlank() -> "等待黑方加入..."
            s.status == "waiting" -> "等待对手加入..."
            s.status == "playing" -> {
                if (s.turn == s.mySeat) "轮到你走" else "等待对手走子"
            }
            s.status == "finished" -> gameOverText(s)
            else -> ""
        }
        // 优先显示选中提示
        binding.tvStatus.text = if (hasSelectionNow && s.status == "playing" && s.turn == s.mySeat) {
            "已选位置, 再点一次确认落子 (点其他位置可改选)"
        } else {
            statusText
        }

        // 棋盘
        binding.boardView.setData(
            board = s.board,
            lastMove = s.lastMove?.let { it.x to it.y },
            mySeat = s.mySeat,
            turn = s.turn,
            status = s.status,
            winner = s.winner,
            winLine = null
        )

        // 按钮可用性
        val playing = s.status == "playing"
        val canMove = playing && s.mySeat != 0 && s.mySeat == s.turn && s.winner == 0
        binding.btnUndo.isEnabled = playing && s.winner == 0
        binding.btnDraw.isEnabled = playing && s.winner == 0
        binding.btnResign.isEnabled = playing && s.winner == 0
        binding.boardView.alpha = if (canMove || s.winner != 0 || s.status != "playing") 1f else 0.95f

        // 聊天
        if (chatAdapter.items != s.messages) {
            chatAdapter.items = s.messages
            chatAdapter.notifyDataSetChanged()
            if (s.messages.isNotEmpty()) {
                binding.rvChat.scrollToPosition(s.messages.size - 1)
            }
        }

        // 悔棋弹窗
        if (s.pendingUndo) {
            AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                .setTitle("悔棋请求")
                .setMessage("对方请求悔棋, 是否同意?")
                .setPositiveButton("同意") { _, _ -> WebSocketClient.sendUndoResponse(true) }
                .setNegativeButton("拒绝") { _, _ -> WebSocketClient.sendUndoResponse(false) }
                .setCancelable(false)
                .show()
            viewModel.clearError()
            // 通过更新state避免反复弹出
            // 简化: 一次性消费
        }

        // 和棋弹窗
        if (s.pendingDraw) {
            AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                .setTitle("和棋提议")
                .setMessage("对方提议和棋, 是否同意?")
                .setPositiveButton("同意") { _, _ -> WebSocketClient.sendDrawResponse(true) }
                .setNegativeButton("拒绝") { _, _ -> WebSocketClient.sendDrawResponse(false) }
                .setCancelable(false)
                .show()
        }

        // 游戏结束
        if (s.status == "finished" && s.gameOverAnnounced && lastGameOverReason != s.winReason + s.winner) {
            lastGameOverReason = s.winReason + s.winner
            val title = when (s.winner) {
                s.mySeat -> "恭喜获胜!"
                0 -> "和棋"
                else -> "惜败"
            }
            AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                .setTitle(title)
                .setMessage(gameOverText(s))
                .setPositiveButton("查看战绩") { _, _ ->
                    startActivity(Intent(this, com.gomoku.app.ui.records.RecordsActivity::class.java))
                }
                .setNegativeButton("继续") { _, _ -> }
                .setCancelable(true)
                .show()
        }

        // 对手离开
        if (s.opponentLeft) {
            Toast.makeText(this, "对手已离开", Toast.LENGTH_SHORT).show()
            viewModel.consumeOpponentLeft()
        }

        // 错误
        s.errorMessage?.let { msg ->
            if (msg != lastErrorShown) {
                lastErrorShown = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun gameOverText(s: GameUiState): String {
        val reasonText = when (s.winReason) {
            "black_win" -> "黑方五连, 获胜!"
            "white_win" -> "白方五连, 获胜!"
            "draw" -> "和棋"
            "resign" -> if (s.winner == 1) "白方认输, 黑方获胜!" else "黑方认输, 白方获胜!"
            "timeout" -> if (s.winner == 1) "白方超时, 黑方获胜!" else "黑方超时, 白方获胜!"
            else -> "对局结束"
        }
        return reasonText
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun confirmResign() {
        AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
            .setTitle("认输")
            .setMessage("确认认输?")
            .setPositiveButton("确认") { _, _ -> WebSocketClient.sendResign() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEmojiDialog() {
        val emojis = arrayOf("😀", "😂", "😍", "😎", "😡", "😱", "🤔", "👍", "👎", "🙏", "🎉", "💔")
        AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
            .setTitle("选择表情")
            .setItems(emojis) { _, which -> WebSocketClient.sendEmoji(emojis[which]) }
            .show()
    }

    private inner class ChatAdapter : RecyclerView.Adapter<ChatAdapter.VH>() {
        var items: List<ChatItem> = emptyList()
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val b = ItemChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val it = items[position]
            val time = DateFormat.format("HH:mm", Date()).toString()
            holder.b.tvName.text = it.name
            holder.b.tvText.text = if (it.isEmoji) it.text else "${it.text}  $time"
            holder.b.tvText.textSize = if (it.isEmoji) 28f else 14f
        }
        inner class VH(val b: ItemChatBinding) : RecyclerView.ViewHolder(b.root)
    }
}
