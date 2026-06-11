package com.tourism.assistant.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tourism.assistant.domain.model.TripPlan
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateChat: () -> Unit,
    onNavigateForm: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val plans by viewModel.savedPlans.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("旅游助手") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("新建行程", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateChat)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Text("对话规划", style = MaterialTheme.typography.titleMedium)
                    Text("与 AI 助手多轮对话，逐步细化出行需求")
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateForm)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("表单填写", style = MaterialTheme.typography.titleMedium)
                    Text("直接填写出发地、日期、预算与偏好")
                }
            }

            Text("历史行程", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            if (plans.isEmpty()) {
                Text("暂无保存的行程", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(plans, key = { it.id }) { plan ->
                        SavedPlanItem(plan = plan, onClick = { onOpenPlan(plan.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPlanItem(plan: TripPlan, onClick: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val timeText = Instant.ofEpochMilli(plan.createdAt)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${plan.request.origin} → ${plan.request.destination}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${plan.request.dateRange.start} 至 ${plan.request.dateRange.end} · ${plan.request.travelers}人",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(timeText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
