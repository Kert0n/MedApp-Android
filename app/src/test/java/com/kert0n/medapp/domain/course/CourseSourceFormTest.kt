package com.kert0n.medapp.domain.course

import com.kert0n.medapp.fixture.CAPSULE_FORM
import com.kert0n.medapp.fixture.LATER
import com.kert0n.medapp.fixture.MILLILITRES
import com.kert0n.medapp.fixture.OTHER_PACK
import com.kert0n.medapp.fixture.PACK
import com.kert0n.medapp.fixture.TABLETS
import com.kert0n.medapp.fixture.TABLET_FORM
import com.kert0n.medapp.fixture.activeCourse
import com.kert0n.medapp.fixture.course
import com.kert0n.medapp.fixture.doses
import com.kert0n.medapp.fixture.millilitres
import com.kert0n.medapp.fixture.pack
import com.kert0n.medapp.fixture.source
import com.kert0n.medapp.fixture.tablets
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Форму и единицу курса задаёт первый источник, дальше несовместимое отвергается (PLAN D5).
 *
 * Проверяется и обратное правило: отвязка последнего источника у действующего курса форму не
 * сбрасывает, у черновика сбрасывает — там ещё нечего терять.
 */
class CourseSourceFormTest {

    private val tabletPack = pack(id = PACK, formId = TABLET_FORM, quantity = tablets("20"))

    private fun draft() = course(doseAmount = BigDecimal("2"))

    @Test
    fun firstSourceFixesFormAndUnit() {
        val fixed = draft().attach(tabletPack, doses = doses(5), at = LATER).getOrThrow()
        assertEquals(TABLET_FORM, fixed.formId)
        assertEquals(TABLETS, fixed.unitId)
        // Доза собралась только теперь: единицу принесла пачка, число задал человек.
        assertEquals(tablets("2"), fixed.dose)
    }

    @Test
    fun packageWithoutAFormIsAttachedToNothing() {
        // «Форма неизвестна» и «форма неизвестна» — две разные неизвестности, и совместимыми они
        // не бывают. Экран так и говорит: «укажите форму, чтобы подключить к курсу».
        val unknownForm = pack(id = OTHER_PACK, formId = null)
        assertEquals(
            CourseRejected.Reason.FORM_UNKNOWN,
            draft().attach(unknownForm, doses = doses(1), at = LATER).rejection()
        )
        // И для первого источника тоже: фиксировать «неизвестно» нечем.
        assertNull(draft().formId)
    }

    @Test
    fun incompatibleFormIsRejected() {
        val capsules = pack(id = OTHER_PACK, formId = CAPSULE_FORM, quantity = tablets("10"))
        val fixed = draft().attach(tabletPack, doses = doses(5), at = LATER).getOrThrow()
        assertEquals(
            CourseRejected.Reason.FORM_MISMATCH,
            fixed.attach(capsules, doses = doses(1), at = LATER).rejection()
        )
    }

    @Test
    fun incompatibleUnitIsRejected() {
        // Форма та же, единица другая: доза курса измеряется единицей курса, и миллилитры в
        // «две таблетки» не подставятся.
        val syrup = pack(id = OTHER_PACK, formId = TABLET_FORM, quantity = millilitres("100"))
        val fixed = draft().attach(tabletPack, doses = doses(5), at = LATER).getOrThrow()
        assertEquals(
            CourseRejected.Reason.UNIT_MISMATCH,
            fixed.attach(syrup, doses = doses(1), at = LATER).rejection()
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
        assertEquals(tablets("2"), unsupplied.dose)
        assertEquals(listOf<CourseSource>(), unsupplied.sources)
    }

    @Test
    fun detachingTheLastSourceOfADraftForgetsFormAndUnit() {
        val chosen = draft().attach(tabletPack, doses = doses(5), at = LATER).getOrThrow()
        val emptied = chosen.detach(tabletPack, LATER)
        assertNull(emptied.formId)
        assertNull(emptied.unitId)
        assertNull(emptied.dose)
        // Число дозы человек уже назвал, и терять его незачем — неизвестна снова только единица.
        assertEquals(BigDecimal("2"), emptied.doseAmount)
    }

    @Test
    fun draftForgetsFormOnlyWhenTheStackEmpties() {
        val second = pack(id = OTHER_PACK, formId = TABLET_FORM, quantity = tablets("12"))
        val two = draft()
            .attach(tabletPack, doses = doses(5), at = LATER).getOrThrow()
            .attach(second, doses = doses(4), at = LATER).getOrThrow()
        val one = two.detach(tabletPack, LATER)
        assertEquals(TABLET_FORM, one.formId)
        assertEquals(TABLETS, one.unitId)
    }

    @Test
    fun forgottenFormLetsTheDraftStartOverWithAnotherForm() {
        val capsules = pack(id = OTHER_PACK, formId = CAPSULE_FORM, quantity = millilitres("10"))
        val restarted = draft()
            .attach(tabletPack, doses = doses(5), at = LATER).getOrThrow()
            .detach(tabletPack, LATER)
            .attach(capsules, doses = doses(1), at = LATER).getOrThrow()
        assertEquals(CAPSULE_FORM, restarted.formId)
        assertEquals(MILLILITRES, restarted.unitId)
    }

    @Test
    fun unsuppliedActiveCourseStillDemandsItsOwnFormBack() {
        // Форма осталась, поэтому подключить пачку другой формы к нему по-прежнему нельзя.
        val capsules = pack(id = OTHER_PACK, formId = CAPSULE_FORM, quantity = tablets("10"))
        val unsupplied = activeCourse(sources = listOf(source(PACK, 5))).detach(tabletPack, LATER)
        assertEquals(
            CourseRejected.Reason.FORM_MISMATCH,
            unsupplied.attach(capsules, doses = doses(1), at = LATER).rejection()
        )
    }

    private fun <T> Result<T>.rejection(): CourseRejected.Reason? =
        (exceptionOrNull() as? CourseRejected)?.reason
}
