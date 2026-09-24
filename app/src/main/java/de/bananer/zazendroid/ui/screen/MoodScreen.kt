package de.bananer.zazendroid.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.bananer.zazendroid.R
import de.bananer.zazendroid.data.mood.MoodCheckin
import de.bananer.zazendroid.ui.viewmodel.MoodStep
import de.bananer.zazendroid.ui.viewmodel.MoodViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Angle (0–100) conversions; 0 = top, clockwise. Pure for testability. */
object MoodRing {
    fun valueToAngle(value: Int): Double = value / 100.0 * 2 * PI
    fun angleToValue(dx: Float, dy: Float): Int {
        // Screen coords: y down; angle from 12 o'clock clockwise.
        var angle = atan2(dx.toDouble(), (-dy).toDouble())
        if (angle < 0) angle += 2 * PI
        return ((angle / (2 * PI)) * 100).toInt().coerceIn(0, 100)
    }
}

/**
 * 0–100 circular setter. No numeric label, no scale marks — only the ring.
 * Tap on the ring or drag; TalkBack via increment/decrement actions.
 */
@Composable
fun MoodRingInput(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val desc = stringResource(R.string.mood_ring_cd)
    Canvas(
        modifier = modifier.size(240.dp)
            .semantics {
                contentDescription = desc
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.firstOrNull { it.pressed } ?: continue
                        down.consume()
                        val w = size.width.toFloat()
                        val c = Offset(w / 2f, w / 2f)
                        onValueChange(
                            MoodRing.angleToValue(
                                down.position.x - c.x,
                                down.position.y - c.y,
                            ),
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat()
                    val c = Offset(w / 2f, w / 2f)
                    onValueChange(MoodRing.angleToValue(change.position.x - c.x, change.position.y - c.y))
                }
            },
    ) {
        val stroke = 78f
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (value > 0) {
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = value / 100f * 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun MoodScreen(vm: MoodViewModel, onDone: () -> Unit) {
    val step by vm.step.collectAsState()
    val value by vm.value.collectAsState()
    val saving by vm.saving.collectAsState()
    val history by vm.history.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (step) {
            MoodStep.SLEEP, MoodStep.STRESS, MoodStep.MOOD -> {
                val question = when (step) {
                    MoodStep.SLEEP -> stringResource(R.string.mood_q_sleep)
                    MoodStep.STRESS -> stringResource(R.string.mood_q_stress)
                    else -> stringResource(R.string.mood_q_mood)
                }
                Text(question, style = MaterialTheme.typography.headlineSmall)
                MoodRingInput(value = value, onValueChange = vm::setValue)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step != MoodStep.SLEEP) {
                        TextButton(onClick = vm::back) {
                            Text(stringResource(R.string.action_back))
                        }
                    } else {
                        TextButton(onClick = onDone) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                    val last = step == MoodStep.MOOD
                    Button(onClick = vm::next, enabled = !saving) {
                        Text(
                            stringResource(
                                if (last) R.string.action_finish else R.string.action_next,
                            ),
                        )
                    }
                }
            }
            MoodStep.RESULT -> {
                val doneToday = history.any { it.dateEpochDay == LocalDate.now().toEpochDay() }
                Text(
                    stringResource(
                        if (doneToday) R.string.mood_history_title else R.string.mood_saved_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                MoodHistoryGraphs(history = history)
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_finish))
                }
            }
        }
    }
}

/** 14-day history as three single-metric bar graphs, gaps for missing days. */
@Composable
private fun MoodHistoryGraphs(history: List<MoodCheckin>) {
    val byDay = remember(history) { history.associateBy { it.dateEpochDay } }
    val today = remember { LocalDate.now().toEpochDay() }
    val days = remember(today) { (0..13).map { today - 13 + it } }
    val dayFmt = remember { DateTimeFormatter.ofPattern("dd") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MoodMetricGraph(
            title = stringResource(R.string.mood_legend_sleep),
            color = MaterialTheme.colorScheme.primary,
            days = days,
            valueOf = { byDay[it]?.sleep },
        )
        MoodMetricGraph(
            title = stringResource(R.string.mood_legend_stress),
            color = MaterialTheme.colorScheme.error,
            days = days,
            valueOf = { byDay[it]?.stress },
        )
        MoodMetricGraph(
            title = stringResource(R.string.mood_legend_mood),
            color = MaterialTheme.colorScheme.tertiary,
            days = days,
            valueOf = { byDay[it]?.mood },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.filterIndexed { i, _ -> i % 2 == 0 }.forEach { day ->
                Text(
                    LocalDate.ofEpochDay(day).format(dayFmt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoodMetricGraph(
    title: String,
    color: Color,
    days: List<Long>,
    valueOf: (Long) -> Int?,
) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / 14f
            val barW = slot * 0.6f
            val maxH = size.height - 10f
            // Baseline.
            drawLine(grid, Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), 2f)
            days.forEachIndexed { i, day ->
                val v = valueOf(day) ?: return@forEachIndexed
                val h = (v / 100f * maxH).coerceAtLeast(2f)
                drawRect(
                    color = color,
                    topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - 2f - h),
                    size = androidx.compose.ui.geometry.Size(barW, h),
                )
            }
        }
    }
}
