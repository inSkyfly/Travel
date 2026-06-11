package com.tourism.assistant.data.local

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.tourism.assistant.domain.model.BudgetInput
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.repository.TripPlanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripPlanLocalRepository @Inject constructor(
    private val dao: SavedPlanDao,
    private val gson: Gson
) : TripPlanRepository {

    override fun getAllPlans(): Flow<List<TripPlan>> {
        return dao.getAllPlans().map { entities ->
            entities.mapNotNull { entity ->
                runCatching {
                    gson.fromJson(entity.planJson, TripPlan::class.java).copy(id = entity.id)
                }.getOrNull()
            }
        }
    }

    override suspend fun getPlanById(id: Long): TripPlan? {
        val entity = dao.getPlanById(id) ?: return null
        return gson.fromJson(entity.planJson, TripPlan::class.java).copy(id = entity.id)
    }

    override suspend fun savePlan(plan: TripPlan): Long {
        val title = "${plan.request.origin} → ${plan.request.destination}"
        val entity = SavedPlanEntity(
            id = plan.id,
            title = title,
            planJson = gson.toJson(plan),
            createdAt = plan.createdAt
        )
        return dao.insert(entity)
    }

    override suspend fun deletePlan(id: Long) {
        dao.deleteById(id)
    }
}

class LocalDateAdapter : TypeAdapter<LocalDate>() {
    override fun write(out: JsonWriter, value: LocalDate?) {
        if (value == null) out.nullValue() else out.value(value.toString())
    }

    override fun read(`in`: JsonReader): LocalDate? {
        val text = `in`.nextString()
        return if (text.isBlank()) null else LocalDate.parse(text)
    }
}

class BudgetInputAdapter : TypeAdapter<BudgetInput>() {
    override fun write(out: JsonWriter, value: BudgetInput?) {
        when (value) {
            is BudgetInput.Amount -> {
                out.beginObject()
                out.name("type").value("amount")
                out.name("total").value(value.total)
                out.endObject()
            }
            is BudgetInput.Level -> {
                out.beginObject()
                out.name("type").value("level")
                out.name("level").value(value.level.name)
                out.endObject()
            }
            null -> out.nullValue()
        }
    }

    override fun read(`in`: JsonReader): BudgetInput {
        `in`.beginObject()
        var type = ""
        var total = 0
        var level = BudgetLevel.COMFORT.name
        while (`in`.hasNext()) {
            when (`in`.nextName()) {
                "type" -> type = `in`.nextString()
                "total" -> total = `in`.nextInt()
                "level" -> level = `in`.nextString()
                else -> `in`.skipValue()
            }
        }
        `in`.endObject()
        return if (type == "amount") {
            BudgetInput.Amount(total)
        } else {
            BudgetInput.Level(BudgetLevel.valueOf(level))
        }
    }
}

object GsonProvider {
    fun create(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
            .registerTypeAdapter(BudgetInput::class.java, BudgetInputAdapter())
            .create()
    }
}
