package com.kert0n.medapp.presentation.pack

import com.kert0n.medapp.presentation.value.ExpiryDatePresentationError
import com.kert0n.medapp.presentation.value.FormPresentationDTO
import com.kert0n.medapp.presentation.value.MoneyPresentationError
import com.kert0n.medapp.presentation.value.QuantityPresentationError
import com.kert0n.medapp.presentation.value.UnitPresentationDTO
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Упаковка в том виде, в каком её держит форма: строками, как человек напечатал (PLAN H1). Пока
 * он печатает, в количестве лежит «12,» или пусто, а в сроке — «03.20»; ни одно из этих
 * состояний величиной не является, и пачкой такое состояние не назовёшь.
 *
 * Обязательных полей четыре — аптечка, название, количество и единица (PLAN C1); остальные
 * поля есть **все до одного** (ТЗ 4.1.1.1), и пустое поле значит «не указано».
 *
 * Аптечка, единица и форма едут выбранными значениями, а не строками: их человек выбирает из
 * списка, и невыразимого состояния у выбора нет. Так же и даты покупки и вскрытия — их называет
 * календарь. Срок годности, наоборот, строка: его перепечатывают с упаковки как есть, вместе с
 * «03.2027», которое датой ещё не является.
 */
data class PackageFormPresentationDTO(
    val medKitId: Uuid? = null,
    val name: String = "",
    val amount: String = "",
    val unit: UnitPresentationDTO? = null,
    val form: FormPresentationDTO? = null,
    val category: String = "",
    val manufacturer: String = "",
    val country: String = "",
    val description: String = "",
    val expiresOn: String = "",
    val defaultIntakeAmount: String = "",
    val note: String = "",
    val price: String = "",
    val purchasedOn: LocalDate? = null,
    val openedOn: LocalDate? = null
)

/**
 * Почему форма упаковки не годится. Причина называет **поле**: форма длинная, и «где-то ошибка»
 * заставило бы человека искать её глазами. Текст по причине берёт экран из `R.string.*`.
 */
sealed interface PackageFormError {

    val field: Field

    /** Поля, которые бывают неверными. Экран подсвечивает названное и раскрывает его раздел. */
    enum class Field {
        MED_KIT, NAME, AMOUNT, UNIT,
        FORM, CATEGORY, MANUFACTURER, COUNTRY, DESCRIPTION, EXPIRY, HINT, NOTE, PRICE;

        /**
         * Обязательных полей четыре (PLAN C1); остальные лежат в раскрываемом разделе. Знание
         * это принадлежит полю, а не экрану: иначе список обязательного разъехался бы с формой.
         */
        val isRequired: Boolean get() = this == MED_KIT || this == NAME || this == AMOUNT || this == UNIT
    }

    /** Не выбрана аптечка: упаковка лежит в каком-то одном месте, и «нигде» её не бывает. */
    data object MedKitMissing : PackageFormError {
        override val field: Field get() = Field.MED_KIT
    }

    /** Не выбрана единица: без неё количество ничего не измеряет (PLAN D1). */
    data object UnitMissing : PackageFormError {
        override val field: Field get() = Field.UNIT
    }

    data object NameEmpty : PackageFormError {
        override val field: Field get() = Field.NAME
    }

    /** Текст длиннее, чем принимает его тип: предел берётся у типа, а не повторяется числом. */
    data class TooLong(override val field: Field) : PackageFormError

    data class Amount(val reason: QuantityPresentationError) : PackageFormError {
        override val field: Field get() = Field.AMOUNT
    }

    /** Заводимая пачка активна, а активная пустой не бывает ([com.kert0n.medapp.domain.pack.Package]). */
    data object AmountIsZero : PackageFormError {
        override val field: Field get() = Field.AMOUNT
    }

    data class Hint(val reason: QuantityPresentationError) : PackageFormError {
        override val field: Field get() = Field.HINT
    }

    /** Доза не бывает нулевой ([com.kert0n.medapp.domain.value.Dose]): нулевая — это не приём. */
    data object HintIsZero : PackageFormError {
        override val field: Field get() = Field.HINT
    }

    data class Expiry(val reason: ExpiryDatePresentationError) : PackageFormError {
        override val field: Field get() = Field.EXPIRY
    }

    data class Price(val reason: MoneyPresentationError) : PackageFormError {
        override val field: Field get() = Field.PRICE
    }

    /** Единицы или формы нет в снимке словаря: он старее, чем тот, кто её назвал (PLAN D1). */
    data class UnknownInVocabulary(override val field: Field) : PackageFormError
}
