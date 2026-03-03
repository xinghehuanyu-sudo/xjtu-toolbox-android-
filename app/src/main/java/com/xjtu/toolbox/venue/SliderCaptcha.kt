package com.xjtu.toolbox.venue

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private const val TAG = "SliderCaptcha"

/**
 * 滑动轨迹中的单个点
 */
data class TrackPoint(
    val x: Int,
    val y: Int,
    val type: String,  // "down", "move", "up"
    val t: Long        // 相对时间戳（ms）
)

/**
 * 滑动验证码结果
 */
data class SliderResult(
    val bgImageWidth: Int,
    val bgImageHeight: Int,
    val sliderImageWidth: Int,
    val sliderImageHeight: Int,
    val startSlidingTime: String,   // ISO 8601
    val entSlidingTime: String,     // ISO 8601
    val trackList: List<TrackPoint>
) {
    fun toJson(): String = Gson().toJson(this)
}

/**
 * 滑动拼图验证码组件
 *
 * @param backgroundImageBase64 背景图 data URI (data:image/jpeg;base64,...)
 * @param sliderImageBase64 滑块图 data URI (data:image/png;base64,...)
 * @param bgOriginalWidth 背景图原始宽度 (px)
 * @param bgOriginalHeight 背景图原始高度 (px)
 * @param sliderOriginalWidth 滑块原始宽度 (px)
 * @param sliderOriginalHeight 滑块原始高度 (px)
 * @param onSlideComplete 滑动完成回调，返回 SliderResult
 */
