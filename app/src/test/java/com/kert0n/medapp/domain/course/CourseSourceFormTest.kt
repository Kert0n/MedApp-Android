package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.CAPSULE_FORM
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.dose
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import com.kert0n.medapp.domain.value.doses
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Форму и единицу курса задаёт первый источник, дальше несовместимое отвергается (PLAN D5).
 *
 * Заданы они один раз и навсегда: отвязка последнего источника их не сбрасывает ни у
 * действующего курса, ни у черновика. Иначе названная человеком доза досталась бы препарату
 * другой единицы и была бы перечитана молча.
 */
class CourseSourceFormTest {

    private val tabletPack = pack(id = PACK, formId = TABLET_FORM, quantity = tablets("20"))

    private fun draft() = course(doseAmount = BigDecimal("2"))

    @Test
    fun firstSourceFixesFormAndUnit() {
        val fixed = draft().attach(tabletPack, doses = 5.doses, at = LATER).getOrThrow()
        assertEquals(TABLET_FORM, fixed.formId)
        assertEquals(TABLETS, fixed.unitId)
        // Доза собралась только теперь: единицу принесла пачка, число задал человек.
        assertEquals(dose("2"), fixed.dose)
    }

    @Test
    fun packageWithoutAFormIsAttachedToNothing() {
        // «Форма неизвестна» и «форма неизвестна» — две разные неизвестности, и совместимыми они
        // не бывают. Экран так и говорит: «укажите форму, чтобы подключить к курсу».
        val unknownForm = pack(id = OTHER_PACK, formId = null)
        assertEquals(
            CourseRejected.Reason.FORM_UNKNOWN,
            draft().attach(unknownForm, doses = 1.doses, at = LATER).rejection()
        )
        // И для первого источника тоже: фиксировать «неизвестно» нечем.
        assertNull(draft().formId)
    }

    @Test
    fun incompatibleFormIsRejected() {
        val capsules = pack(id = OTHER_PACK, formId = CAPSULE_FORM, quantity = tablets("10"))
        val fixed = draft().attach(tabletPack, doses = 5.doses, at = LATER).getOrThrow()
        assertEquals(
            CourseRejected.Reason.FORM_MISMATCH,
            fixed.attach(capsules, doses = 1.doses, at = LATER).rejection()
        )
    }

    @Test
    fun incompatibleUnitIsRejected() {
        // Форма та же, единица другая: доза курса измеряется единицей курса, и миллилитры в
        // «две таблетки» не подставятся.
        val syrup = pack(id = OTHER_PACK, formId = TABLET_FORM, quantity = millilitres("100"))
        val fixed = draft().attach(tabletPack, doses = 5.doses, at = LATER).getOrThrow()
        assertEquals(
            CourseRejected.Reason.UNIT_MISMATCH,
            fixed.attach(syrup, doses = 1.doses, at = LATER).rejection()
        )
        assertEquals(TABLETS, fixed.unitId)
    }

    @Test
    fun detachingTheLastSourceOfAnActiveCourseKeepsFormAndUnit() {
        // Иначе доза и расписание мгновенно потеряли бы смысл, а состоявшиеся приёмы остались бы
        // с единицей, которой у курса больше нет. Курс просто становится необеспеченным.
        val active = activeCourse(sources = listOf(source(PACK, 5)))
        val unsupplied = active.detach(tabletPack, LATER)
        assertEquals(emptyList<CourseSource>(), unsupplied.sources)
        assertEquals(TABLET_FORM, unsupplied.formId)
        assertEquals(TABLETS, unsupplied.unitId)
        assertEquals(dose("2"), unsupplied.dose)
        assertEquals(listOf<CourseSource>(), unsupplied.sources)
    }

    @Test
    fun detachingTheLastSourceOfADraftKeepsFormAndUnit() {
        val chosen = draft().attach(tabletPack, doses = 5.doses, at = LATER).getOrThrow()
        val emptied = chosen.detach(tabletPack, LATER)
        assertEquals(TABLET_FORM, emptied.formId)
        assertEquals(TABLETS, emptied.unitId)
        // Доза остаётся дозой в таблетках: и число, и единица, которой его назвали.
        assertEquals(BigDecimal("2"), emptied.doseAmount)
        assertEquals(dose("2"), emptied.dose)
    }

    @Test
    fun draftKeepsFormWhileOtherSourcesRemain() {
        val second = pack(id = OTHER_PACK, formId = TABLET_FORM, quantity = tablets("12"))
        val two = draft()
            .attach(tabletPack, doses = 5.doses, at = LATER).getOrThrow()
            .attach(second, doses = 4.doses, at = LATER).getOrThrow()
        val one = two.detach(tabletPack, LATER)
        assertEquals(TABLET_FORM, one.formId)
        assertEquals(TABLETS, one.unitId)
    }

    /**
     * Названное для таблеток число не достаётся миллилитрам. Пустой черновик по-прежнему
     * лечение теми же таблетками; другой препарат — другое лечение и другой черновик.
     */
    @Test
    fun emptiedDraftDoesNotLetAnotherFormInheritTheDose() {
        val capsules = pack(id = OTHER_PACK, formId = CAPSULE_FORM, quantity = millilitres("10"))
        val emptied = draft()
            .attach(tabletPack, doses = 5.doses, at = LATER).getOrThrow()
            .detach(tabletPack, LATER)

        assertEquals(
            CourseRejected.Reason.FORM_MISMATCH,
            emptied.attach(capsules, doses = 1.doses, at = LATER).rejection()
        )
        val sameFormOtherUnit =
            pack(id = OTHER_PACK, formId = TABLET_FORM, quantity = millilitres("10"))
        assertEquals(
            CourseRejected.Reason.UNIT_MISMATCH,
            emptied.attach(sameFormOtherUnit, doses = 1.doses, at = LATER).rejection()
        )
        assertEquals(dose("2"), emptied.dose)
    }

    @Test
    fun unsuppliedActiveCourseStillDemandsItsOwnFormBack() {
        // Форма осталась, поэтому подключить пачку другой формы к нему по-прежнему нельзя.
        val capsules = pack(id = OTHER_PACK, formId = CAPSULE_FORM, quantity = tablets("10"))
        val unsupplied = activeCourse(sources = listOf(source(PACK, 5))).detach(tabletPack, LATER)
        assertEquals(
            CourseRejected.Reason.FORM_MISMATCH,
            unsupplied.attach(capsules, doses = 1.doses, at = LATER).rejection()
        )
    }

    private fun <T> Result<T>.rejection(): CourseRejected.Reason? =
        (exceptionOrNull() as? CourseRejected)?.reason
}
