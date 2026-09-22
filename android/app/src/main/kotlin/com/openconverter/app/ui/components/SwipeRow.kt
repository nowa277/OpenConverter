package com.openconverter.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.openconverter.app.R
import com.openconverter.app.ui.theme.OcError
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeRow(
    onRemove: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val actionPx = with(LocalDensity.current) { 88.dp.toPx() }
    val shift = remember { Animatable(0f) }
    val raw = remember { floatArrayOf(0f) }
    val width = remember { floatArrayOf(1f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .fillMaxWidth()
            .clip(RectangleShape),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(OcError),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(88.dp)
                    .clickable {
                        scope.launch {
                            if (!onRemove()) {
                                shift.animateTo(0f, tween(200))
                                raw[0] = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.queue_remove),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Box(
            modifier = Modifier
                .onSizeChanged { width[0] = it.width.toFloat().coerceAtLeast(1f) }
                .offset { IntOffset(shift.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(actionPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { raw[0] = shift.value },
                        onHorizontalDrag = { _, dragAmount ->
                            raw[0] += dragAmount
                            scope.launch { shift.snapTo(SwipeMath.rubberOffset(raw[0], actionPx)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (SwipeMath.shouldCollapse(raw[0], width[0])) {
                                    if (!onRemove()) shift.animateTo(0f, tween(200))
                                    return@launch
                                }
                                val target = SwipeMath.snapTarget(shift.value, actionPx)
                                shift.animateTo(target, tween(200))
                                raw[0] = target
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                shift.animateTo(0f, tween(200))
                                raw[0] = 0f
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
