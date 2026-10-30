package com.dailybudget.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToLong

data class BudgetCalculationResult(
    val dailyLimitCents: Long,
    val remainingDays: Int,
    val remainingBudgetCents: Long,
    val isCycleEnded: Boolean
) {
    val dailyLimitRubles: Double get() = dailyLimitCents / 100.0
    val remainingBudgetRubles: Double get() = remainingBudgetCents / 100.0
    val roundedDailyLimit: Long get() = (dailyLimitCents / 100.0).roundToLong()
}

object BudgetCalculator {

    fun calculate(
        totalBudgetCents: Long,
        cycleDays: Int,
        startDate: LocalDate,
        today: LocalDate,
        spentBeforeTodayCents: Long,
        spentTodayCents: Long
    ): BudgetCalculationResult {
        val rawDaysPassed = ChronoUnit.DAYS.between(startDate, today).toInt()
        val daysPassed = max(0, rawDaysPassed)

        val totalDaysLeft = cycleDays - daysPassed
        val isCycleEnded = totalDaysLeft <= 0

        val remainingDays = if (isCycleEnded) 0 else totalDaysLeft
        val totalSpentCents = spentBeforeTodayCents + spentTodayCents
        val remainingBudgetCents = totalBudgetCents - totalSpentCents

        val dailyLimitCents = when {
            isCycleEnded -> 0L
            remainingDays == 1 -> (totalBudgetCents - spentBeforeTodayCents) - spentTodayCents
            else -> {
                val budgetAtStartOfToday = totalBudgetCents - spentBeforeTodayCents
                val baseDailyLimit = budgetAtStartOfToday / remainingDays
                baseDailyLimit - spentTodayCents
            }
        }

        return BudgetCalculationResult(
            dailyLimitCents = dailyLimitCents,
            remainingDays = remainingDays,
            remainingBudgetCents = remainingBudgetCents,
            isCycleEnded = isCycleEnded
        )
    }
}
