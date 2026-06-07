package com.gomoku.app.ui.records

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gomoku.app.databinding.ActivityRecordsBinding
import com.gomoku.app.databinding.ItemRecordBinding
import com.gomoku.app.net.ApiService
import com.gomoku.app.net.Stone
import com.gomoku.app.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordsBinding
    private val items = ArrayList<ApiService.GameSummary>()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rvRecords.layoutManager = LinearLayoutManager(this)
        binding.rvRecords.adapter = adapter
        binding.btnBack.setOnClickListener { finish() }
        load()
    }

    private fun load() {
        binding.tvEmpty.visibility = View.GONE
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { ApiService.listGames(Prefs.nickname, 50, 0) }
            items.clear()
            items.addAll(list)
            adapter.notifyDataSetChanged()
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val g = items[position]
            holder.b.tvRoom.text = "房间: ${g.roomId}"
            holder.b.tvPlayers.text = "${g.blackName}  VS  ${g.whiteName}"
            holder.b.tvResult.text = resultText(g)
            holder.b.tvTime.text = g.endedAt.replace("T", " ").take(19)
            holder.itemView.setOnClickListener {
                val intent = Intent(this@RecordsActivity, ReplayActivity::class.java)
                intent.putExtra(ReplayActivity.EXTRA_ID, g.id)
                startActivity(intent)
            }
        }
        inner class VH(val b: ItemRecordBinding) : RecyclerView.ViewHolder(b.root)
    }

    private fun resultText(g: ApiService.GameSummary): String {
        val myName = Prefs.nickname
        return when {
            g.reason == "draw" -> "和棋"
            g.winner == Stone.BLACK && g.blackName == myName -> "胜(执黑)"
            g.winner == Stone.WHITE && g.whiteName == myName -> "胜(执白)"
            g.winner == Stone.BLACK && g.whiteName == myName -> "负(执白)"
            g.winner == Stone.WHITE && g.blackName == myName -> "负(执黑)"
            else -> "胜负未定"
        }
    }
}
