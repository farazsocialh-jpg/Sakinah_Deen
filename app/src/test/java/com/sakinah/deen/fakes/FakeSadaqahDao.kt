package com.sakinah.deen.fakes

import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSadaqahDao : SadaqahDao {
    private val logs = MutableStateFlow<List<SadaqahLogEntity>>(emptyList())

    override fun observeMonthlyTotal(startTimestamp: Long, endTimestamp: Long): Flow<Double?> =
        logs.map { list -> list.filter { it.timestamp in startTimestamp..endTimestamp }.sumOf { it.amount } }

    override fun observeRecentLogs(limit: Int): Flow<List<SadaqahLogEntity>> =
        logs.map { it.take(limit) }

    override suspend fun insertLog(log: SadaqahLogEntity) {
        logs.value = logs.value + log
    }

    override suspend fun deleteLog(id: String) {
        logs.value = logs.value.filterNot { it.id == id }
    }
}