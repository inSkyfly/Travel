package com.tourism.assistant.util

import com.tourism.assistant.domain.model.BudgetBreakdown
import com.tourism.assistant.domain.model.BudgetCategory
import com.tourism.assistant.domain.model.BudgetInput
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.DateRange

object BudgetCalculator {

    private val levelTotals = mapOf(
        BudgetLevel.ECONOMY to 3000,
        BudgetLevel.COMFORT to 6000,
        BudgetLevel.LUXURY to 12000
    )

    private val ratios = mapOf(
        BudgetLevel.ECONOMY to listOf(0.35f, 0.25f, 0.20f, 0.12f, 0.08f),
        BudgetLevel.COMFORT to listOf(0.30f, 0.30f, 0.22f, 0.12f, 0.06f),
        BudgetLevel.LUXURY to listOf(0.25f, 0.35f, 0.25f, 0.10f, 0.05f)
    )

    private val categoryNames = listOf("交通", "住宿", "餐饮", "门票", "其他")

    fun resolveTotal(budget: BudgetInput, dayCount: Int, travelers: Int): Int {
        return when (budget) {
            is BudgetInput.Amount -> budget.total
            is BudgetInput.Level -> {
                val base = levelTotals[budget.level] ?: 6000
                (base * dayCount / 3f * travelers / 2f).toInt().coerceAtLeast(1500)
            }
        }
    }

    fun resolveLevel(budget: BudgetInput): BudgetLevel {
        return when (budget) {
            is BudgetInput.Level -> budget.level
            is BudgetInput.Amount -> when {
                budget.total < 4000 -> BudgetLevel.ECONOMY
                budget.total < 9000 -> BudgetLevel.COMFORT
                else -> BudgetLevel.LUXURY
            }
        }
    }

    fun buildBreakdown(
        budget: BudgetInput,
        dateRange: DateRange,
        travelers: Int,
        transportCost: Int,
        hotelCost: Int,
        foodCost: Int,
        ticketCost: Int
    ): BudgetBreakdown {
        val total = resolveTotal(budget, dateRange.dayCount, travelers)
        val level = resolveLevel(budget)
        val ratioList = ratios[level] ?: ratios[BudgetLevel.COMFORT]!!
        val allocated = ratioList.map { (total * it).toInt() }
        val otherSpent = (total * 0.05f).toInt()
        val categories = categoryNames.mapIndexed { index, name ->
            val spent = when (index) {
                0 -> transportCost
                1 -> hotelCost
                2 -> foodCost
                3 -> ticketCost
                else -> otherSpent
            }
            BudgetCategory(name, allocated[index], spent.coerceAtMost(allocated[index] + 500))
        }
        return BudgetBreakdown(total = total, categories = categories)
    }
}
