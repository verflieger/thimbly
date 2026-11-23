package at.mtr.sewfar.data.seed

import androidx.room.withTransaction
import at.mtr.sewfar.data.ListKind
import at.mtr.sewfar.data.TimeSource
import at.mtr.sewfar.data.db.LessonTagEntity
import at.mtr.sewfar.data.db.MeasurementTypeEntity
import at.mtr.sewfar.data.db.PatternCategoryEntity
import at.mtr.sewfar.data.db.PurposeEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.domain.Ids

/**
 * Inserts missing built-in list entries with deterministic IDs (SPEC 1). Existing rows, including ones the user
 * renamed, hid or reordered, are never touched, so running it on every start is safe.
 */
class Seeder(private val db: SewFarDatabase, private val time: TimeSource) {
    suspend fun seed() {
        val now = time.nowMillis()
        val dao = db.listDao()
        fun rows(kind: ListKind) = BuiltIns.keys.getValue(kind).mapIndexed { index, key ->
            Triple(Ids.builtIn(kind.table, key), key, index)
        }
        db.withTransaction {
            dao.seedMeasurementTypes(rows(ListKind.MEASUREMENT_TYPE).map { (id, key, order) ->
                MeasurementTypeEntity(id, builtinKey = key, sortOrder = order, createdAt = now, updatedAt = now)
            })
            dao.seedPatternCategories(rows(ListKind.PATTERN_CATEGORY).map { (id, key, order) ->
                PatternCategoryEntity(id, builtinKey = key, sortOrder = order, createdAt = now, updatedAt = now)
            })
            dao.seedLessonTags(rows(ListKind.LESSON_TAG).map { (id, key, order) ->
                LessonTagEntity(id, builtinKey = key, sortOrder = order, createdAt = now, updatedAt = now)
            })
            dao.seedPurposes(rows(ListKind.PURPOSE).map { (id, key, order) ->
                PurposeEntity(id, builtinKey = key, sortOrder = order, createdAt = now, updatedAt = now)
            })
        }
    }
}
