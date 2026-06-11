package com.tourism.assistant.ui.navigation

object Routes {
    const val HOME = "home"
    const val CHAT = "chat"
    const val FORM = "form"
    const val PLAN = "plan/{planId}"
    const val PLAN_GENERATED = "plan_generated"

    fun plan(planId: Long) = "plan/$planId"
}
