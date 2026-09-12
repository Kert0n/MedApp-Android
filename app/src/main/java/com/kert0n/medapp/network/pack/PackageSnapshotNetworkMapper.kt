package com.kert0n.medapp.network.pack

import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.domain.pack.Claims
import com.kert0n.medapp.domain.pack.Package
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.domain.value.Quantity
import com.kert0n.medapp.domain.value.Vocabulary
import com.kert0n.medapp.network.value.formOrMiss
import com.kert0n.medapp.network.value.unitOrMiss
import java.math.BigDecimal
import java.time.Instant

/**
 * Снимок пачки с сервера, собранный в домен: подтверждённое состояние и его обвязка вместе,
 * потому что порознь с провода они не приходят (PLAN E4).
 */
class PackageSnapshot(val pack: Package, val sync: PackageSyncState)

/**
 * Знает ли снимок словаря всё, что называет этот снимок пачки. Промах — `VocabularyMiss`, как
 * и у полного разбора: тот, кто держит словарь, дочитывает его до того, как снимок ляжет в базу.
 */
fun PackageSnapshotNetworkDTO.requireKnownIn(vocabulary: Vocabulary) {
    vocabulary.unitOrMiss(pack.unitId)
    pack.formId?.let(vocabulary::formOrMiss)
}

/**
 * Провод → домен. Единица и форма приходят идентификаторами и разрешаются по снимку словаря;
 * промах — `VocabularyMiss`, и решает его резолвер, а не этот маппер. Аптечку приносит вызывающий:
 * снимок называет её номером, а объект есть у того, кто читает базу. Личных сведений в снимке
 * нет по контракту: [addedAt] — момент первого наблюдения чужой пачки, свою вызывающий заводит
 * сам. Пачка на сервере жива по определению — нулевой остаток сервер уничтожает.
 */
fun PackageSnapshotNetworkDTO.toDomain(
    vocabulary: Vocabulary,
    medKit: MedKit,
    addedAt: Instant,
    observedAt: Instant
): PackageSnapshot = PackageSnapshot(
    pack = Package(
        id = pack.id,
        medKit = medKit.also { require(it.id == pack.medKitId) { "снимок пачки называет другую аптечку" } },
        facts = PackageFacts(
            shared = PackageSharedFacts(
                name = pack.name,
                form = pack.formId?.let(vocabulary::formOrMiss),
                category = pack.category,
                manufacturer = pack.manufacturer,
                country = pack.country,
                description = pack.description
            )
        ),
        quantity = Quantity(BigDecimal(pack.amount), vocabulary.unitOrMiss(pack.unitId)),
        addedAt = addedAt,
        claims = Claims(total = BigDecimal(claims.total), mine = claims.mine?.let(::BigDecimal))
    ),
    sync = PackageSyncState(
        packageId = pack.id,
        version = pack.version,
        claimsVersion = claims.version,
        syncedAt = observedAt
    )
)
