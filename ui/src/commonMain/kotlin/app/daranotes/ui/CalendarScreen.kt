package app.daranotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.daranotes.MainViewModel
import app.daranotes.core.CalendarDay
import app.daranotes.core.Dates
import app.daranotes.core.GitSync
import app.daranotes.core.Note
import app.daranotes.core.NoteCalendar
import app.daranotes.core.PlatformDates
import app.daranotes.core.ioDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * A month calendar: a filled dot marks a day with a daily note, a ring a day when other notes
 * were last edited (by their last commit, so a clone or pull doesn't move them all to today).
 * Pick a day to list its notes; pick a note to open it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val notes by vm.notes.collectAsState()
    val sync by vm.syncState.collectAsState()
    var days by remember { mutableStateOf<Map<LocalDate, CalendarDay>?>(null) }
    val today = remember { Dates.today() }
    val month = vm.calendarMonth.value ?: firstOfMonth(today)
    val picked = vm.calendarDay.value ?: today
    val pick = { day: LocalDate ->
        vm.calendarDay.value = day
        vm.calendarMonth.value = firstOfMonth(day)
    }

    LaunchedEffect(Unit) { vm.refresh() }
    // Recomputed when notes change and after each sync, which can bring new commits.
    LaunchedEffect(notes, sync.generation) {
        val times = GitSync.editTimes(vm.repo.root)
        days = withContext(ioDispatcher) { NoteCalendar.days(notes, times) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Tip("Back") {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = { Text("Calendar") },
                actions = {
                    TextButton(onClick = { pick(today) }) { Text("Today") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            // Side by side on wide windows and phones turned sideways; stacked otherwise.
            val wide = maxWidth >= 720.dp || maxWidth > maxHeight
            val calendar: @Composable () -> Unit = {
                MonthView(
                    month = month,
                    days = days.orEmpty(),
                    picked = picked,
                    today = today,
                    onPick = pick,
                    onMonth = { vm.calendarMonth.value = it },
                )
                Legend()
            }
            val open = { note: Note -> vm.openPath(note.path) }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { calendar() }
                    VerticalDivider()
                    LazyColumn(Modifier.weight(1f).fillMaxHeight()) { dayNotes(picked, days, open) }
                }
            } else {
                // One scrolling page, so a long day's list isn't squeezed under the month.
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "month") { Column { calendar() } }
                    item(key = "divider") { HorizontalDivider() }
                    dayNotes(picked, days, open)
                }
            }
        }
    }
}

private fun firstOfMonth(d: LocalDate) = LocalDate(d.year, d.month, 1)

/** "November 2026". */
private fun monthTitle(first: LocalDate) = PlatformDates.monthName(first.month.number, short = false) + " " + first.year

/** "Thursday, November 12, 2026". */
private fun longDate(d: LocalDate) =
    PlatformDates.dayName(d.dayOfWeek.isoDayNumber, short = false) + ", " +
        PlatformDates.monthName(d.month.number, short = false) + " " + d.day + ", " + d.year

@Composable
private fun MonthView(
    month: LocalDate,
    days: Map<LocalDate, CalendarDay>,
    picked: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onMonth: (LocalDate) -> Unit,
) {
    val weekStart = remember { PlatformDates.firstDayOfWeek() }
    val weeks = remember(month, weekStart) { NoteCalendar.monthGrid(month, weekStart) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tip("Previous month") {
                IconButton(onClick = { onMonth(month.minus(1, DateTimeUnit.MONTH)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
            }
            Text(
                monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Tip("Next month") {
                IconButton(onClick = { onMonth(month.plus(1, DateTimeUnit.MONTH)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }
        Row {
            for (i in 0 until 7) {
                val iso = (weekStart - 1 + i) % 7 + 1
                Text(
                    PlatformDates.dayName(iso, short = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                )
            }
        }
        for (week in weeks) {
            Row {
                for (day in week) {
                    Box(Modifier.weight(1f).height(48.dp)) {
                        if (day != null) {
                            DayCell(day, days[day], selected = day == picked, isToday = day == today) { onPick(day) }
                        }
                    }
                }
            }
        }
    }
}

/** One day: its number, with today outlined and the picked day filled in; markers below. */
@Composable
private fun DayCell(day: LocalDate, entry: CalendarDay?, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val daily = entry?.daily.orEmpty().isNotEmpty()
    val edited = entry?.edited?.size ?: 0
    val description = buildList {
        add(PlatformDates.monthName(day.month.number, short = false) + " " + day.day)
        if (isToday) add("today")
        if (daily) add("daily note")
        if (edited > 0) add(if (edited == 1) "1 note edited" else "$edited notes edited")
    }.joinToString(", ")
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .then(if (isToday) Modifier.border(1.5.dp, colors.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Row(Modifier.height(MARKER), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (daily) Marker(daily = true)
                if (edited > 0) Marker(daily = false)
            }
        }
    }
}

private val MARKER = 8.dp

/**
 * A daily note's marker is a filled dot, an edited note's a ring, so the shape tells them apart
 * as well as the color. The colors are blue and orange (Okabe-Ito), which stay distinct with
 * red-green color blindness; lighter versions on dark backgrounds.
 */
@Composable
private fun Marker(daily: Boolean, size: Dp = MARKER) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = when {
        daily && dark -> Color(0xFF56B4E9)
        daily -> Color(0xFF0072B2)
        dark -> Color(0xFFF0B040)
        else -> Color(0xFFE69F00)
    }
    Canvas(Modifier.size(size)) {
        if (daily) {
            drawCircle(color)
        } else {
            val stroke = 2.dp.toPx()
            drawCircle(color, radius = this.size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        }
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(daily = true)
        Text("Daily note", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp, end = 20.dp))
        Marker(daily = false)
        Text("Last edited", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The notes on [day]: its daily note, then the other notes last edited that day. */
private fun LazyListScope.dayNotes(day: LocalDate, days: Map<LocalDate, CalendarDay>?, onOpen: (Note) -> Unit) {
    item(key = "date") {
        Text(
            longDate(day),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
    if (days == null) {
        item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }
        return
    }
    val entry = days[day]
    if (entry == null) {
        item(key = "none") {
            Text(
                "No notes on this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        return
    }
    if (entry.daily.isNotEmpty()) {
        item(key = "daily") { SectionHeader(daily = true, "Daily note") }
        items(entry.daily, key = { "d:" + it.path }) { NoteRow(it, onOpen) }
    }
    if (entry.edited.isNotEmpty()) {
        item(key = "edited") { SectionHeader(daily = false, "Last edited (${entry.edited.size})") }
        items(entry.edited, key = { "e:" + it.path }) { NoteRow(it, onOpen) }
    }
}

@Composable
private fun SectionHeader(daily: Boolean, label: String) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marker(daily)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun NoteRow(note: Note, onOpen: (Note) -> Unit) {
    ListItem(
        headlineContent = { Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(note.path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        },
        modifier = Modifier.clickable { onOpen(note) },
    )
}
