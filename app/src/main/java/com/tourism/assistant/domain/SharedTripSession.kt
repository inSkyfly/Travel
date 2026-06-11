package com.tourism.assistant.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedTripSession @Inject constructor() {
    private val _builder = MutableStateFlow(TripRequestBuilder())
    val builder = _builder.asStateFlow()

    fun update(block: (TripRequestBuilder) -> Unit) {
        val snapshot = _builder.value.toBuilderSnapshot()
        block(snapshot)
        _builder.value = snapshot
    }

    fun reset() {
        _builder.value = TripRequestBuilder()
    }

    fun currentBuilder(): TripRequestBuilder = _builder.value.toBuilderSnapshot()
}
