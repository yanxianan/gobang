package com.gomoku.app.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gomoku.app.databinding.ActivitySettingsBinding
import com.gomoku.app.util.Prefs

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etHost.setText(Prefs.serverHost)
        binding.btnSave.setOnClickListener {
            val host = binding.etHost.text.toString().trim()
            if (host.isBlank()) {
                Toast.makeText(this, "服务器地址不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.serverHost = host
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
        binding.btnBack.setOnClickListener { finish() }
    }
}
