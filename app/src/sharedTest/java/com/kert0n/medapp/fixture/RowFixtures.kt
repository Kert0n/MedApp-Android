package com.kert0n.medapp.fixture

import com.kert0n.medapp.domain.intake.Intake
import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.storage.medkit.MedKitStorageEntity
import com.kert0n.medapp.storage.pack.PackageRefStorageRow
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
        medKit = medKit.row()
    )

/** Строка аптечки по ссылке: имя и место у ссылки не спрашивают, их даёт фикстура. */
fun MedKitRef.row(): MedKitStorageEntity = medKit(id = id, publication = publication).toMedKitStorageEntity()

/** Строка ссылки: серверная часть пачки с тем, что ссылка о ней знает; остаток — фикстурный. */
fun PackageRef.toStorageRow(): PackageRefStorageRow = PackageRefStorageRow(
    pack = pack(
        id = id,
        medKit = medKit,
        name = name,
        quantity = com.kert0n.medapp.domain.value.Quantity(java.math.BigDecimal("20"), unit),
        form = form,
        lifecycle = lifecycle,
        access = access
    ).toStorageEntity(PackageSyncState(id)),
    medKit = medKit.row()
)

fun Intake.toStorageRow(sync: IntakeSyncState = IntakeSyncState(id)): IntakeStorageRow =
    IntakeStorageRow(
        intake = toStorageEntity(sync),
        planned = (this as? CourseIntake)?.plannedPackage?.toStorageRow(),
        taken = taken?.pkg?.toStorageRow()
    )

fun StockMovement.toStorageRow(): StockMovementStorageRow {
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
