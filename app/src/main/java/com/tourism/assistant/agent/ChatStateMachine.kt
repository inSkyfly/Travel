package com.tourism.assistant.agent

import com.tourism.assistant.domain.TripRequestBuilder
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.Preference
import com.tourism.assistant.util.FlexibleDateParser

enum class ChatStep {
    GREETING,
    DESTINATION,
    ORIGIN,
    DATES,
    TRAVELERS,
    BUDGET,
    PREFERENCES,
    SPECIAL_NEEDS,
    COMPLETE
}

class ChatStateMachine(
    private val builder: TripRequestBuilder = TripRequestBuilder()
) {
    private var step = ChatStep.GREETING
    private var preferenceBuffer = mutableSetOf<Preference>()

    fun currentStep(): ChatStep = step

    fun isDialogComplete(): Boolean = step == ChatStep.COMPLETE

    fun getBuilder(): TripRequestBuilder = builder

    fun startConversation(): String {
        step = ChatStep.DESTINATION
        return "您好！我是您的旅游助手。请告诉我您想去哪里旅行？"
    }

    fun processInput(input: String): Pair<String, Boolean> {
        val text = input.trim()
        if (text.isEmpty()) {
            return "请再补充一些信息哦～" to false
        }

        return when (step) {
            ChatStep.GREETING -> startConversation() to false
            ChatStep.DESTINATION -> {
                builder.destination = text
                step = ChatStep.ORIGIN
                "好的，${builder.destination}是个不错的选择！请问您从哪个城市出发？" to false
            }
            ChatStep.ORIGIN -> {
                builder.origin = text
                step = ChatStep.DATES
                "请告诉我出行日期范围，例如：0610-0614、6月10到14号、2026年6月10到6月14日 都可以" to false
            }
            ChatStep.DATES -> {
                val range = FlexibleDateParser.parseRange(text)
                if (range == null) {
                    "没太看懂日期，您可以试试：0610-0614、20260610-20260614、2026年6月10号到6月14号" to false
                } else {
                    builder.startDate = range.first
                    builder.endDate = range.second
                    step = ChatStep.TRAVELERS
                    val recognized = FlexibleDateParser.formatRange(range.first, range.second)
                    "好的，已识别出行时间：$recognized。一共几位出行呢？" to false
                }
            }
            ChatStep.TRAVELERS -> {
                val count = text.filter { it.isDigit() }.toIntOrNull()
                if (count == null || count <= 0) {
                    "请输入有效的人数，例如：2" to false
                } else {
                    builder.travelers = count
                    step = ChatStep.BUDGET
                    "您的预算大概是？可以说具体金额（如5000），或选择：经济/舒适/豪华" to false
                }
            }
            ChatStep.BUDGET -> {
                if (!parseBudget(text)) {
                    "请回复金额数字，或 经济/舒适/豪华 其中一个" to false
                } else {
                    step = ChatStep.PREFERENCES
                    preferenceBuffer.clear()
                    preferencePrompt() to false
                }
            }
            ChatStep.PREFERENCES -> handlePreferences(text)
            ChatStep.SPECIAL_NEEDS -> {
                builder.specialNeeds = text
                step = ChatStep.COMPLETE
                summaryMessage() to true
            }
            ChatStep.COMPLETE -> {
                "需求已收集完成，您可以点击「生成行程」查看完整规划。" to true
            }
        }
    }

    private fun handlePreferences(text: String): Pair<String, Boolean> {
        if (text.contains("完成") || text.contains("好了") || text.contains("没有") || text == "无") {
            builder.preferences = preferenceBuffer.toMutableSet()
            step = ChatStep.SPECIAL_NEEDS
            return "还有其他特殊需求吗？比如带老人小孩、不想爬山、爱吃辣等（没有请回复「无」）" to false
        }

        val matched = Preference.entries.filter { pref ->
            text.contains(pref.label) || text.contains(pref.name, ignoreCase = true)
        }
        if (matched.isEmpty()) {
            return "没有识别到偏好，请从以下选择（可多选，回复编号或名称，完成后回复「完成」）：\n${preferenceOptions()}" to false
        }
        preferenceBuffer.addAll(matched)
        return "已记录：${preferenceBuffer.joinToString("、") { it.label }}。继续选择或回复「完成」" to false
    }

    private fun preferencePrompt(): String {
        return "您有哪些旅行偏好？可多选，完成后回复「完成」：\n${preferenceOptions()}"
    }

    private fun preferenceOptions(): String {
        return Preference.entries.mapIndexed { index, pref ->
            "${index + 1}. ${pref.label}"
        }.joinToString("\n")
    }

    private fun parseBudget(text: String): Boolean {
        val amount = text.filter { it.isDigit() }.toIntOrNull()
        if (amount != null && amount > 0) {
            builder.budgetAmount = amount
            builder.budgetLevel = null
            return true
        }
        return when {
            text.contains("经济") -> {
                builder.budgetLevel = BudgetLevel.ECONOMY
                builder.budgetAmount = null
                true
            }
            text.contains("豪华") -> {
                builder.budgetLevel = BudgetLevel.LUXURY
                builder.budgetAmount = null
                true
            }
            text.contains("舒适") -> {
                builder.budgetLevel = BudgetLevel.COMFORT
                builder.budgetAmount = null
                true
            }
            else -> false
        }
    }

    private fun summaryMessage(): String {
        val prefs = if (builder.preferences.isEmpty()) "无特别偏好" else builder.preferences.joinToString("、") { it.label }
        return buildString {
            appendLine("太好了，我已了解您的需求：")
            appendLine("· 出发地：${builder.origin}")
            appendLine("· 目的地：${builder.destination}")
            appendLine("· 日期：${builder.startDate} 至 ${builder.endDate}")
            appendLine("· 人数：${builder.travelers} 人")
            appendLine("· 偏好：$prefs")
            if (builder.specialNeeds.isNotBlank()) {
                appendLine("· 特殊需求：${builder.specialNeeds}")
            }
            append("点击下方「生成行程」即可查看完整规划。")
        }
    }

    fun reset() {
        step = ChatStep.GREETING
        preferenceBuffer.clear()
        builder.origin = ""
        builder.destination = ""
        builder.startDate = null
        builder.endDate = null
        builder.travelers = 1
        builder.budgetAmount = null
        builder.budgetLevel = BudgetLevel.COMFORT
        builder.preferences.clear()
        builder.specialNeeds = ""
    }
}
