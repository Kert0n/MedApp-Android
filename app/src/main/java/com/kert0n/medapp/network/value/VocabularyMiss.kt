package com.kert0n.medapp.network.value

import com.kert0n.medapp.domain.value.DosageForm
import com.kert0n.medapp.domain.value.QuantityUnit
import com.kert0n.medapp.domain.value.Vocabulary
import kotlin.uuid.Uuid

/**
 * Снимок словаря старее того, кто назвал единицу или форму. Не «такого нет»: словарь только
 * растёт, и промах лечится чтением с сервера, а без связи — это задержка с названной причиной.
 * Исключение, а не `null`, потому что промах случается глубоко в разборе, а решает его тот, кто
 * снимок держит, — [VocabularyResolver].
 */
class VocabularyMiss(val kind: Kind, val id: Uuid) : IllegalStateException("$kind $id не в снимке словаря") {

    enum class Kind { UNIT, FORM }
}

/** Единица из снимка — или промах, который разбор наверх не глотает. */
fun Vocabulary.unitOrMiss(id: Uuid): QuantityUnit =
    unit(id) ?: throw VocabularyMiss(VocabularyMiss.Kind.UNIT, id)

fun Vocabulary.formOrMiss(id: Uuid): DosageForm =
    form(id) ?: throw VocabularyMiss(VocabularyMiss.Kind.FORM, id)
