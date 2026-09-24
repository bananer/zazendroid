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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
                MoodHistoryGraph(history = history)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                    MoodLegend()
                }
            }
        }
    }
}

@Composable
private fun MoodLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.mood_legend_sleep), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.mood_legend_stress), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.mood_legend_mood), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** 14-day grouped bar graph: 3 bars/day (sleep/stress/mood), gaps for missing days. */
@Composable
private fun MoodHistoryGraph(history: List<MoodCheckin>) {
    val byDay = remember(history) { history.associateBy { it.dateEpochDay } }
    val today = remember { LocalDate.now().toEpochDay() }
    val days = remember(today) { (0..13).map { today - 13 + it } }
    val sleepColor = MaterialTheme.colorScheme.primary
    val stressColor = MaterialTheme.colorScheme.error
    val moodColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val dayFmt = remember { DateTimeFormatter.ofPattern("dd") }
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(vertical = 8.dp)) {
            val barW = size.width / 14f / 4f
            val maxH = size.height - 40f
            // Baseline.
            drawLine(grid, Offset(0f, size.height - 30f), Offset(size.width, size.height - 30f), 2f)
            days.forEachIndexed { i, day ->
                val x0 = i * size.width / 14f
                val e = byDay[day]
                if (e != null) {
                    val bars = listOf(e.sleep to sleepColor, e.stress to stressColor, e.mood to moodColor)
                    bars.forEachIndexed { b, (v, c) ->
                        val h = (v / 100f * maxH).coerceAtLeast(2f)
                        drawRect(
                            color = c,
                            topLeft = Offset(x0 + b * barW, size.height - 30f - h),
                            size = androidx.compose.ui.geometry.Size(barW * 0.8f, h),
                        )
                    }
                }
            }
        }
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
