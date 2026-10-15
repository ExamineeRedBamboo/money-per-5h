package com.moneyper5h.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneyper5h.app.domain.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val Ink = Color(0xFF202020)
private val Muted = Color(0xFF626262)
private val Background = Color(0xFFF8F8F8)
private val Line = Color(0xFFDEDEDE)
private val Soft = Color(0xFFEEEEEE)
private val Heat = listOf(Color(0xFFE5E7E9), Color(0xFFC1CBD5), Color(0xFF8B9EAF), Color(0xFF5B7185))
private val dateFormat = DateTimeFormatter.ofPattern("M/d", Locale.CHINA).withZone(Usage.zone)
private val timeFormat = DateTimeFormatter.ofPattern("yyyy/M/d HH:mm", Locale.CHINA).withZone(Usage.zone)
private fun date(time: Long) = dateFormat.format(Instant.ofEpochMilli(time))

@Composable
fun UsageScreen(state: UsageUiState, dispatch: (Action) -> Unit, confirmAmount: () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = Color.White,
        background = Background, surface = Color.White, onSurface = Ink, onBackground = Ink,
        secondary = Muted, surfaceVariant = Soft, outline = Line)) {
        Surface(color = Background, modifier = Modifier.fillMaxSize()) {
            if (state.loading || state.unavailable) {
                Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                    Text(if (state.loading) "正在读取记录…" else "无法读取本地数据，请重启应用重试。")
                }
            } else {
                val session = state.session
                BackHandler(session.dialog == Dialog.NONE && session.page == Page.RECORDS) { dispatch(Action.Navigate(Page.USAGE)) }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (session.page == Page.USAGE) "用量" else "记录", fontSize = 23.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (session.page == Page.USAGE) UsagePage(state, dispatch) else RecordsPage(state, dispatch)
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 20.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                        if (session.dialog == Dialog.NONE) session.message?.let { Text(it, color = Muted, fontSize = 11.sp) }
                    }
                    HorizontalDivider(color = Line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (page in Page.entries) {
                            val selected = session.page == page
                            TextButton(onClick = { dispatch(Action.Navigate(page)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (selected) Soft else Color.Transparent, contentColor = if (selected) Ink else Muted)) {
                                Text(if (page == Page.USAGE) "用量" else "记录", fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
                if (session.dialog != Dialog.NONE) EntryDialog(state, dispatch, confirmAmount)
            }
        }
    }
}

@Composable
private fun UsagePage(state: UsageUiState, dispatch: (Action) -> Unit) {
    val s = state.session
    val plan = s.ledger.plan
    val draft = s.ledger.draft
    val enabled = s.canRecord(state.now)
    val quotas = remember(s.ledger.records, plan, state.now / 1_000) {
        listOf(Usage.quota(s.ledger.records, plan?.fiveHourLimit ?: 0, Usage.FIVE_HOURS, state.now),
            Usage.quota(s.ledger.records, plan?.weeklyLimit ?: 0, Usage.WEEK, state.now))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 720.dp
        // Standard phone sizes fit without scrolling; accessibility fonts/short windows remain usable.
        val needsScroll = maxHeight < 600.dp || LocalDensity.current.fontScale > 1.3f
        val modifier = if (needsScroll) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
        Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan?.let { "Pro ${Money.compact(it.hundredths)}x" } ?: "未设置倍数", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(plan?.let { "${if (enabled) "有效至" else "到期于"} ${date(it.expiresAt)}" } ?: "每次设置后有效30天", color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = { dispatch(Action.Open(Dialog.PLAN)) }) { Text(if (plan == null) "设置倍数" else "重新设置", fontSize = 12.sp) }
            }
            if (!enabled) Text(if (plan == null) "设置倍数后开始记录" else "订阅已过期，重新设置倍数后继续记录", fontSize = 12.sp, lineHeight = 16.sp)
            HorizontalDivider(color = Line)
            quotas.forEachIndexed { index, quota -> QuotaCard(if (index == 0) "5小时限额" else "每周限额", quota, plan != null, state.now, compact) }
            Column(Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("待记录", fontSize = 12.sp, lineHeight = 16.sp, color = Muted)
                        Text(Money.display(draft.cents), fontSize = if (compact) 25.sp else 29.sp, lineHeight = if (compact) 30.sp else 36.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { dispatch(Action.Open(Dialog.AMOUNT)) }, enabled = enabled,
                        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text("直接输入", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(progress = { (s.remaining(state.elapsed).toFloat() / Draft.WAIT_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp), color = if (draft.cents > 0) Ink else Soft, trackColor = Soft)
                Text(when {
                    !enabled -> if (draft.hasContent) "草稿已保留，设置倍数后继续" else "设置倍数后开始记录"
                    draft.held && draft.hasContent -> "草稿已保留，继续编辑后记下"
                    draft.cents == 0L -> "点金额，松手后自动记下"
                    else -> "松手后自动记下"
                }, fontSize = 12.sp, lineHeight = 16.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            }
            SessionReducer.denominations.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { cents ->
                        OutlinedButton(onClick = { dispatch(Action.Add(cents)) }, enabled = enabled,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Line), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
                            Text("+${Money.compact(cents)}", fontSize = 14.sp, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { dispatch(Action.UndoTap) }, enabled = draft.additions.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(draft.additions.lastOrNull()?.let { "撤销 +${Money.compact(it)}" } ?: "撤销上次", fontSize = 12.sp)
                }
                TextButton(onClick = { dispatch(Action.Clear) }, enabled = draft.hasContent, modifier = Modifier.weight(1f)) { Text("清空", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun QuotaCard(title: String, quota: Quota, configured: Boolean, now: Long, compact: Boolean) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp))
        .border(1.dp, Line, RoundedCornerShape(15.dp)).padding(if (compact) 10.dp else 14.dp)) {
        Text(title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = if (compact) 6.dp else 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val minutes = quota.end?.let { ceil((it - now).coerceAtLeast(0) / 60_000.0).toLong() }
            Text(minutes?.let { if (it >= 1_440) "${it / 1_440}天 ${it / 60 % 24}时后重置" else "${it / 60}时 ${it % 60}分后重置" } ?: "", fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
            Text(if (configured) "剩余${quota.percent}%" else "未设置", fontSize = 12.sp, lineHeight = 16.sp)
        }
        LinearProgressIndicator(progress = { quota.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp)
            .semantics { contentDescription = if (configured) "$title，剩余${quota.percent}%，已记录${Money.display(quota.used)}，额度${Money.display(quota.limit)}" else "$title，尚未设置倍数" },
            color = Color(0xFF363636), trackColor = Line)
    }
}

@Composable
private fun RecordsPage(state: UsageUiState, dispatch: (Action) -> Unit) {
    val records = state.session.ledger.records
    val sorted = remember(records) { records.sortedWith(compareByDescending<UsageRecord> { it.savedAt }.thenByDescending { it.id }) }
    val days = remember(records, Usage.day(state.now)) { Usage.heatmap(records, state.now) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "heatmap") { DailyHeatmap(days) }
            if (sorted.isEmpty()) item(key = "empty") { Text("暂无记录", color = Muted, modifier = Modifier.padding(vertical = 20.dp)) }
            items(sorted, key = { it.id }) { record ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Money.display(record.cents), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(timeFormat.format(Instant.ofEpochMilli(record.savedAt)), fontSize = 12.sp, color = Muted)
                    }
                    TextButton(onClick = { dispatch(Action.Delete(record.id)) }, modifier = Modifier.semantics {
                        contentDescription = "删除 ${Money.display(record.cents)}，${timeFormat.format(Instant.ofEpochMilli(record.savedAt))}"
                    }) { Text("删除") }
                }
                HorizontalDivider(color = Line)
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.session.deleted != null) {
                Text("记录已移除", color = Muted, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = { dispatch(Action.UndoDelete) }) { Text("撤销") }
            }
        }
    }
}

@Composable
private fun DailyHeatmap(days: List<Usage.Day>) {
    var selected by remember(days) { mutableStateOf<Usage.Day?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("每日记录", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        val last = days.last { !it.future }.date
        Text("${days.first().date.format(dateFormat)}—${last.format(dateFormat)} · 12周", fontSize = 11.sp, color = Muted)
        val summary = days.filter { !it.future && it.cents > 0 }.joinToString("；") { "${it.date} ${Money.display(it.cents)}" }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.height(98.dp), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("一", "", "三", "", "五", "", "日").forEach { Text(it, fontSize = 10.sp, lineHeight = 11.sp, color = Muted) }
            }
            Canvas(Modifier.weight(1f).height(98.dp).semantics { contentDescription = "每日已记录金额热图，含本周12周。无记录不代表零消费。$summary" }
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        val column = (offset.x / (size.width / 12f)).toInt().coerceIn(0, 11)
                        val row = (offset.y / (size.height / 7f)).toInt().coerceIn(0, 6)
                        selected = days[column * 7 + row]
                    }
                }) {
                val gap = 3.dp.toPx()
                val w = size.width / 12
                val h = size.height / 7
                days.forEachIndexed { index, day ->
                    if (!day.future) drawRoundRect(Heat[day.level], topLeft = Offset(index / 7 * w, index % 7 * h),
                        size = Size((w - gap).coerceAtLeast(1f), h - gap), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            listOf("无记录", "≤20", "≤50", ">50").forEachIndexed { index, label ->
                Box(Modifier.padding(start = 5.dp, end = 3.dp).size(9.dp).background(Heat[index], RoundedCornerShape(2.dp)))
                Text(if (index == 0) label else "¥$label", fontSize = 10.sp, color = Muted)
            }
        }
        selected?.let { day -> Text("${day.date} · ${if (day.future) "尚未到达" else if (day.cents == 0L) "无记录" else "已记录 ${Money.display(day.cents)}"}",
            fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

@Composable
private fun EntryDialog(state: UsageUiState, dispatch: (Action) -> Unit, confirmAmount: () -> Unit) {
    val s = state.session
    val amount = s.dialog == Dialog.AMOUNT
    var value by rememberSaveable(s.dialog) { mutableStateOf(if (amount) s.ledger.draft.input else s.ledger.plan?.hundredths?.let(Money::compact) ?: "") }
    val allowed = s.canRecord(state.now)
    val parsed = if (amount) Money.parse(value) else Plan.parse(value)
    val submit = { if (amount) confirmAmount() else dispatch(Action.ConfirmPlan(value)) }
    AlertDialog(onDismissRequest = { dispatch(Action.Close) }, title = { Text(if (amount) "直接输入" else "设置倍数") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = value, onValueChange = {
                value = it.take(if (amount) 32 else 16)
                if (amount) dispatch(Action.Input(value))
            }, label = { Text(if (amount) "这笔金额（元）" else "倍数（x）") }, singleLine = true, enabled = !amount || allowed,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (parsed != null && (!amount || allowed)) submit() }))
            if (amount) {
                Text(if (s.ledger.draft.cents > 0) "修改当前待记录金额，确认后只记一笔。" else "输入后点确认记下。", fontSize = 13.sp, color = Muted)
                Text("最多 ¥999999.99。", fontSize = 12.sp, color = Muted)
                if (!allowed) {
                    Text("订阅已过期，输入已暂存", fontSize = 13.sp)
                    TextButton(onClick = { dispatch(Action.Open(Dialog.PLAN)) }) { Text("重新设置倍数") }
                }
            } else {
                listOf("月预算" to 150L, "每周限额" to 35L, "5小时限额" to 5L).forEach { (label, factor) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, fontSize = 14.sp)
                        Text(parsed?.let { Money.display(it * factor) } ?: "—", fontSize = 14.sp)
                    }
                }
                Text("确认后有效30天。按新倍数重算额度，已用金额和重置时间保留。", fontSize = 13.sp, color = Muted)
            }
            if (value.isNotEmpty() && parsed == null) Text(if (amount) "请输入大于0、最多两位小数的金额" else "倍数范围0.01—6666.66x，最多两位小数", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            s.message?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = submit, enabled = parsed != null && (!amount || allowed)) { Text(if (amount) "确认记下" else "确认设置") }
    }, dismissButton = { TextButton(onClick = { dispatch(Action.Close) }) { Text("取消") } })
}
