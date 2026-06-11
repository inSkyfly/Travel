package com.tourism.assistant.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.Preference
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormScreen(
    onBack: () -> Unit,
    onPlanGenerated: (Long) -> Unit,
    viewModel: FormViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("表单填写") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.origin,
                onValueChange = viewModel::updateOrigin,
                label = { Text("出发地") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.destination,
                onValueChange = viewModel::updateDestination,
                label = { Text("目的地") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.startDate?.format(formatter) ?: "",
                onValueChange = { text ->
                    runCatching { LocalDate.parse(text) }.getOrNull()?.let(viewModel::updateStartDate)
                },
                label = { Text("开始日期 (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.endDate?.format(formatter) ?: "",
                onValueChange = { text ->
                    runCatching { LocalDate.parse(text) }.getOrNull()?.let(viewModel::updateEndDate)
                },
                label = { Text("结束日期 (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.travelers,
                onValueChange = viewModel::updateTravelers,
                label = { Text("出行人数") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.budgetAmount,
                onValueChange = viewModel::updateBudgetAmount,
                label = { Text("预算总额（元，可选）") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("预算等级")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BudgetLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.useBudgetLevel && state.budgetLevel == level,
                        onClick = { viewModel.updateBudgetLevel(level) },
                        label = { Text(level.label) }
                    )
                }
            }
            Text("旅行偏好（多选）")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Preference.entries.forEach { pref ->
                    FilterChip(
                        selected = state.preferences.contains(pref),
                        onClick = { viewModel.togglePreference(pref) },
                        label = { Text(pref.label) }
                    )
                }
            }
            OutlinedTextField(
                value = state.specialNeeds,
                onValueChange = viewModel::updateSpecialNeeds,
                label = { Text("其他特殊需求") },
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let { Text(it, color = Color.Red) }
            Button(
                onClick = { viewModel.generatePlan { plan -> onPlanGenerated(plan.id) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isGenerating
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("生成行程")
            }
        }
    }
}
