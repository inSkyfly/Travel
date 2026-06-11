package com.tourism.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tourism.assistant.domain.model.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onPlanGenerated: (Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val lastMessage = uiState.messages.lastOrNull()

    LaunchedEffect(
        uiState.messages.size,
        lastMessage?.content,
        lastMessage?.analysisText,
        lastMessage?.isStreaming
    ) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("对话规划")
                        if (uiState.aiBackendHint.isNotBlank()) {
                            Text(
                                uiState.aiBackendHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isComplete) {
                    Button(
                        onClick = { viewModel.generatePlan { plan -> onPlanGenerated(plan.id) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isGenerating
                    ) {
                        if (uiState.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                        }
                        Text("生成行程")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = viewModel::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入您的回答…") },
                        singleLine = true,
                        enabled = !uiState.isReplying
                    )
                    IconButton(
                        onClick = viewModel::sendMessage,
                        enabled = !uiState.isReplying
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
                uiState.error?.let { Text(it, color = Color.Red) }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    var analysisExpanded by remember(message.id) { mutableStateOf(message.isAnalysisExpanded) }
    LaunchedEffect(message.isStreaming, message.isAnalysisExpanded) {
        analysisExpanded = if (message.isStreaming) true else message.isAnalysisExpanded
    }

    val alignment = if (message.isFromAgent) Alignment.CenterStart else Alignment.CenterEnd
    val bg = if (message.isFromAgent) Color(0xFFE3F2FD) else Color(0xFFDCEDC8)
    val analysisColor = Color(0xFF78909C)
    val bodyColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(bg, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (message.isFromAgent && message.analysisText.isNotBlank()) {
                if (message.isStreaming || analysisExpanded) {
                    if (!message.isStreaming) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { analysisExpanded = false },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "收起分析",
                                style = MaterialTheme.typography.labelSmall,
                                color = analysisColor
                            )
                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = "收起",
                                tint = analysisColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = message.analysisText.trimEnd(),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = analysisColor
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { analysisExpanded = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "查看分析过程",
                            style = MaterialTheme.typography.labelSmall,
                            color = analysisColor
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "展开",
                            tint = analysisColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (message.content.isNotBlank()) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor
                )
            } else if (message.isStreaming && message.isFromAgent) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在生成…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
