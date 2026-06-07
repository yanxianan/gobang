package com.gomoku.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gomoku.app.databinding.ActivityHomeBinding
import com.gomoku.app.net.ApiService
import com.gomoku.app.net.WebSocketClient
import com.gomoku.app.ui.game.GameActivity
import com.gomoku.app.ui.records.RecordsActivity
import com.gomoku.app.ui.settings.SettingsActivity
import com.gomoku.app.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etNickname.setText(Prefs.nickname)

        binding.btnCreate.setOnClickListener { onCreate() }
        binding.btnJoin.setOnClickListener { onJoin() }
        binding.btnRecords.setOnClickListener {
            if (Prefs.nickname.isBlank()) {
                toast("请先填写昵称")
                return@setOnClickListener
            }
            startActivity(Intent(this, RecordsActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (Prefs.serverHost.isBlank()) {
            // 服务器未配置(理论上不会发生, 因为有默认地址)
        }

        WebSocketClient.connect()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun onCreate() {
        val nick = binding.etNickname.text.toString().trim()
        if (nick.isBlank()) { toast("请填写昵称"); return }
        if (Prefs.serverHost.isBlank()) { toast("请先在设置中配置服务器地址"); return }
        Prefs.nickname = nick
        val timeLimit = when (binding.rgTime.checkedRadioButtonId) {
            com.gomoku.app.R.id.rb_30s -> 30
            com.gomoku.app.R.id.rb_60s -> 60
            com.gomoku.app.R.id.rb_unlimited -> 0
            else -> 0
        }
        Prefs.defaultTimeLimit = timeLimit
        WebSocketClient.connect()
        WebSocketClient.sendCreateRoom(nick, timeLimit)
        // 短暂延时再跳转, 确保连接建立
        lifecycleScope.launch {
            // 等待 room_created 事件在 GameActivity 处理
            startActivity(Intent(this@HomeActivity, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_AS_HOST, true)
            })
        }
    }

    private fun onJoin() {
        val nick = binding.etNickname.text.toString().trim()
        val roomId = binding.etRoomId.text.toString().trim().uppercase()
        if (nick.isBlank()) { toast("请填写昵称"); return }
        if (roomId.isBlank()) { toast("请输入房间号"); return }
        if (Prefs.serverHost.isBlank()) { toast("请先在设置中配置服务器地址"); return }
        Prefs.nickname = nick
        WebSocketClient.connect()
        WebSocketClient.sendJoinRoom(roomId, nick)
        startActivity(Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_AS_HOST, false)
            putExtra(GameActivity.EXTRA_ROOM_ID, roomId)
        })
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
