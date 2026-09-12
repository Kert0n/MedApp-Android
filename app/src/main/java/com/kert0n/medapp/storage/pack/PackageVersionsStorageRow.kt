package com.kert0n.medapp.storage.pack

import androidx.room.ColumnInfo

/**
 * Версии пачки, уже лежащие в базе. Их две, и они независимы: состояние пачки и картину броней
 * двигают разные команды и разные люди (PLAN B3, E1). Читаются вместе, потому что решение о
 * снимке принимается по обеим; пусто — о пачке база ещё ничего не знает.
 */
class PackageVersionsStorageRow(
    val version: Long?,
    @ColumnInfo(name = "claims_version") val claimsVersion: Long?
)
