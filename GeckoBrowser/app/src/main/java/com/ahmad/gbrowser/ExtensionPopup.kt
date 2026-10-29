package com.ahmad.gbrowser

import android.content.Context
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoSession
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoView

private const val MIN_W = 220f
private const val MIN_H = 260f
private const val KEY_W = "ext_popup_w"
private const val KEY_H = "ext_popup_h"

/** State of the floating extension window. The size (in dp) is shared by all extensions and saved. */
class ExtPopupState(private val prefs: SharedPreferences) {
    var session by mutableStateOf<GeckoSession?>(null)
    var title by mutableStateOf("")
    var ready by mutableStateOf(false)
    var w by mutableFloatStateOf(prefs.getFloat(KEY_W, 340f))
    var h by mutableFloatStateOf(prefs.getFloat(KEY_H, 460f))
    var maxW = 400f
    var maxH = 700f
    // Window position (pixels from its default spot) and how far it may move.
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
    var limX = 0f
    var limMinY = 0f
    var limMaxY = 0f
    var gecko: GeckoView? = null

    fun scaleBy(f: Float) {
        val cw = w.coerceIn(MIN_W, maxW)
        val ch = h.coerceIn(MIN_H, maxH)
        w = (cw * f).coerceIn(MIN_W, maxW)
        h = (ch * f).coerceIn(MIN_H, maxH)
    }

    fun commit() {
        prefs.edit().putFloat(KEY_W, w).putFloat(KEY_H, h).apply()
    }
}

/** Wraps the popup's web view and turns two-finger pinches into window resizing. */
class PinchFrame(ctx: Context) : FrameLayout(ctx) {
    var scaleCb: (Float) -> Unit = {}
    var endCb: () -> Unit = {}

    private val detector = ScaleGestureDetector(
        ctx,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleCb(detector.scaleFactor)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                endCb()
            }
        }
    )

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount > 1) {
            detector.onTouchEvent(ev)
            return true
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return true
    }
}

@Composable
fun ExtensionPopupWindow(state: ExtPopupState, onClose: () -> Unit) {
    if (state.session == null) return
    val cs = MaterialTheme.colorScheme

    // GeckoView opens the popup session by itself; attach it to the view once it is open.
    LaunchedEffect(state.session) {
        val s = state.session
        state.ready = false
        state.dx = 0f
        state.dy = 0f
        if (s != null) {
            var n = 0
            while (!s.isOpen && n < 200) {
                delay(50)
                n++
            }
            state.ready = s.isOpen
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        state.maxW = maxOf(MIN_W, maxWidth.value - 24f)
        state.maxH = maxOf(MIN_H, maxHeight.value - 112f)
        val w = state.w.coerceIn(MIN_W, state.maxW)
        val h = state.h.coerceIn(MIN_H, state.maxH)
        val dens = LocalDensity.current.density
        state.limX = maxOf(0f, (maxWidth.value - w) / 2f) * dens
        state.limMinY = -maxOf(0f, maxHeight.value - h - 88f) * dens
        state.limMaxY = 88f * dens
        val ox = state.dx.coerceIn(-state.limX, state.limX)
        val oy = state.dy.coerceIn(state.limMinY, state.limMaxY)

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = cs.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .padding(bottom = 88.dp)
                    .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
                    .width(w.dp)
                    .height(h.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Empty area: hold and drag to move the window.
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        state.dx = (state.dx + drag.x).coerceIn(-state.limX, state.limX)
                                        state.dy = (state.dy + drag.y).coerceIn(state.limMinY, state.limMaxY)
                                    }
                                }
                        )
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onClose() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, "Close", Modifier.size(16.dp), tint = cs.onSurface)
                        }
                    }

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(22.dp)),
                        factory = { ctx ->
                            val gv = GeckoView(ctx)
                            gv.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                            state.gecko = gv
                            val frame = PinchFrame(ctx)
                            frame.addView(
                                gv,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            frame.scaleCb = { f -> state.scaleBy(f) }
                            frame.endCb = { state.commit() }
                            frame
                        },
                        update = { frame ->
                            val gv = frame.getChildAt(0) as GeckoView
                            val s = state.session
                            if (state.ready && s != null && gv.session !== s && s.isOpen) {
                                gv.releaseSession()
                                gv.setSession(s)
                                s.setActive(true)
                            }
                        }
                    )
                }
            }
        }
    }
}