@Composable
fun SliderCaptchaView(
    backgroundImageBase64: String,
    sliderImageBase64: String,
    bgOriginalWidth: Int,
    bgOriginalHeight: Int,
    sliderOriginalWidth: Int,
    sliderOriginalHeight: Int,
    onSlideComplete: (SliderResult) -> Unit
) {
    // 解码图片
    val bgBitmap = remember(backgroundImageBase64) {
        decodeBase64Image(backgroundImageBase64)
    }
    val sliderBitmap = remember(sliderImageBase64) {
        decodeBase64Image(sliderImageBase64)
    }

    if (bgBitmap == null || sliderBitmap == null) {
        Text("验证码加载失败", color = MiuixTheme.colorScheme.error)
        return
    }

    val density = LocalDensity.current

    // 显示宽度 = 260dp（与网页端一致），高度按比例
    val displayWidthDp = 260.dp
    val displayHeightDp = with(density) {
        (displayWidthDp.toPx() * bgOriginalHeight / bgOriginalWidth).toDp()
    }
    val displayWidthPx = with(density) { displayWidthDp.toPx() }

    // 滑块显示尺寸（按相同比例缩放）
    val scaleRatio = displayWidthPx / bgOriginalWidth
    val sliderDisplayWidthPx = sliderOriginalWidth * scaleRatio
    val sliderDisplayHeightPx = sliderOriginalHeight * scaleRatio
    val sliderDisplayWidthDp = with(density) { sliderDisplayWidthPx.toDp() }

    // 最大滑动距离
    val maxSlideX = displayWidthPx - sliderDisplayWidthPx

    // 服务器期望的显示坐标系参数 (260-based, 与网页 CSS px 一致)
    val serverBgWidth = 260
    val serverSliderHeight = (sliderOriginalHeight * 260.0 / bgOriginalWidth).roundToInt()

    // 滑动状态
    var offsetX by remember { mutableFloatStateOf(0f) }
    var cumulativeY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val trackPoints = remember { mutableListOf<TrackPoint>() }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    // 模拟用户看到验证码后到开始拖动的延迟 (800-1500ms)
    val captchaViewDelay = remember { (800..1500).random().toLong() }

    Column(
        modifier = Modifier.width(displayWidthDp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 拼图区域
        Box(
            modifier = Modifier
                .width(displayWidthDp)
                .height(displayHeightDp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            // 背景图
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = "验证码背景",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )

            // 滑块
            Image(
                bitmap = sliderBitmap.asImageBitmap(),
                contentDescription = "滑块",
                modifier = Modifier
                    .size(sliderDisplayWidthDp, displayHeightDp)
                    .offset { IntOffset(offsetX.roundToInt(), 0) },
                contentScale = ContentScale.FillBounds
            )
        }

        Spacer(Modifier.height(12.dp))

        // 滑动条
        Box(
            modifier = Modifier
                .width(displayWidthDp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
        ) {
            // 提示文字
            if (!isDragging && offsetX == 0f) {
                Text(
                    "向右拖动滑块完成验证",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 滑块按钮
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .size(40.dp)
                    .shadow(2.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragStartTime = System.currentTimeMillis()
                                cumulativeY = 0f
                                trackPoints.clear()
                                trackPoints.add(
                                    TrackPoint(0, 0, "down", captchaViewDelay)
                                )
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val newX = (offsetX + dragAmount.x).coerceIn(0f, maxSlideX)
                                offsetX = newX
                                cumulativeY += dragAmount.y

                                // 转换为 260 显示坐标系（服务器期望的坐标）
                                val displayX = (newX * serverBgWidth / displayWidthPx).roundToInt()
                                val displayY = (cumulativeY * serverBgWidth / displayWidthPx).roundToInt()
                                trackPoints.add(
                                    TrackPoint(
                                        displayX, displayY, "move",
                                        captchaViewDelay + (System.currentTimeMillis() - dragStartTime)
                                    )
                                )
                            },
                            onDragEnd = {
                                isDragging = false
                                val displayX = (offsetX * serverBgWidth / displayWidthPx).roundToInt()
                                val displayY = (cumulativeY * serverBgWidth / displayWidthPx).roundToInt()
                                trackPoints.add(
                                    TrackPoint(
                                        displayX, displayY, "up",
                                        captchaViewDelay + (System.currentTimeMillis() - dragStartTime)
                                    )
                                )

                                val startTime = java.time.Instant.ofEpochMilli(dragStartTime)
                                val endTime = java.time.Instant.now()
                                val fmt = java.time.format.DateTimeFormatter.ISO_INSTANT

                                val result = SliderResult(
                                    bgImageWidth = serverBgWidth,
                                    bgImageHeight = 0,
                                    sliderImageWidth = 0,
                                    sliderImageHeight = serverSliderHeight,
                                    startSlidingTime = fmt.format(startTime),
                                    entSlidingTime = fmt.format(endTime),
                                    trackList = trackPoints.toList()
                                )
                                Log.d(TAG, "Slide complete: displayX=$displayX, points=${trackPoints.size}")
                                onSlideComplete(result)
                            },
                            onDragCancel = {
                                isDragging = false
                                offsetX = 0f
                                trackPoints.clear()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "→",
                    color = MiuixTheme.colorScheme.onPrimary,
                    style = MiuixTheme.textStyles.body1
                )
            }
        }
    }
}

/**
 * 重置滑块位置（验证失败后调用）
 */
@Composable
fun rememberSliderReset(): MutableState<Boolean> = remember { mutableStateOf(false) }

/**
 * 解码 data URI base64 图片
 */
private fun decodeBase64Image(dataUri: String): android.graphics.Bitmap? {
    return try {
        val base64Str = dataUri.substringAfter("base64,")
        val bytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to decode image", e)
        null
    }
}

/**
 * 自动解题：Alpha 轮廓 + 背景边缘匹配算法
 * 1. 提取滑块的 alpha 轮廓（不透明↔透明的边界像素 = 拼图形状）
 * 2. 对背景图做 Sobel 边缘检测（缺口边界产生强边缘）
 * 3. 横向滑动 alpha 轮廓模板，找背景边缘响应最大的位置
 *
 * @return 目标 x 坐标（260 显示坐标系），null 表示无法自动解题
 */
fun autoSolveCaptcha(
    backgroundImageBase64: String,
    sliderImageBase64: String,
    bgOriginalWidth: Int,
    bgOriginalHeight: Int
): Int? {
    return try {
        val bgBmp = decodeBase64Image(backgroundImageBase64) ?: return null
        val slBmp = decodeBase64Image(sliderImageBase64) ?: return null

        val bw = bgBmp.width
        val bh = bgBmp.height
        val sw = slBmp.width
        val sh = slBmp.height

        // ── 1. 背景灰度化 ──
        val bgGray = IntArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            val p = bgBmp.getPixel(x, y)
            bgGray[y * bw + x] = (p shr 16 and 0xFF) * 77 + (p shr 8 and 0xFF) * 150 + (p and 0xFF) * 29 shr 8
        }

        // ── 2. 提取滑块不透明度 ──
        val slOpaque = BooleanArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            slOpaque[y * sw + x] = (slBmp.getPixel(x, y) ushr 24) > 128
        }

        // ── 3. Sobel 边缘检测（背景） ──
        val bgEdge = IntArray(bw * bh)
        for (y in 1 until bh - 1) for (x in 1 until bw - 1) {
            val gx = -bgGray[(y - 1) * bw + x - 1] + bgGray[(y - 1) * bw + x + 1] -
                2 * bgGray[y * bw + x - 1] + 2 * bgGray[y * bw + x + 1] -
                bgGray[(y + 1) * bw + x - 1] + bgGray[(y + 1) * bw + x + 1]
            val gy = -bgGray[(y - 1) * bw + x - 1] + bgGray[(y + 1) * bw + x - 1] -
                2 * bgGray[(y - 1) * bw + x] + 2 * bgGray[(y + 1) * bw + x] -
                bgGray[(y - 1) * bw + x + 1] + bgGray[(y + 1) * bw + x + 1]
            bgEdge[y * bw + x] = kotlin.math.abs(gx) + kotlin.math.abs(gy)
        }

        // ── 4. 提取滑块 Alpha 轮廓（形状边界） ──
        // 不透明像素且 4-邻域至少有一个透明像素 → 形状边界点
        data class ContourPoint(val sx: Int, val sy: Int)
        val contourPoints = mutableListOf<ContourPoint>()

        for (y in 1 until sh - 1) for (x in 1 until sw - 1) {
            if (!slOpaque[y * sw + x]) continue
            val hasTransparentNeighbor =
                !slOpaque[(y - 1) * sw + x] ||
                !slOpaque[(y + 1) * sw + x] ||
                !slOpaque[y * sw + (x - 1)] ||
                !slOpaque[y * sw + (x + 1)]
            if (hasTransparentNeighbor) {
                contourPoints.add(ContourPoint(x, y))
            }
        }

        if (contourPoints.isEmpty()) {
            Log.w(TAG, "autoSolve: no contour points in slider")
            return null
        }

        // ── 5. 模板匹配：Alpha 轮廓 × 背景边缘 ──
        // 缺口不会在最左侧（约 15%）
        val searchStart = (bw * 0.15).toInt()
        val searchEnd = bw - sw
        var bestX = searchStart
        var bestScore = -1.0

        for (x in searchStart..searchEnd) {
            var score = 0L
            for (cp in contourPoints) {
                val bx = x + cp.sx
                val by = cp.sy
                if (bx in 0 until bw && by in 0 until bh) {
                    score += bgEdge[by * bw + bx]
                }
            }
            val norm = score.toDouble() / contourPoints.size
            if (norm > bestScore) {
                bestScore = norm
                bestX = x
            }
        }

        // 转换到 260 显示坐标系
        val displayX = (bestX * 260.0 / bgOriginalWidth).roundToInt()
        Log.d(TAG, "autoSolve: bestX=$bestX (orig), displayX=$displayX, score=${bestScore.toInt()}, contourPoints=${contourPoints.size}")
        displayX
    } catch (e: Exception) {
        Log.e(TAG, "autoSolve failed", e)
        null
    }
}

/**
 * 生成模拟人类滑动轨迹（自动解题时用）
 * @param targetX 目标 x 坐标（显示坐标系）
 * @param duration 总滑动时间 (ms)
 */
fun generateHumanLikeTrack(targetX: Int, duration: Long = 1200L): List<TrackPoint> {
    val baseT = (800L..1500L).random()
    val points = mutableListOf<TrackPoint>()
    points.add(TrackPoint(0, 0, "down", baseT))

    val steps = (duration / 16).toInt()  // ~60fps
    var t = baseT + (100L..200L).random()
    for (i in 1..steps) {
        val progress = i.toFloat() / steps
        // 缓动函数：先快后慢 (easeOutCubic)
        val eased = 1 - (1 - progress) * (1 - progress) * (1 - progress)
        val x = (targetX * eased).roundToInt()
        val y = (-2..2).random()  // 微小 y 轴抖动
        points.add(TrackPoint(x, y, "move", t))
        t += (12L..20L).random()
    }

    points.add(TrackPoint(targetX, (-3..0).random(), "up", t + (200L..500L).random()))
    return points
}
