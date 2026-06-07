package com.gomoku.app.ui.game

import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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

    // 当前显示中的结算对话框
    private var gameOverDialog: GameOverDialog? = null
    // 当前显示中的 rematch 提议对话框
    private var rematchOfferDialog: RematchOfferDialog? = null
    // 对局开始的系统时间, 用于结算框显示
    private var gameStartTimestamp: Long = 0L
    // 当前对局步数(从 room_state 推算)
    @Suppress("unused")
    private var lastKnownMoves: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.boardView.onCellTap = { x, y ->
            WebSocketClient.sendMove(x, y)
        }
        binding.boardView.onSelectionChanged = { hasSelection ->
            lastSelectionState = hasSelection
            if (hasSelection) {
                binding.tvStatus.text = "已选位置, 再点一次确认落子 (点其他位置可改选)"
            }
        }

        binding.rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvChat.adapter = chatAdapter

        binding.btnUndo.setOnClickListener { WebSocketClient.sendUndoRequest() }
        binding.btnResign.setOnClickListener { confirmResign() }
        binding.btnDraw.setOnClickListener {
            // 点击求饶: 无需确认, 直接发送求和请求给对手
            WebSocketClient.sendDrawOffer()
            viewModel.markMyDrawOffer()
            Toast.makeText(this, "已向对手求饶…🥺", Toast.LENGTH_SHORT).show()
        }
        binding.btnSend.setOnClickListener {
            val text = binding.etChat.text.toString().trim()
            if (text.isNotEmpty()) {
                WebSocketClient.sendChat(text)
                binding.etChat.setText("")
            }
        }
        binding.btnEmoji.isClickable = true
        binding.btnEmoji.setOnClickListener { showEmojiDialog() }

        // 表情快捷栏: 给前 N 个按钮设置点击
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
            .setTitle("退出对局")
            .setMessage("确认离开? 这一局就判你输啦~")
            .setPositiveButton("确认离开") { _, _ ->
                WebSocketClient.sendLeaveRoom()
                WebSocketClient.disconnect()
                super.onBackPressed()
            }
            .setNegativeButton("继续下棋", null)
            .show()
    }

    private fun render(s: GameUiState) {
        binding.tvRoomId.text = "房间号: ${s.roomId}"
        binding.tvBlackName.text = if (s.blackName.isBlank()) "等待黑方" else s.blackName
        binding.tvWhiteName.text = if (s.whiteName.isBlank()) "等待白方" else s.whiteName

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
        val baseStatus = when {
            s.status == "waiting" && s.mySeat == Stone.BLACK && s.whiteName.isBlank() -> "等待白方加入..."
            s.status == "waiting" && s.mySeat == Stone.WHITE && s.blackName.isBlank() -> "等待黑方加入..."
            s.status == "waiting" -> "等待对手加入..."
            s.status == "playing" -> {
                if (s.turn == s.mySeat) "轮到你走" else "等待对手走子"
            }
            s.status == "finished" -> "本局结束"
            else -> ""
        }
        binding.tvStatus.text = if (s.status == "playing" && s.turn == s.mySeat && hasSelectionCheck()) {
            "已选位置, 再点一次确认落子 (点其他位置可改选)"
        } else {
            baseStatus
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
        binding.btnUndo.isEnabled = playing && s.winner == 0
        binding.btnDraw.isEnabled = playing && s.winner == 0
        binding.btnResign.isEnabled = playing && s.winner == 0
        binding.boardView.alpha = 1f

        // 聊天列表
        if (chatAdapter.items != s.messages) {
            chatAdapter.items = s.messages
            chatAdapter.notifyDataSetChanged()
            if (s.messages.isNotEmpty()) {
                binding.rvChat.scrollToPosition(s.messages.size - 1)
            }
        }

        // 悔棋弹窗
        if (s.pendingUndo) {
            viewModel.consumeRematchOffer() // 兜底, 防误触
            AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                .setTitle("悔棋请求")
                .setMessage("对方想悔棋, 让不让呀?")
                .setPositiveButton("让ta悔") { _, _ -> WebSocketClient.sendUndoResponse(true) }
                .setNegativeButton("不让") { _, _ -> WebSocketClient.sendUndoResponse(false) }
                .setCancelable(false)
                .show()
        }

        // 求饶(原和棋)弹窗 - 只有对方会收到
        if (s.pendingDraw) {
            AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                .setTitle("对方求饶 🥺")
                .setMessage("求求你让我一下好不好? 😭\n\n(已向你请求和棋)")
                .setPositiveButton("饶了ta") { _, _ -> WebSocketClient.sendDrawResponse(true) }
                .setNegativeButton("绝不轻饶") { _, _ -> WebSocketClient.sendDrawResponse(false) }
                .setCancelable(false)
                .show()
        }

        // 求饶响应反馈 (我方发起的求饶, 收到对方响应)
        s.drawResponseResult?.let { result ->
            if (result == "accepted") {
                AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                    .setTitle("🥹 对方心软啦!")
                    .setMessage("对方揉了揉你的头:\n\"算了算了, 这次就饶了你, 以后可不许再偷懒哦~\" 💕")
                    .setPositiveButton("知道啦~", null)
                    .setCancelable(false)
                    .show()
            } else {
                AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                    .setTitle("💢 啪! 一巴掌!")
                    .setMessage("对方冷酷地拒绝了你, 还反手给了你一巴掌 👋\n\n\"给我继续下! 绝不服输!\" 😤💥")
                    .setPositiveButton("呜呜呜", null)
                    .setCancelable(false)
                    .show()
            }
            viewModel.consumeDrawResponse()
        }

        // 记录游戏开始时间
        if (s.status == "playing" && gameStartTimestamp == 0L) {
            gameStartTimestamp = System.currentTimeMillis()
        }
        if (s.status != "playing" && s.status != "finished") {
            gameStartTimestamp = 0L
        }
        if (s.status == "finished") {
            // 步数从 room_state 的 moves 数量计算 (近似)
            // 因为 room_state payload 中我们用 lastMove, 不再含 moves 数组, 暂用 lastKnownMoves
        }

        // ============== 结算框 ==============
        if (s.status == "finished" && s.gameOverAnnounced && s.gameOverTimestamp > 0
            && gameOverDialog == null) {
            showGameOverDialog(s)
        }
        // rematch_start 触发时关闭结算框
        if (s.status == "playing" && gameOverDialog != null) {
            gameOverDialog?.onRematchAccepted()
            gameOverDialog = null
            // 也关闭 rematch 提议框(若开着)
            rematchOfferDialog?.dismiss()
            rematchOfferDialog = null
        }

        // ============== 对方要求 rematch ==============
        if (s.pendingRematchOffer && rematchOfferDialog == null) {
            showRematchOfferDialog(s)
        }

        // ============== 等待对方回应(我方已点"不服再战") ==============
        if (s.waitingMyRematch && gameOverDialog != null) {
            gameOverDialog?.let { d ->
                if (d.isShowing) {
                    // 让对话框处于等待状态
                }
            }
        }

        // ============== 一方拒绝(rematchCancelled) ==============
        if (s.rematchCancelled) {
            handleRematchCancel(s)
        }

        // 对手离开
        if (s.opponentLeft) {
            Toast.makeText(this, "对方已离开", Toast.LENGTH_SHORT).show()
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

    private var lastSelectionState: Boolean = false
    private fun hasSelectionCheck(): Boolean = lastSelectionState

    private fun showGameOverDialog(s: GameUiState) {
        val durationSec = ((System.currentTimeMillis() - gameStartTimestamp) / 1000).toInt()
        gameOverDialog = GameOverDialog(
            context = this,
            mySeat = s.mySeat,
            winner = s.winner,
            reason = s.winReason,
            durationSec = if (durationSec > 0) durationSec else 1,
            totalMoves = s.moveCount,
            onBackHome = {
                WebSocketClient.sendLeaveRoom()
                WebSocketClient.disconnect()
                startActivity(Intent(this, com.gomoku.app.ui.home.HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            },
            onRematch = {
                WebSocketClient.sendRematchRequest()
                viewModel.markRematchRequested()
            }
        )
        gameOverDialog?.show()
    }

    private fun showRematchOfferDialog(s: GameUiState) {
        val fromName = if (s.rematchOfferFrom == Stone.BLACK) s.blackName else s.whiteName
        rematchOfferDialog = RematchOfferDialog(
            context = this,
            fromSeat = s.rematchOfferFrom,
            fromName = fromName,
            onAccept = {
                WebSocketClient.sendRematchResponse(true)
                viewModel.consumeRematchOffer()
                Toast.makeText(this, "已接下挑战!", Toast.LENGTH_SHORT).show()
            },
            onReject = {
                WebSocketClient.sendRematchResponse(false)
                viewModel.consumeRematchOffer()
            }
        )
        rematchOfferDialog?.show()
    }

    private fun handleRematchCancel(s: GameUiState) {
        val msg = when (s.rematchCancelReason) {
            "rejected" -> {
                if (s.rematchRejectCount >= 2) {
                    "对方已两次拒绝挑战, 房间结束~"
                } else {
                    "对方拒绝挑战 (${s.rematchRejectCount}/2 次)"
                }
            }
            "too_many_rejects" -> "对方已两次拒绝, 房间结束~"
            "opponent_offline" -> "对方不在线, 房间结束~"
            else -> "对方拒绝了, 房间结束~"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        viewModel.consumeRematchCancel()
        // 关闭弹窗
        rematchOfferDialog?.dismiss()
        rematchOfferDialog = null
        gameOverDialog?.dismiss()
        gameOverDialog = null
        // 如果还能再求一次(只拒绝1次), 给"求对方再战"按钮
        if (s.rematchRejectCount == 1) {
            AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                .setTitle("😢 ta 不愿意...")
                .setMessage("要不再求一次对方陪你下棋?")
                .setPositiveButton("再求一次") { _, _ ->
                    // 重新发 rematch_request, 服务端会清除本座位旧的请求
                    WebSocketClient.sendRematchRequest()
                    // 重新弹"等待回应"提示
                    AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                        .setTitle("⏳ 已再次发出")
                        .setMessage("等待对方回应中…")
                        .setPositiveButton("好的") { d, _ -> d.dismiss() }
                        .setCancelable(false)
                        .show()
                }
                .setNegativeButton("算了吧") { _, _ ->
                    WebSocketClient.sendLeaveRoom()
                    WebSocketClient.disconnect()
                    startActivity(Intent(this, com.gomoku.app.ui.home.HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
                .setCancelable(false)
                .show()
        } else {
            // 两次拒绝, 直接退出
            AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
                .setTitle("💔 算啦算啦")
                .setMessage("对方两次都拒绝啦, 我们回主页吧~")
                .setPositiveButton("回主页") { _, _ ->
                    WebSocketClient.sendLeaveRoom()
                    WebSocketClient.disconnect()
                    startActivity(Intent(this, com.gomoku.app.ui.home.HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun confirmResign() {
        AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
            .setTitle("认输")
            .setMessage("确认认输吗? 真的认输吗?")
            .setPositiveButton("认输") { _, _ -> WebSocketClient.sendResign() }
            .setNegativeButton("再战一会", null)
            .show()
    }

    private fun confirmBeg() {
        // 已废弃: 点击求饶不再需要确认, 直接发送
    }

    private fun showEmojiDialog() {
        val emojis = arrayOf("😀", "😂", "🥰", "😘", "🤗", "💕", "💋", "😡", "😱", "🤔", "👍", "👎", "🙏", "🎉", "😴", "🤨", "🥺", "💪", "🙄", "😭", "🤭")
        AlertDialog.Builder(this, R.style.Theme_Gomoku_Dialog)
            .setTitle("选个表情")
            .setItems(emojis) { _, which -> WebSocketClient.sendEmoji(emojis[which]) }
            .show()
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
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
