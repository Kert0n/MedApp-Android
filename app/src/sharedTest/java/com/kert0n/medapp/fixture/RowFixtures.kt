package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.network.intake.IntakeSyncState
import com.kert0n.medapp.network.pack.PackageSyncState
import com.kert0n.medapp.storage.intake.IntakeStorageRow
import com.kert0n.medapp.storage.intake.toStorageEntity
import com.kert0n.medapp.storage.medkit.toStorageEntity as toMedKitStorageEntity
import com.kert0n.medapp.storage.pack.PackageStorageRow
import com.kert0n.medapp.storage.pack.toDetailsStorageEntity
import com.kert0n.medapp.storage.pack.toStorageEntity
import com.kert0n.medapp.storage.pack.toStorageEntity as toClaimsStorageEntity
import com.kert0n.medapp.domain.intake.CourseIntake
import com.kert0n.medapp.storage.stock.StockMovementStorageRow
import com.kert0n.medapp.storage.stock.toStorageEntity as toMovementStorageEntity

/**
 * Строки хранения, собранные из доменных объектов так, как их собрала бы база: связи заполнены
 * теми же пачками и аптечками, что вошли в объект. Круговой тест маппера без базы.
 */
fun Package.toStorageRow(sync: PackageSyncState = PackageSyncState(id)): PackageStorageRow =
    PackageStorageRow(
        pack = toStorageEntity(sync),
        details = toDetailsStorageEntity(),
        claims = claims?.toClaimsStorageEntity(id),
        medKit = medKit.toMedKitStorageEntity()
    )

fun Intake.toStorageRow(sync: IntakeSyncState = IntakeSyncState(id)): IntakeStorageRow =
    IntakeStorageRow(
        intake = toStorageEntity(sync),
        planned = (this as? CourseIntake)?.plannedPackage?.toStorageRow(),
        taken = taken?.pkg?.toStorageRow()
    )

fun StockMovement.toStorageRow(): StockMovementStorageRow {
    fun MedKit.row() = toMedKitStorageEntity()
    return StockMovementStorageRow(
        movement = toMovementStorageEntity(),
        pack = pkg.toStorageRow(),
        medKit = when (this) {
            is StockMovement.Receipt -> medKit.row()
            is StockMovement.Recount -> medKit.row()
            is StockMovement.Disposal -> medKit.row()
            is StockMovement.RemoteChange -> medKit.row()
            is StockMovement.AccessLoss -> medKit.row()
            is StockMovement.Transfer -> null
        },
        source = (this as? StockMovement.Transfer)?.source?.row(),
        target = (this as? StockMovement.Transfer)?.target?.row()
    )
}
