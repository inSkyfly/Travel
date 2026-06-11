package com.tourism.assistant.util

import kotlinx.coroutines.delay

/** 打字机效果：分析过程 + 正文，默认每字 10ms */
object ChatStreamRenderer {

    suspend fun streamReply(
        analysisLines: List<String>,
        reply: String,
        onUpdate: (analysisText: String, content: String, isStreaming: Boolean) -> Unit,
        charDelayMs: Long = 10L
    ) {
        var analysis = ""
        for (line in analysisLines) {
            for (ch in line) {
                analysis += ch
                onUpdate(analysis, "", true)
                delay(charDelayMs)
            }
            analysis += "\n"
            onUpdate(analysis, "", true)
            delay(charDelayMs * 4)
        }

        var content = ""
        for (ch in reply) {
            content += ch
            onUpdate(analysis, content, true)
            delay(charDelayMs)
        }
        onUpdate(analysis, content, false)
    }

    /** 仅流式输出正文，保留已有分析文案 */
    suspend fun streamContentOnly(
        preservedAnalysis: String,
        reply: String,
        onUpdate: (analysisText: String, content: String, isStreaming: Boolean) -> Unit,
        charDelayMs: Long = 10L
    ) {
        var content = ""
        for (ch in reply) {
            content += ch
            onUpdate(preservedAnalysis, content, true)
            delay(charDelayMs)
        }
        onUpdate(preservedAnalysis, content, false)
    }
}
