package com.silencer.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silencer.app.logic.WorkWindow
import com.silencer.app.ui.theme.AmberStart
import com.silencer.app.ui.theme.IndigoStart
import com.silencer.app.ui.theme.OrangeEnd
import com.silencer.app.ui.theme.VioletEnd
import java.time.LocalDateTime
import kotlinx.coroutines.launch

private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun fmt(dt: LocalDateTime): String = String.format("%02d:%02d", dt.hour, dt.minute)

private fun weekdayLabel(dt: LocalDateTime): String = WEEKDAY_NAMES[dt.dayOfWeek.value - 1]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SilencerViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    // 时间段编辑底部弹窗目标 + 规则说明弹窗
    var sheetTarget by remember { mutableStateOf<SheetTarget>(SheetTarget.None) }
    var showRules by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能静音", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showRules = true }) {
                        Text("?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatusHeader(state, vm) }
            item { WorkCard(state, vm, onEdit = { sheetTarget = SheetTarget.Window(it) }) }
            item { PolicyCard(state, vm) }
            item { PermissionCard(state, vm) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    (sheetTarget as? SheetTarget.Window)?.let { t ->
        WindowEditSheet(
            initial = t.index?.let { i -> state.settings.windows.getOrNull(i) },
            existing = state.settings.windows,
            editingIndex = t.index,
            onDismiss = { sheetTarget = SheetTarget.None },
            onConfirm = { w ->
                if (t.index == null) vm.addWindow(w) else vm.updateWindow(t.index, w)
                sheetTarget = SheetTarget.None
            }
        )
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("使用规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleLine("· 勾选的星期 × 时间段内 → 自动静音")
                    RuleLine("· 其余时间 → 恢复正常响铃")
                    RuleLine("· 法定节假日 → 不静音")
                    RuleLine("· 调休补班日（周末上班）→ 按工作日静音")
                    RuleLine("· 节假日数据来自 timor.tech，超 7 天自动更新")
                    val years = state.cachedYears
                    if (years.isNotEmpty()) {
                        RuleLine("当前已缓存：${years.joinToString("、") { "${it} 年" }}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRules = false }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun RuleLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

// ---------- 顶部状态（大主开关） ----------

@Composable
private fun StatusHeader(state: UiState, vm: SilencerViewModel) {
    val enabled = state.settings.enabled
    val silent = state.silentNow

    // 所有状态统一紧凑条，仅用颜色区分：静音=靛蓝紫 / 响铃=琥珀橙 / 停用=浅灰白
    val off = !enabled
    val brush = when {
        off -> SolidColor(Color(0xFFF1F5F9))
        silent -> Brush.linearGradient(listOf(IndigoStart, VioletEnd))
        else -> Brush.linearGradient(listOf(AmberStart, OrangeEnd))
    }
    val white = Color.White
    val icon: ImageVector = when {
        off -> Icons.Filled.Info
        silent -> Icons.Filled.Notifications
        else -> Icons.Filled.CheckCircle
    }
    val title = when {
        off -> "跟随系统"
        silent -> "静音中"
        else -> "监听中"
    }
    val next = state.nextTransition
    val subtitle = when {
        off -> "铃声由系统控制，本应用不接管"
        silent -> if (next == null) "工作时间"
        else "工作时间 · ${weekdayLabel(next)} ${fmt(next)} 恢复"
        else -> if (next == null) "已开启"
        else "已开启 · ${weekdayLabel(next)} ${fmt(next)} 静音"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .clickable { vm.toggleEnabled() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (off) Color(0xFFE2E8F0) else white.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (off) Color(0xFF64748B) else white,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = if (off) Color(0xFF475569) else white
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = if (off) Color(0xFF64748B) else white.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { vm.toggleEnabled() },
                modifier = Modifier.scale(0.8f),
                colors = if (off) SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF94A3B8),
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFCBD5E1),
                    uncheckedBorderColor = Color(0xFF94A3B8)
                ) else SwitchDefaults.colors(
                    checkedThumbColor = white,
                    checkedTrackColor = white.copy(alpha = 0.35f),
                    uncheckedThumbColor = white,
                    uncheckedTrackColor = white.copy(alpha = 0.25f),
                    uncheckedBorderColor = white.copy(alpha = 0.6f),
                    uncheckedTrackContentColor = white.copy(alpha = 0.9f)
                )
            )
        }
    }
}

// ---------- 工作时间（时间段 + 生效星期） ----------

private sealed interface SheetTarget {
    data object None : SheetTarget
    data class Window(val index: Int?) : SheetTarget
}

/** 快捷预设：label + 开始/结束分钟（0 点起） */
private data class Preset(val label: String, val startMinute: Int, val endMinute: Int)

private val PRESETS = listOf(
    Preset("标准班", 9 * 60, 18 * 60),
    Preset("上午班", 9 * 60, 12 * 60),
    Preset("下午班", 14 * 60, 18 * 60),
    Preset("夜班", 22 * 60, 6 * 60),
    Preset("全天", 0, 23 * 60 + 59)
)

private val WHEEL_ITEM_HEIGHT = 44.dp

/** iOS 风格滚轮：一列可滚动 + 中间高亮 + 上下渐隐，fling 后自动吸附居中 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()

    // 滚动过程中推算当前居中的条目并回传
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.map { it.index to it.offset }
        }.collect { _ ->
            val info = listState.layoutInfo
            val half = info.viewportSize.height / 2
            val center = info.visibleItemsInfo
                .firstOrNull { it.offset <= half && it.offset + it.size >= half }
                ?.index
            if (center != null && center != selected) onSelected(center.coerceIn(0, count - 1))
        }
    }
    // 外部（如快捷预设）改变选中项时滚动过去；用户自行滚动时选中项已居中，跳过以免打断滚动
    LaunchedEffect(selected) {
        val info = listState.layoutInfo
        val half = info.viewportSize.height / 2
        val center = info.visibleItemsInfo
            .firstOrNull { it.offset <= half && it.offset + it.size >= half }
            ?.index
        if (center != selected) listState.animateScrollToItem(selected)
    }

    val maskColor = MaterialTheme.colorScheme.surfaceContainerLow
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 中间高亮行（背景）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f))
        )
        // 滚动列表（上下各留一行，保证首尾项也能滚到中间）
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT),
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT * 3)
        ) {
            items(count) { i ->
                val isCenter = i == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT)
                        .clickable { scope.launch { listState.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label(i),
                        fontSize = if (isCenter) 20.sp else 15.sp,
                        fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCenter) Color(0xFF0F172A) else Color(0xFF94A3B8)
                    )
                }
            }
        }
        // 上下渐隐遮罩（不拦截触摸）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(maskColor, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, maskColor)))
        )
    }
}

@Composable
private fun TimeWheelRow(label: String, minute: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        WheelPicker(
            count = 24,
            selected = minute / 60,
            onSelected = { h -> onChange(h * 60 + minute % 60) },
            label = { String.format("%02d", it) },
            modifier = Modifier.weight(1f)
        )
        Text(
            ":",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        WheelPicker(
            count = 60,
            selected = minute % 60,
            onSelected = { m -> onChange(minute / 60 * 60 + m) },
            label = { String.format("%02d", it) },
            modifier = Modifier.weight(1f)
        )
    }
}

/** 添加/修改时间段的底部弹窗：快捷预设 + 开始/结束滚轮，一次搞定 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WindowEditSheet(
    initial: WorkWindow?,
    existing: List<WorkWindow>,
    editingIndex: Int?,
    onDismiss: () -> Unit,
    onConfirm: (WorkWindow) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var startMinute by remember { mutableStateOf(initial?.startMinute ?: 9 * 60) }
    var endMinute by remember { mutableStateOf(initial?.endMinute ?: 18 * 60) }
    // 确定时若与已有时段重叠，先弹确认再保存
    var overlapTarget by remember { mutableStateOf<WorkWindow?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (initial == null) "添加工作时间段" else "修改工作时间段",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            // 快捷预设
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { p ->
                    FilterChip(
                        selected = p.startMinute == startMinute && p.endMinute == endMinute,
                        onClick = {
                            startMinute = p.startMinute
                            endMinute = p.endMinute
                        },
                        label = { Text(p.label) }
                    )
                }
            }
            TimeWheelRow("开始", startMinute) { startMinute = it }
            TimeWheelRow("结束", endMinute) { endMinute = it }
            if (startMinute == endMinute) {
                Text(
                    "开始与结束时间不能相同",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = {
                        val w = WorkWindow(startMinute, endMinute)
                        val hit = existing
                            .filterIndexed { i, _ -> i != editingIndex }
                            .firstOrNull { it.overlaps(w) }
                        if (hit != null) overlapTarget = hit else onConfirm(w)
                    },
                    enabled = startMinute != endMinute,
                    modifier = Modifier.weight(1f)
                ) { Text("确定") }
            }
        }
    }

    // 重叠确认弹窗：确认后保存，取消则留在弹窗内继续调整
    overlapTarget?.let { hit ->
        AlertDialog(
            onDismissRequest = { overlapTarget = null },
            title = { Text("时间段重叠") },
            text = { Text("该时段与 ${hit.startLabel()} — ${hit.endLabel()} 重叠，仍要保存吗？") },
            confirmButton = {
                TextButton(onClick = {
                    overlapTarget = null
                    onConfirm(WorkWindow(startMinute, endMinute))
                }) { Text("仍然保存") }
            },
            dismissButton = {
                TextButton(onClick = { overlapTarget = null }) { Text("再想想") }
            }
        )
    }
}

@Composable
private fun WorkCard(
    state: UiState,
    vm: SilencerViewModel,
    onEdit: (Int?) -> Unit
) {
    var showCrossDayTip by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "工作时间",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { showCrossDayTip = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onEdit(null) }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加时间段")
                }
            }
            if (state.settings.windows.isEmpty()) {
                Text(
                    "还没有时间段，点右上角 + 添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.settings.windows.forEachIndexed { i, w ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onEdit(i) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${w.startLabel()} — ${w.endLabel()}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        val dur = if (w.endMinute > w.startMinute) w.endMinute - w.startMinute
                        else w.endMinute + 1440 - w.startMinute
                        Text(
                            "时长 ${dur / 60} 小时${if (dur % 60 != 0) " ${dur % 60} 分" else ""} · 点击修改",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = { vm.removeWindow(i) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "生效星期",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                WEEKDAY_NAMES.forEachIndexed { index, name ->
                    val weekday = index + 1
                    FilterChip(
                        selected = weekday in state.settings.weekdays,
                        onClick = { vm.toggleWeekday(weekday) },
                        label = { Text(name, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showCrossDayTip) {
        AlertDialog(
            onDismissRequest = { showCrossDayTip = false },
            title = { Text("关于时间段") },
            text = { Text("支持跨天时段，如 22:00 — 06:00（次日 06:00 恢复响铃）。") },
            confirmButton = {
                TextButton(onClick = { showCrossDayTip = false }) { Text("知道了") }
            }
        )
    }
}

// ---------- 静音策略（节假日 + 静音方式） ----------

@Composable
private fun PolicyCard(state: UiState, vm: SilencerViewModel) {
    // 高级区域展开状态：Ringer 被选中时强制展开
    var advancedOpen by remember { mutableStateOf(state.settings.silenceMode == "ringer") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            // 标题与法定节假日开关合并为一行，显著压缩卡片高度
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "静音策略",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "法定节假日",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.settings.respectHolidays)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
                Switch(
                    checked = state.settings.respectHolidays,
                    onCheckedChange = { vm.toggleHolidays() },
                    modifier = Modifier.scale(0.8f)
                )
            }
            if (state.cachedYears.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.holidayBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "获取节假日数据中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "节假日数据未获取",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { vm.refreshHolidays() },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("获取", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                Text(
                    "节假日不静音 · 调休补班照常",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.holidayMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.cachedYears.isEmpty()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            // 默认展示：勿扰模式（推荐）
            ModeRow(
                selected = state.settings.silenceMode == "dnd",
                title = "勿扰模式",
                desc = "推荐 · 只留闹钟，华为上最稳定",
                onClick = { vm.setSilenceMode("dnd") }
            )
            if (state.settings.silenceMode == "dnd" && !state.dndGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "未授权通知使用权，到下方权限中开启",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 高级：静音铃声（折叠）
            if (advancedOpen) {
                ModeRow(
                    selected = state.settings.silenceMode == "ringer",
                    title = "静音铃声",
                    desc = "仅铃声静音，通知照常响。部分国产系统上可能失效",
                    onClick = { vm.setSilenceMode("ringer") }
                )
                if (state.settings.silenceMode == "ringer") {
                    Text(
                        "当前使用“静音铃声”，部分手机上可能不生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            // 高级展开/收起按钮（靠左、低调，参考 Apple 设置“更多”风格）
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { advancedOpen = !advancedOpen }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "高级",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    if (advancedOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ModeRow(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- 权限与测试 ----------

@Composable
private fun PermissionCard(state: UiState, vm: SilencerViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "权限",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            PermissionRow(
                label = "通知使用权（勿扰）",
                ok = state.dndGranted,
                onClick = vm::openDndSettings
            )
            PermissionRow(
                label = "忽略电池优化（防杀后台）",
                ok = state.batteryOk,
                onClick = vm::openBatterySettings
            )
            PermissionRow(
                label = "允许精确闹钟（准时切换）",
                ok = state.exactAlarmOk,
                onClick = vm::openExactAlarmSettings
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.applyNow() }, modifier = Modifier.fillMaxWidth()) {
                Text("立即应用一次（测试）")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "华为手机请在 设置 → 应用启动管理 中允许自启动，避免被杀",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (ok) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已开启",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            TextButton(onClick = onClick) { Text("去设置", color = MaterialTheme.colorScheme.error) }
        }
    }
}
