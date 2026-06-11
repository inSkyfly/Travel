package com.tourism.assistant.ui.plan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tourism.assistant.domain.model.FoodRec
import com.tourism.assistant.domain.model.HotelRec
import com.tourism.assistant.domain.model.LocalTransportMode
import com.tourism.assistant.domain.model.TimeSlotActivity
import com.tourism.assistant.domain.model.TransportSegment
import com.tourism.assistant.domain.model.TransportType
import com.tourism.assistant.domain.model.TripPlan

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedFood by remember { mutableStateOf<FoodRec?>(null) }
    var pendingTransport by remember {
        mutableStateOf<Pair<TimeSlotActivity, LocalTransportMode>?>(null)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingTransport?.let { (activity, mode) ->
            when (mode) {
                LocalTransportMode.METRO -> viewModel.openMetroRoute(activity)
                LocalTransportMode.TAXI -> viewModel.openTaxiRoute(activity)
                else -> Unit
            }
        }
        pendingTransport = null
    }

    fun requestMapNavigation(activity: TimeSlotActivity, mode: LocalTransportMode) {
        pendingTransport = activity to mode
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    selectedFood?.let { food ->
        FoodDetailDialog(
            food = food,
            onDismiss = { selectedFood = null },
            onOpenApp = {
                viewModel.openFoodShop(food)
                selectedFood = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("行程规划") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Column(
                    Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            state.plan == null -> {
                Text("未找到行程", modifier = Modifier.padding(padding).padding(16.dp))
            }
            else -> {
                PlanContent(
                    plan = state.plan!!,
                    selectedFoodTaste = state.selectedFoodTaste,
                    onFoodTasteSelected = viewModel::filterFoodByTaste,
                    onBookTransport = { viewModel.deepLinkHelper.openTransportBooking(it) },
                    onBookHotel = { hotel ->
                        viewModel.deepLinkHelper.openHotelBooking(hotel.bookingUrl, hotel.name)
                    },
                    onFoodClick = { selectedFood = it },
                    onMetroNavigate = { requestMapNavigation(it, LocalTransportMode.METRO) },
                    onTaxiNavigate = { requestMapNavigation(it, LocalTransportMode.TAXI) },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun FoodDetailDialog(
    food: FoodRec,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("区域：${food.area}")
                if (food.address.isNotBlank()) {
                    Text("地址：${food.address}")
                }
                Text("口味：${food.taste} · ${food.mealType} · 人均 ¥${food.avgPrice}")
                Text("平台：${food.platform}")
                Text(food.reason)
                food.avoidTips?.let { Text("避坑：$it", color = MaterialTheme.colorScheme.error) }
                if (food.isLocalFavorite) {
                    Text("标签：本地人推荐", color = Color(0xFF2E7D32))
                }
                if (food.isInfluencerHype) {
                    Text("标签：网红打卡（注意评价差异）", color = Color(0xFFE65100))
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenApp) {
                Text("打开${food.platform}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanContent(
    plan: TripPlan,
    selectedFoodTaste: String?,
    onFoodTasteSelected: (String?) -> Unit,
    onBookTransport: (TransportSegment) -> Unit,
    onBookHotel: (HotelRec) -> Unit,
    onFoodClick: (FoodRec) -> Unit,
    onMetroNavigate: (TimeSlotActivity) -> Unit,
    onTaxiNavigate: (TimeSlotActivity) -> Unit,
    modifier: Modifier = Modifier
) {
    val foods = if (selectedFoodTaste == null) {
        plan.foods
    } else {
        plan.foods.filter { it.taste == selectedFoodTaste }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "${plan.request.origin} → ${plan.request.destination}",
            style = MaterialTheme.typography.headlineSmall
        )
        Text("${plan.request.dateRange.start} 至 ${plan.request.dateRange.end} · ${plan.request.travelers}人")

        SectionTitle("往返交通")
        TransportCard("去程", plan.transport.outbound, onBookTransport)
        TransportCard("返程", plan.transport.inbound, onBookTransport)

        SectionTitle("每日详细行程")
        plan.dailyPlans.forEach { day ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("第${day.dayIndex}天 · ${day.date}", style = MaterialTheme.typography.titleMedium)
                    day.activities.forEach { activity ->
                        Text("${activity.period}：${activity.title}", style = MaterialTheme.typography.titleSmall)
                        Text(activity.description, style = MaterialTheme.typography.bodyMedium)
                        activity.transportToNext?.let { transportLabel ->
                            TransportNavigationRow(
                                label = transportLabel,
                                destinationName = activity.nextDestinationName,
                                onMetroNavigate = { onMetroNavigate(activity) },
                                onTaxiNavigate = { onTaxiNavigate(activity) }
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("住宿推荐")
        plan.accommodations.forEach { hotel ->
            HotelCard(hotel, onBookHotel)
        }

        SectionTitle("当地美食")
        Text(
            "点击店铺查看详情，并跳转${plan.foods.firstOrNull()?.platform ?: "美团/大众点评"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedFoodTaste == null,
                onClick = { onFoodTasteSelected(null) },
                label = { Text("全部") }
            )
            listOf("辣", "微辣", "清淡", "甜").forEach { taste ->
                FilterChip(
                    selected = selectedFoodTaste == taste,
                    onClick = { onFoodTasteSelected(taste) },
                    label = { Text(taste) }
                )
            }
        }
        foods.forEach { food ->
            FoodCard(food, onClick = { onFoodClick(food) })
        }

        SectionTitle("预算分配")
        BudgetSection(plan)

        SectionTitle("天气与穿衣")
        plan.weatherTips.forEach { weather ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${weather.date} · ${weather.condition}")
                    Text("${weather.tempLow}°C ~ ${weather.tempHigh}°C · 降水 ${weather.precipitation} · ${weather.wind}")
                    Text(weather.clothingAdvice, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionTitle("当地注意事项")
        plan.localTips.forEach { tip ->
            Text("· $tip", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TransportNavigationRow(
    label: String,
    destinationName: String?,
    onMetroNavigate: () -> Unit,
    onTaxiNavigate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = buildString {
                append("→ $label")
                if (!destinationName.isNullOrBlank()) append("（前往 $destinationName）")
            },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMetroNavigate) {
                Text("地铁 · 百度地图")
            }
            OutlinedButton(onClick = onTaxiNavigate) {
                Text("打车 · 百度地图")
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun TransportCard(
    label: String,
    segment: TransportSegment,
    onBook: (TransportSegment) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                "${if (segment.type == TransportType.TRAIN) "火车" else "飞机"} ${segment.number}",
                style = MaterialTheme.typography.titleSmall
            )
            Text("${segment.departure} ${segment.departTime} → ${segment.arrival} ${segment.arriveTime}")
            Text("耗时 ${segment.duration} · ¥${segment.price}")
            segment.transferInfo?.let { Text("中转：$it") }
            Button(onClick = { onBook(segment) }) {
                Text("一键跳转预订")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotelCard(hotel: HotelRec, onBook: (HotelRec) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(hotel.name, style = MaterialTheme.typography.titleMedium)
            Text("评分 ${hotel.rating} · 近3月好评率 ${hotel.recentGoodRate}% · ¥${hotel.pricePerNight}/晚")
            Text(hotel.distanceToAttraction)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hotel.keywords.forEach { keyword ->
                    AssistChip(onClick = {}, label = { Text(keyword) })
                }
            }
            hotel.controversyWarning?.let {
                Text("争议提醒：$it", color = Color(0xFFE65100))
            }
            Button(onClick = { onBook(hotel) }) {
                Text("去${hotel.platform}预订")
            }
        }
    }
}

@Composable
private fun FoodCard(food: FoodRec, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                if (food.isLocalFavorite) {
                    AssistChip(onClick = {}, label = { Text("本地人推荐") })
                }
                if (food.isInfluencerHype) {
                    AssistChip(onClick = {}, label = { Text("网红打卡") })
                }
            }
            Text("${food.area} · ${food.taste} · ${food.mealType} · 人均 ¥${food.avgPrice}")
            if (food.address.isNotBlank()) {
                Text(food.address, style = MaterialTheme.typography.bodySmall)
            }
            Text(food.reason)
            if (food.isInfluencerHype) {
                Text("打卡热度高，但真实口味评分可能低于预期", color = Color(0xFFE65100))
            }
            food.avoidTips?.let { Text("避坑：$it", color = MaterialTheme.colorScheme.error) }
            Text(
                "点击查看详情 · 跳转${food.platform}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun BudgetSection(plan: TripPlan) {
    val breakdown = plan.budgetBreakdown
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("总预算 ¥${breakdown.total} · 已规划 ¥${breakdown.spentTotal}")
            LinearProgressIndicator(
                progress = { breakdown.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            breakdown.categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(category.name)
                    Text("¥${category.spent} / ¥${category.allocated}")
                }
            }
        }
    }
}
