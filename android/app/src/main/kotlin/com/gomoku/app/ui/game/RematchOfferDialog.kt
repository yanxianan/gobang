package com.gomoku.app.ui.game

import android.app.AlertDialog
import android.content.Context
import com.gomoku.app.R

/**
 * 对方要求"再战"时弹出的对话框 (只有对方会收到此弹窗, 发送方不会)
 * - 接下挑战: 同意 rematch
 * - 拒绝:     拒绝 rematch
 */
class RematchOfferDialog(
    context: Context,
    private val fromSeat: Int,
    private val fromName: String,
    private val onAccept: () -> Unit,
    private val onReject: () -> Unit
) {
    private val dialog: AlertDialog

    init {
        val builder = AlertDialog.Builder(context, R.style.Theme_Gomoku_Dialog)
            .setTitle("⚔️ 对方要再战!")
            .setMessage("$fromName 想要再来一局, 要接下挑战吗?")
            .setPositiveButton("接下挑战") { _, _ -> onAccept() }
            .setNegativeButton("拒绝") { _, _ -> onReject() }
            .setCancelable(false)
        dialog = builder.create()
    }

    fun show() = dialog.show()
    fun dismiss() = dialog.dismiss()
    val isShowing: Boolean get() = dialog.isShowing
}
