package com.kert0n.medapp.domain.pack

import com.kert0n.medapp.domain.value.requireOptionalText
import com.kert0n.medapp.domain.value.requireText
import kotlin.uuid.Uuid

/**
 * Та часть сведений об упаковке, которая переходит границу публикации: описание **препарата**, а
 * не этой коробки.
 *
 * Граница C0 живёт здесь, в структуре, а не в комментарии над плоским списком полей. Отсюда два
 * следствия. Первое: шесть текстовых инвариантов объявлены один раз и не могут разойтись между
 * упаковкой и намерением отправки. Второе: вопрос «правка была только локальной?» — это
 * `before.shared == after.shared`, а не сравнение шести полей россыпью, где седьмое забудут.
 *
 * Личные срок годности, доза-подсказка, заметка, цена и даты покупки остаются в [PackageFacts] и
 * на сервер не уезжают вовсе: он их не хранит и не будет (PLAN C0, E2).
 *
 * Имя — `PackageSharedFacts`, а не `PackageDescription`: описание уже есть внутри, и второе имя
 * дало бы `facts.description.description`.
 */
data class PackageSharedFacts(
    val name: String,
    val formId: Uuid? = null,
    val category: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val description: String? = null
) {
    init {
        requireText(name, PACKAGE_NAME_MAX_LENGTH, "PackageSharedFacts.name")
        requireOptionalText(category, PACKAGE_CATEGORY_MAX_LENGTH, "PackageSharedFacts.category")
        requireOptionalText(
            manufacturer,
            PACKAGE_MANUFACTURER_MAX_LENGTH,
            "PackageSharedFacts.manufacturer"
        )
        requireOptionalText(country, PACKAGE_COUNTRY_MAX_LENGTH, "PackageSharedFacts.country")
        requireOptionalText(
            description,
            PACKAGE_DESCRIPTION_MAX_LENGTH,
            "PackageSharedFacts.description"
        )
    }
}
