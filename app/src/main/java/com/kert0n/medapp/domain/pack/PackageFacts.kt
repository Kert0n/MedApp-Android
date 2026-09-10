package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.Money
import com.kert0n.medapp.domain.value.Dose
import com.kert0n.medapp.domain.value.requireOptionalText
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Что человек знает про упаковку и правит одним редактором. [shared] уезжает на сервер, остальное
 * личное и остаётся на устройстве (PLAN C0). `null` — сведений нет, и сохранение с `null` очищает
 * поле; обратный смысл `null` у PATCH переводит сетевой маппер (D3). Количества здесь нет: его
 * меняет пересчёт.
 */
data class PackageFacts(
    val shared: PackageSharedFacts,
    val expiresOn: ExpiryDate? = null,
    val defaultIntakeAmount: Dose? = null,
    val note: String? = null,
    val price: Money? = null,
    val purchasedOn: LocalDate? = null,
    val openedOn: LocalDate? = null
) {
    init {
        requireOptionalText(note, NOTE_MAX_LENGTH, "PackageFacts.note")
    }

    val name: String get() = shared.name
    val formId: Uuid? get() = shared.formId
    val category: String? get() = shared.category
    val manufacturer: String? get() = shared.manufacturer
    val country: String? get() = shared.country
    val description: String? get() = shared.description

    /** Срока нет — не просрочена: про неизвестный срок мы не знаем ничего. */
    fun isExpiredOn(date: LocalDate): Boolean = expiresOn?.isExpiredOn(date) == true

    /** Истекает ли срок не позже чем через [days] дней, считая [date] включительно (D8). */
    fun expiresWithin(date: LocalDate, days: Long): Boolean =
        expiresOn?.expiresWithin(date, days) == true

    companion object {
        const val NOTE_MAX_LENGTH = 200
    }
}
