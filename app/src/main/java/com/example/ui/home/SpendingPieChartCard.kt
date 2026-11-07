package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Categories
import com.example.data.model.CategoryInfo
import com.example.data.model.TransactionItem
import com.example.ui.theme.ExpenseRed
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

data class PieChartSlice(
    val category: String,
    val amount: Double,
    val percentage: Float,
    val startAngle: Float,
    val sweepAngle: Float,
    val categoryInfo: CategoryInfo
)

/**
 * Native Jetpack Compose interactive Pie/Donut Chart component.
 * Inspired by modern declarative charting systems (like Recharts), it provides:
 * - Dynamic category spending distribution calculation
 * - Smooth entrance and change animations
 * - Interactive slice selection via direct Canvas touch or legend chips
 * - Detailed center info card displaying selected category metrics
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendingPieChartCard(
    transactions: List<TransactionItem>,
    modifier: Modifier = Modifier,
    onCategoryFilterSelected: ((String?) -> Unit)? = null
) {
    val currencyFormatter = remember {
        NumberFormat.getNumberInstance(Locale("th", "TH")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
    }

    // Filter only expense transactions
    val expenseTransactions = remember(transactions) {
        transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
    }

    val totalExpense = remember(expenseTransactions) {
        expenseTransactions.sumOf { it.amount }
    }

    // Group expenses by category
    val categoryData = remember(expenseTransactions, totalExpense) {
        if (totalExpense <= 0.0) {
            emptyList()
        } else {
            val grouped = expenseTransactions
                .groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .toList()
                .sortedByDescending { it.second }

            var currentAngle = 0f
            grouped.map { (catName, amount) ->
                val fraction = (amount / totalExpense).toFloat()
                val sweep = fraction * 360f
                val slice = PieChartSlice(
                    category = catName,
                    amount = amount,
                    percentage = fraction * 100f,
                    startAngle = currentAngle,
                    sweepAngle = sweep,
                    categoryInfo = Categories.getCategoryInfo(catName, "EXPENSE")
                )
                currentAngle += sweep
                slice
            }
        }
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // Clear selection if category is no longer present in data
    LaunchedEffect(categoryData) {
        if (selectedCategory != null && categoryData.none { it.category == selectedCategory }) {
            selectedCategory = null
            onCategoryFilterSelected?.invoke(null)
        }
    }

    // Animation progress (0f to 1f)
    var animationTarget by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "pie_animation"
    )

    LaunchedEffect(categoryData) {
        animationTarget = 0f
        animationTarget = 1f
    }

    val density = LocalDensity.current
    val strokeWidthDp = 28.dp
    val strokeWidthPx = with(density) { strokeWidthDp.toPx() }
    val selectedExtraStrokePx = with(density) { 8.dp.toPx() }

    val activeSlice = remember(selectedCategory, categoryData) {
        categoryData.firstOrNull { it.category == selectedCategory }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spending_pie_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = "Pie Chart",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "สัดส่วนรายจ่ายตามหมวดหมู่",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (categoryData.isNotEmpty()) "${categoryData.size} หมวดหมู่ที่มีการใช้จ่าย" else "ไม่มีรายการรายจ่าย",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedCategory != null) {
                    Surface(
                        onClick = {
                            selectedCategory = null
                            onCategoryFilterSelected?.invoke(null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "รีเซ็ต",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (categoryData.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "ยังไม่มีข้อมูลรายจ่ายสำหรับสร้างแผนภูมิ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "กดปุ่ม + ด้านล่างเพื่อเริ่มบันทึกรายจ่าย",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Interactive Donut Chart Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Donut Chart Canvas with Tap Detection
                    Canvas(
                        modifier = Modifier
                            .size(210.dp)
                            .testTag("pie_chart_canvas")
                            .pointerInput(categoryData, totalExpense) {
                                detectTapGestures { offset ->
                                    val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                                    val dx = offset.x - canvasCenter.x
                                    val dy = offset.y - canvasCenter.y
                                    val distance = sqrt(dx * dx + dy * dy)
                                    val outerRadius = min(size.width, size.height) / 2f
                                    val innerRadius = outerRadius - strokeWidthPx - selectedExtraStrokePx

                                    if (distance in innerRadius..outerRadius) {
                                        val angleInDegrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f
                                        // Chart starts at -90 deg (12 o'clock)
                                        val clockwiseOffsetFromTop = (angleInDegrees + 90f) % 360f

                                        val tappedSlice = categoryData.firstOrNull { slice ->
                                            val start = slice.startAngle
                                            val end = slice.startAngle + slice.sweepAngle
                                            clockwiseOffsetFromTop in start..end
                                        }

                                        if (tappedSlice != null) {
                                            if (selectedCategory == tappedSlice.category) {
                                                selectedCategory = null
                                                onCategoryFilterSelected?.invoke(null)
                                            } else {
                                                selectedCategory = tappedSlice.category
                                                onCategoryFilterSelected?.invoke(tappedSlice.category)
                                            }
                                        }
                                    } else if (distance < innerRadius) {
                                        // Center tapped -> deselect
                                        selectedCategory = null
                                        onCategoryFilterSelected?.invoke(null)
                                    }
                                }
                            }
                    ) {
                        val canvasRadius = min(size.width, size.height) / 2f - strokeWidthPx / 2f - 4.dp.toPx()
                        val arcSize = Size(canvasRadius * 2, canvasRadius * 2)
                        val topLeft = Offset(
                            (size.width - arcSize.width) / 2f,
                            (size.height - arcSize.height) / 2f
                        )

                        categoryData.forEach { slice ->
                            val isSelected = slice.category == selectedCategory
                            val animatedSweep = slice.sweepAngle * animatedProgress
                            val strokeWidth = if (isSelected) strokeWidthPx + selectedExtraStrokePx else strokeWidthPx
                            val sliceColor = if (selectedCategory == null || isSelected) {
                                slice.categoryInfo.color
                            } else {
                                slice.categoryInfo.color.copy(alpha = 0.3f)
                            }

                            drawArc(
                                color = sliceColor,
                                startAngle = -90f + slice.startAngle * animatedProgress,
                                sweepAngle = animatedSweep.coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }

                    // Center Details Information
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .testTag("pie_chart_center_details")
                    ) {
                        if (activeSlice != null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(activeSlice.categoryInfo.color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = activeSlice.categoryInfo.icon,
                                    contentDescription = activeSlice.category,
                                    tint = activeSlice.categoryInfo.color,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeSlice.category,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "฿${currencyFormatter.format(activeSlice.amount)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ExpenseRed
                                ),
                                maxLines = 1
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = activeSlice.categoryInfo.color.copy(alpha = 0.15f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", activeSlice.percentage),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = activeSlice.categoryInfo.color,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "รายจ่ายรวม",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "฿${currencyFormatter.format(totalExpense)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ExpenseRed
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = "แตะเพื่อดูสัดส่วน",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Legend FlowRow (Recharts-like interactive chips)
                Text(
                    text = "รายการสัดส่วนค่าใช้จ่าย (แตะเพื่อดูรายละเอียด):",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoryData.forEach { slice ->
                        val isSelected = slice.category == selectedCategory
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                slice.categoryInfo.color.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, slice.categoryInfo.color)
                            } else {
                                null
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isSelected) {
                                        selectedCategory = null
                                        onCategoryFilterSelected?.invoke(null)
                                    } else {
                                        selectedCategory = slice.category
                                        onCategoryFilterSelected?.invoke(slice.category)
                                    }
                                }
                                .testTag("legend_chip_${slice.category}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(slice.categoryInfo.color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = slice.category,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format(Locale.US, "%.0f%%", slice.percentage),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) slice.categoryInfo.color else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
