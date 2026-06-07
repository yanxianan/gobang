package com.gomoku.app.ui.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.gomoku.app.net.Stone
import kotlin.math.min

class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boardSize = 15
    private var board: List<List<Int>> = List(boardSize) { List(boardSize) { Stone.NONE } }
    private var lastMove: Pair<Int, Int>? = null
    private var mySeat: Int = 0
    private var turn: Int = Stone.BLACK
    private var status: String = "waiting"
    private var winner: Int = 0
    private var winLine: List<Pair<Int, Int>>? = null
    var onCellTap: ((Int, Int) -> Unit)? = null
    var isReplay: Boolean = false
    // 双击落子机制: 第一次点击"选中", 第二次点同一位置才落子
    private var selectedX: Int = -1
    private var selectedY: Int = -1
    // "已选中"提示文本, 提示用户再点一次确认
    var confirmHintProvider: (() -> String)? = null
    var onSelectionChanged: ((Boolean) -> Unit)? = null
    private var prevHasSelection: Boolean = false

    private val woodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8C887")
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A2C0F")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val blackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val whiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A2C0F")
        style = Paint.Style.FILL
    }
    private val lastMoveMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val winLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A2C0F")
        textAlign = Paint.Align.CENTER
        textSize = 24f
    }
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 80
    }
    private val selectMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1A1208")
        strokeWidth = 3f
    }

    private var hoverX: Int = -1
    private var hoverY: Int = -1

    private var cellSize = 0f
    private var padding = 0f

    fun setData(
        board: List<List<Int>>,
        lastMove: Pair<Int, Int>?,
        mySeat: Int,
        turn: Int,
        status: String,
        winner: Int,
        winLine: List<Pair<Int, Int>>?
    ) {
        this.board = board
        this.lastMove = lastMove
        this.mySeat = mySeat
        this.turn = turn
        this.status = status
        this.winner = winner
        this.winLine = winLine
        // 棋盘状态变化时清空选中
        clearSelection()
    }

    fun clearSelection() {
        val had = (selectedX >= 0)
        selectedX = -1
        selectedY = -1
        hoverX = -1
        hoverY = -1
        if (had) onSelectionChanged?.invoke(false)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        padding = min(w, h) * 0.05f
        cellSize = (min(w, h) - 2 * padding) / (boardSize - 1)
        coordPaint.textSize = cellSize * 0.35f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 棋盘背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), woodPaint)

        // 网格
        for (i in 0 until boardSize) {
            val pos = padding + i * cellSize
            canvas.drawLine(padding, pos, padding + (boardSize - 1) * cellSize, pos, linePaint)
            canvas.drawLine(pos, padding, pos, padding + (boardSize - 1) * cellSize, linePaint)
        }
        // 坐标
        for (i in 0 until boardSize) {
            val pos = padding + i * cellSize
            val label = ('A' + i).toString()
            canvas.drawText(label, pos, padding - 8f, coordPaint)
            canvas.drawText((i + 1).toString(), padding - 18f, pos + 8f, coordPaint)
        }
        // 星位
        val starPoints = listOf(
            intArrayOf(3, 3), intArrayOf(3, 11), intArrayOf(11, 3),
            intArrayOf(11, 11), intArrayOf(7, 7)
        )
        for (sp in starPoints) {
            val cx = padding + sp[0] * cellSize
            val cy = padding + sp[1] * cellSize
            canvas.drawCircle(cx, cy, 5f, pointPaint)
        }

        // 棋子
        for (y in 0 until boardSize) {
            for (x in 0 until boardSize) {
                val v = board[y][x]
                if (v == Stone.NONE) continue
                val cx = padding + x * cellSize
                val cy = padding + y * cellSize
                val r = cellSize * 0.42f
                if (v == Stone.BLACK) {
                    canvas.drawCircle(cx, cy, r, blackPaint)
                    canvas.drawCircle(cx, cy, r, blackStrokePaint)
                } else {
                    canvas.drawCircle(cx, cy, r, whitePaint)
                    canvas.drawCircle(cx, cy, r, whiteStrokePaint)
                }
            }
        }

        // 最后一手红点标记
        lastMove?.let { (x, y) ->
            val cx = padding + x * cellSize
            val cy = padding + y * cellSize
            val r = cellSize * 0.15f
            canvas.drawCircle(cx, cy, r, lastMoveMarkPaint)
        }

        // 悬停预览(未选中时显示)
        if (!isReplay && status == "playing" && mySeat != 0 && turn == mySeat && winner == 0
            && selectedX < 0
            && hoverX in 0 until boardSize && hoverY in 0 until boardSize
            && board[hoverY][hoverX] == Stone.NONE) {
            previewPaint.color = if (mySeat == Stone.BLACK) Color.BLACK else Color.WHITE
            val cx = padding + hoverX * cellSize
            val cy = padding + hoverY * cellSize
            canvas.drawCircle(cx, cy, cellSize * 0.42f, previewPaint)
        }

        // 已选中位置标记(双击落子用)
        if (selectedX in 0 until boardSize && selectedY in 0 until boardSize) {
            val cx = padding + selectedX * cellSize
            val cy = padding + selectedY * cellSize
            val r = cellSize * 0.42f
            // 画4个小方块角标, 表示"待确认"
            val s = cellSize * 0.12f
            canvas.drawRect(cx - r - s, cy - r - s, cx - r + s, cy - r + s, selectMarkPaint)
            canvas.drawRect(cx + r - s, cy - r - s, cx + r + s, cy - r + s, selectMarkPaint)
            canvas.drawRect(cx - r - s, cy + r - s, cx - r + s, cy + r + s, selectMarkPaint)
            canvas.drawRect(cx + r - s, cy + r - s, cx + r + s, cy + r + s, selectMarkPaint)
        }

        // 胜利连线
        winLine?.let { line ->
            if (line.size >= 2) {
                val p1 = PointF(
                    padding + line.first().first * cellSize,
                    padding + line.first().second * cellSize
                )
                val p2 = PointF(
                    padding + line.last().first * cellSize,
                    padding + line.last().second * cellSize
                )
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, winLinePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isReplay) return false
        if (status != "playing" || winner != 0 || mySeat == 0 || mySeat != turn) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val x = event.x
                val y = event.y
                val gx = Math.round((x - padding) / cellSize)
                val gy = Math.round((y - padding) / cellSize)
                if (gx in 0 until boardSize && gy in 0 until boardSize) {
                    hoverX = gx
                    hoverY = gy
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (hoverX in 0 until boardSize && hoverY in 0 until boardSize) {
                    if (board[hoverY][hoverX] == Stone.NONE) {
                        // 双击落子: 第一次点设置selected, 第二次点同一位置才落子
                        if (selectedX == hoverX && selectedY == hoverY) {
                            // 确认落子
                            onCellTap?.invoke(hoverX, hoverY)
                            clearSelection()
                        } else {
                            // 第一次或换位置, 标记为待确认
                            val wasEmpty = (selectedX < 0)
                            selectedX = hoverX
                            selectedY = hoverY
                            if (wasEmpty) onSelectionChanged?.invoke(true)
                        }
                    } else {
                        // 已有子的位置, 取消选择
                        clearSelection()
                    }
                }
                hoverX = -1
                hoverY = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                hoverX = -1
                hoverY = -1
                invalidate()
            }
        }
        return true
    }
}
