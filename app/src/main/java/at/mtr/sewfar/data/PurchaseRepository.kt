package at.mtr.sewfar.data

import androidx.room.withTransaction
import at.mtr.sewfar.data.db.DeletionToken
import at.mtr.sewfar.data.db.PurchaseEntity
import at.mtr.sewfar.data.db.SewFarDatabase
import at.mtr.sewfar.data.db.SoftDeleter
import at.mtr.sewfar.domain.Ids
import at.mtr.sewfar.domain.PurchaseCategory
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The purchases log (SPEC 4.3). */
class PurchaseRepository(private val db: SewFarDatabase, private val deleter: SoftDeleter, private val time: TimeSource) {
    private val dao = db.purchaseDao()

    fun observeAll(): Flow<List<PurchaseEntity>> = dao.observeAll()
    fun observe(id: String): Flow<PurchaseEntity?> = dao.observeOne(id)

    suspend fun create(name: String, category: PurchaseCategory, priceCents: Long, boughtOn: LocalDate): String {
        val id = Ids.random()
        val now = time.nowMillis()
        dao.insert(PurchaseEntity(id, name.trim(), category, priceCents, boughtOn, createdAt = now, updatedAt = now))
        return id
    }

    suspend fun edit(id: String, change: (PurchaseEntity) -> PurchaseEntity) = db.withTransaction {
        val current = dao.get(id) ?: return@withTransaction
        val next = change(current)
        if (next != current) dao.update(next.copy(updatedAt = time.nowMillis()))
    }

    suspend fun delete(id: String): DeletionToken? = deleter.delete("purchase", id)
}
