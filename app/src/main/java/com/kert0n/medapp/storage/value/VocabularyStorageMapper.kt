package com.kert0n.medapp.storage.value

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import kotlin.uuid.Uuid

/**
 * Единица по колонке. Строки держат идентификаторы, а домену нужны объекты, и берутся они из
 * снимка словаря; промах здесь — нарушенный инвариант хранения, а не состояние: в базу
 * количество ложится только вместе со своей единицей, а словарь не убывает (PLAN F1).
 */
fun Vocabulary.storedUnit(id: Uuid): QuantityUnit =
    checkNotNull(unit(id)) { "единица $id записана в строке, но её нет в словаре" }

fun Vocabulary.storedForm(id: Uuid): DosageForm =
    checkNotNull(form(id)) { "форма $id записана в строке, но её нет в словаре" }
