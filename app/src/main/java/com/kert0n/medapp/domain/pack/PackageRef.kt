package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.medkit.MedKitRef
import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import kotlin.uuid.Uuid

/**
 * Ссылка на пачку из чужого агрегата — честный список того, что курсу, приёму и движению нужно
 * о ней знать: тождество, имя, единица, форма, годится ли она для запаса и где лежит.
 * Переходов у ссылки нет: списать или переименовать пачку через копию, лежащую внутри курса,
 * нечем — это делает сама [Package] в транзакции, которая её прочитала.
 *
 * Равенство по [id]: ссылка указывает на вещь, и та же пачка с новым именем — та же ссылка.
 */
class PackageRef(
    val id: Uuid,
    val name: String,
    val unit: QuantityUnit,
    val form: DosageForm?,
    val lifecycle: Package.Lifecycle,
    val access: Package.Access,
    val medKit: MedKitRef
) {
    /** Берут ли из этой пачки — то же правило, что у самой пачки. */
    val suppliesStock: Boolean get() = Package.suppliesStock(lifecycle, access)

    override fun equals(other: Any?): Boolean =
        this === other || (other is PackageRef && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "PackageRef(id=$id, name=$name, unit=$unit)"
}
