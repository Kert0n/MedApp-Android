package com.kert0n.medapp.core.format

import com.kert0n.medapp.domain.model.Money
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор цены: грамматику обещает адаптер, поэтому он же её и проверяет. */
class MoneyInputTest {

    private val yen: Currency = Currency.getInstance("JPY")

    private fun errorOf(input: String): MoneyInputError {
        val error = MoneyInput.parse(input).exceptionOrNull()
        assertTrue("ожидался отказ на «$input»", error is MoneyInputException)
        return (error as MoneyInputException).error
    }

    @Test
    fun bothSeparatorsAreAccepted() {
        assertEquals(Money(BigDecimal("1.50")), MoneyInput.parse("1,50").getOrThrow())
        assertEquals(Money(BigDecimal("1.50")), MoneyInput.parse("1.50").getOrThrow())
    }

    @Test
    fun exponentIsRejectedInBothCases() {
        // `DecimalFormat` читает экспоненту независимо от шаблона, и разделитель по умолчанию
        // заглавный: «1E3» разбирался в тысячу, пока грамматику не проверял адаптер.
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("1E3"))
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("1e3"))
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("1E-2"))
    }

    @Test
    fun signIsRejectedEvenWhenItChangesNothing() {
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("-0"))
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("-1"))
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("+1"))
    }

    @Test
    fun groupedAndEmptyInputIsRejected() {
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("1 200"))
        assertEquals(MoneyInputError.NOT_A_DECIMAL, errorOf("сто"))
        assertEquals(MoneyInputError.EMPTY, errorOf("   "))
        assertEquals(MoneyInputError.TOO_LONG, errorOf("1".repeat(MONEY_MAX_INPUT_LENGTH + 1)))
    }

    @Test
    fun currencyRangeIsReportedSeparatelyFromGrammar() {
        assertEquals(MoneyInputError.OUT_OF_CURRENCY_RANGE, errorOf("1.005"))
        val fractionalYen = MoneyInput.parse("1,50", yen).exceptionOrNull()
        assertEquals(
            MoneyInputError.OUT_OF_CURRENCY_RANGE,
            (fractionalYen as MoneyInputException).error
        )
    }

    @Test
    fun parseIsCorrectUnderConcurrentUse() {
        // `DecimalFormat` изменяем: на общем экземпляре восемь потоков превращали «7890.12» в
        // «778899001122» и бросали исключения мимо Result. Свой экземпляр на вызов это снимает.
        val expected = Money(BigDecimal("7890.12"))
        val pool = Executors.newFixedThreadPool(THREADS)
        val tasks = List(THREADS) {
            Callable {
                var wrong = 0
                var escaped = 0
                repeat(REPEATS) {
                    try {
                        if (MoneyInput.parse("7890.12").getOrNull() != expected) wrong++
                    } catch (cause: Throwable) {
                        escaped++
                    }
                }
                wrong to escaped
            }
        }
        val results = pool.invokeAll(tasks).map { it.get() }
        pool.shutdown()
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS))
        assertEquals("неверных разборов", 0, results.sumOf { it.first })
        assertEquals("исключений мимо Result", 0, results.sumOf { it.second })
    }

    private companion object {
        const val THREADS = 8
        const val REPEATS = 200
    }
}
