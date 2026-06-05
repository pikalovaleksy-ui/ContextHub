package com.example.project.ui.zones

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.project.model.Target
import com.example.project.model.Vertex
import com.example.project.model.Zone
import kotlin.math.min
import kotlin.math.sqrt

class MapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Drawing state
    private var offsetX = 0f
    private var offsetY = 0f
    private var scale = 0.15f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private val scaleDetector: ScaleGestureDetector

    private val gridPaint = Paint().apply {
        color = Color.argb(40, 0, 0, 0)
        strokeWidth = 1f
    }
    private val axisPaint = Paint().apply {
        color = Color.argb(80, 0, 0, 0)
        strokeWidth = 2f
    }
    private val gridTextPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        textSize = 24f
    }
    private val radarPaint = Paint().apply {
        color = Color.argb(255, 103, 80, 164)
        style = Paint.Style.FILL
    }
    private val radarStrokePaint = Paint().apply {
        color = Color.argb(255, 103, 80, 164)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val targetPaint = Paint().apply {
        color = Color.argb(255, 233, 30, 99)
        style = Paint.Style.FILL
    }
    private val targetStrokePaint = Paint().apply {
        color = Color.argb(200, 233, 30, 99)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val editingLinePaint = Paint().apply {
        color = Color.argb(200, 76, 175, 80)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    private val editingFillPaint = Paint().apply {
        color = Color.argb(40, 76, 175, 80)
        style = Paint.Style.FILL
    }
    private val vertexPaint = Paint().apply {
        color = Color.argb(255, 76, 175, 80)
        style = Paint.Style.FILL
    }
    private val vertexStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
    }
    private val labelBgPaint = Paint().apply {
        color = Color.argb(180, 103, 80, 164)
        style = Paint.Style.FILL
    }
    private val coordsTextPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        textSize = 22f
        isAntiAlias = true
    }
    private val statusPaint = Paint().apply {
        color = Color.argb(255, 0, 0, 0)
        textSize = 28f
        isAntiAlias = true
        isFakeBoldText = true
    }
    // Data
    var targets: List<Target> = emptyList()
        set(value) { field = value; invalidate() }
    var savedZones: List<Zone> = emptyList()
        set(value) { field = value; invalidate() }
    var editingVertices: List<Vertex> = emptyList()
        set(value) { field = value; invalidate() }
    var editingZoneName: String = ""
    var editingZoneColor: Int = 0xFF6750A4.toInt()
    var targetLabels: Map<Int, String> = emptyMap()
    var onVertexAdded: ((Vertex) -> Unit)? = null
    var onMapTap: ((Float, Float) -> Unit)? = null
    var onZoneTapped: ((Zone?) -> Unit)? = null
    var selectedZoneId: String? = null
        set(value) { field = value; invalidate() }
    var isEditMode: Boolean = false
    var isInteractive: Boolean = true
    var showGridCoordinates: Boolean = true
    var statusText: String = ""
        set(value) { field = value; invalidate() }

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale *= detector.scaleFactor
                scale = scale.coerceIn(0.02f, 1.5f)
                invalidate()
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return false
        scaleDetector.onTouchEvent(event)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (sqrt(dx * dx + dy * dy) > 10f) isDragging = true
                    offsetX += dx
                    offsetY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging && !scaleDetector.isInProgress) {
                    val world = screenToWorld(event.x, event.y)
                    val tappedZone = findZoneAt(world.x.toInt(), world.y.toInt())
                    if (tappedZone != null) {
                        onZoneTapped?.invoke(tappedZone)
                    } else if (isEditMode && selectedZoneId != null) {
                        // Tap on empty space while zone selected → deselect
                        onZoneTapped?.invoke(null)
                    } else if (isEditMode) {
                        val v = Vertex(world.x.toInt(), world.y.toInt())
                        onVertexAdded?.invoke(v)
                    } else {
                        onMapTap?.invoke(world.x, world.y)
                    }
                }
                isDragging = false
            }
        }
        return true
    }

    private fun findZoneAt(x: Int, y: Int): Zone? {
        for (zone in savedZones) {
            if (zone.vertices.size < 3) continue
            if (isPointInPolygon(x, y, zone.vertices)) {
                return zone
            }
        }
        return null
    }

    private fun isPointInPolygon(px: Int, py: Int, vertices: List<Vertex>): Boolean {
        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val xi = vertices[i].x
            val yi = vertices[i].y
            val xj = vertices[j].x
            val yj = vertices[j].y
            if ((yi > py) != (yj > py) &&
                px < (xj - xi) * (py - yi) / (yj - yi) + xi
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun worldToScreen(worldX: Float, worldY: Float): PointF {
        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY
        return PointF(cx + worldX * scale, cy - worldY * scale)
    }

    private fun screenToWorld(screenX: Float, screenY: Float): PointF {
        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY
        return PointF((screenX - cx) / scale, -(screenY - cy) / scale)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f + offsetX
        val cy = height / 2f + offsetY

        drawGrid(canvas, cx, cy)
        drawSavedZones(canvas)
        if (editingVertices.isNotEmpty()) {
            drawEditingZone(canvas)
        }
        drawTargets(canvas, cx, cy)
        drawRadarOrigin(canvas, cx, cy)
        drawStatus(canvas)
    }

    private fun drawStatus(canvas: Canvas) {
        if (statusText.isNotEmpty()) {
            canvas.drawText(statusText, 16f, height - 16f, statusPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float) {
        val step = 500
        val stepPx = step * scale
        if (stepPx < 10f) return
        val halfW = (width / 2f / stepPx + 2).toInt()
        val halfH = (height / 2f / stepPx + 2).toInt()

        for (i in -halfW..halfW) {
            val x = cx + i * stepPx
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
        for (i in -halfH..halfH) {
            val y = cy + i * stepPx
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        canvas.drawLine(0f, cy, width.toFloat(), cy, axisPaint)
        canvas.drawLine(cx, 0f, cx, height.toFloat(), axisPaint)

        if (showGridCoordinates) {
            for (i in -halfW..halfW) {
                val x = cx + i * stepPx
                canvas.drawText("${i * 500}mm", x + 4f, cy + 20f, gridTextPaint)
            }
            for (i in -halfH..halfH) {
                if (i == 0) continue
                val y = cy + i * stepPx
                canvas.drawText("${i * 500}mm", cx + 4f, y - 4f, gridTextPaint)
            }
        }
    }

    private fun drawRadarOrigin(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, 12f, radarPaint)
        canvas.drawCircle(cx, cy, 12f, radarStrokePaint)
        canvas.drawText("Радар", cx + 16f, cy + 6f, gridTextPaint)
    }

    private fun drawTargets(canvas: Canvas, cx: Float, cy: Float) {
        for (t in targets) {
            val sp = worldToScreen(t.x.toFloat(), t.y.toFloat())
            canvas.drawCircle(sp.x, sp.y, 18f, targetStrokePaint)
            canvas.drawCircle(sp.x, sp.y, 14f, targetPaint)

            val label = targetLabels[t.id] ?: "T${t.id}"
            val vStr = "${t.speed}мм/c"
            canvas.drawText(label, sp.x + 20f, sp.y - 6f, coordsTextPaint)
            canvas.drawText(vStr, sp.x + 20f, sp.y + 18f, coordsTextPaint)
            canvas.drawText(
                "(${t.x}, ${t.y})",
                sp.x - 30f,
                sp.y - 24f,
                coordsTextPaint
            )
        }
    }

    private fun drawSavedZones(canvas: Canvas) {
        for (zone in savedZones) {
            if (zone.vertices.size < 3) continue
            val zoneColor = if (zone.color != 0) zone.color else 0xFF6750A4.toInt()
            val isActive = zone.enabled
            val fillAlpha = if (isActive) 60 else 20
            val strokeAlpha = if (isActive) 200 else 80
            val fillPaint = Paint().apply {
                this.color = Color.argb(fillAlpha, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
                style = Paint.Style.FILL
            }
            val strokePaint = Paint().apply {
                this.color = Color.argb(strokeAlpha, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }

            val path = Path()
            val first = worldToScreen(
                zone.vertices.first().x.toFloat(),
                zone.vertices.first().y.toFloat()
            )
            path.moveTo(first.x, first.y)
            for (i in 1 until zone.vertices.size) {
                val v = worldToScreen(
                    zone.vertices[i].x.toFloat(),
                    zone.vertices[i].y.toFloat()
                )
                path.lineTo(v.x, v.y)
            }
            path.close()

            // Selection: highlight with zone color
            val isSelected = zone.id == selectedZoneId
            if (isSelected) {
                canvas.drawPath(path, Paint().apply {
                    color = Color.argb(255, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                    isAntiAlias = true
                })
                canvas.drawPath(path, Paint().apply {
                    color = Color.argb(120, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
            }

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)

            val labelBgColor = if (isActive) Color.argb(180, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
                              else Color.argb(100, Color.red(zoneColor), Color.green(zoneColor), Color.blue(zoneColor))
            val bgPaint = Paint().apply {
                this.color = labelBgColor
                style = Paint.Style.FILL
            }
            val labelPos = polygonCenter(zone.vertices)
            val lp = worldToScreen(labelPos.first, labelPos.second)
            val textW = labelPaint.measureText(zone.name)
            val rect = android.graphics.RectF(
                lp.x - textW / 2f - 8f,
                lp.y - 20f,
                lp.x + textW / 2f + 8f,
                lp.y + 10f
            )
            canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
            canvas.drawText(zone.name, lp.x - textW / 2f, lp.y + 4f, labelPaint)
        }
    }

    private fun drawEditingZone(canvas: Canvas) {
        val pts = editingVertices.map {
            worldToScreen(it.x.toFloat(), it.y.toFloat())
        }
        var color = editingZoneColor
        val fillPaint = Paint().apply {
            this.color = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            this.color = Color.argb(200, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }
        val vtxPaint = Paint().apply {
            this.color = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.FILL
        }
        val vtxStroke = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        if (pts.size >= 2) {
            val path = Path()
            path.moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            if (pts.size >= 3) {
                path.close()
                canvas.drawPath(path, fillPaint)
            }
            val dashPath = Path()
            dashPath.moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) dashPath.lineTo(pts[i].x, pts[i].y)
            canvas.drawPath(dashPath, linePaint)
        }
        for (pt in pts) {
            canvas.drawCircle(pt.x, pt.y, 12f, vtxStroke)
            canvas.drawCircle(pt.x, pt.y, 8f, vtxPaint)
        }
        if (editingZoneName.isNotEmpty() && pts.isNotEmpty()) {
            val textW = labelPaint.measureText(editingZoneName)
            val first = pts.first()
            val rect = android.graphics.RectF(
                first.x - textW / 2f - 8f,
                first.y - 40f,
                first.x + textW / 2f + 8f,
                first.y - 10f
            )
            val bg = Paint().apply {
                this.color = Color.argb(180, Color.red(color), Color.green(color), Color.blue(color))
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(rect, 8f, 8f, bg)
            canvas.drawText(editingZoneName, first.x - textW / 2f, first.y - 16f, labelPaint)
        }
    }

    private fun polygonCenter(vertices: List<Vertex>): Pair<Float, Float> {
        if (vertices.isEmpty()) return 0f to 0f
        val cx = vertices.map { it.x }.average().toFloat()
        val cy = vertices.map { it.y }.average().toFloat()
        return cx to cy
    }

    fun fitToView() {
        val maxCoord = 3000f
        val sx = width / (2f * maxCoord)
        val sy = height / (2f * maxCoord)
        scale = min(sx, sy) * 0.7f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    fun resetView() {
        scale = 0.15f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }
}
