package com.kert0n.medapp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Границы слоёв держатся проверкой, а не вниманием. Раньше их стерегли грепы AGENTS, и это
 * работало ровно до тех пор, пока кто-нибудь не забывал их запустить: сценарий успел сходить
 * напрямую в сеть, состояние экрана — в представление, и оба раза нарушение доехало до ревью.
 *
 * Правило одно: **домен — центр всего**. Действие продукта называет домен, а выполняет тот корень,
 * который умеет; зависимости идут внутрь, к домену, и никогда наружу.
 *
 * Здесь проверяется то, что действительно нельзя, а не то, что сейчас случайно не встречается:
 * список разрешённого у каждого корня — это его договор, и расширять его нужно осознанно.
 */
class LayerBoundariesTest {

    /**
     * Кому что позволено видеть. Пусто — не видит никого, кроме себя, `java` и `kotlin`.
     *
     * `di` отсутствует в списках намеренно: это проводка, она собирает граф и по построению видит
     * всех. Зато её самой не видит никто, кроме тех, кому нужны её определители (`@MedAppHttp`,
     * `@IoDispatcher`).
     */
    private val maySee: Map<String, Set<String>> = mapOf(
        // Домен не знает ни Android, ни Room, ни Ktor, ни остальных корней.
        "domain" to emptySet(),
        // Представление строит состояние экрана из доменных величин — и только.
        "presentation" to setOf("domain"),
        // Составляющие экрана рисуют доменные значения и готовые DTO представления.
        "ui" to setOf("domain", "presentation"),
        // Сеть говорит с сервером на языке домена; про очередь и хранение она не знает.
        "network" to setOf("domain", "di"),
        // Очередь видит сеть и домен, но не Room (AGENTS).
        "queue" to setOf("domain", "network", "di"),
        // Хранение реализует порты очереди и сети; выше себя не смотрит.
        "storage" to setOf("domain", "network", "queue", "di"),
        // Платформа выполняет порты сети (ключ, хранилище учётных данных).
        "platform" to setOf("domain", "network", "di"),
        // Сценарий стоит над логиками — но в сеть не ходит: сетевое действие называет домен портом.
        "feature" to setOf("domain", "presentation", "ui", "queue", "storage"),
        // Приложение собирает всё вместе.
        "app" to setOf("domain", "presentation", "ui", "feature", "queue", "storage", "di")
    )

    private val sources: File = listOf(
        File("src/main/java/com/kert0n/medapp"),
        File("app/src/main/java/com/kert0n/medapp")
    ).firstOrNull { it.isDirectory } ?: error("исходники не найдены: проверка прошла бы впустую")

    private val roots: Set<String> = maySee.keys

    /** Проверка, которая не должна пройти впустую: дерево на месте и оно не пустое. */
    @Test
    fun theSourceTreeIsWhereWeThinkItIs() {
        val files = sources.walkTopDown().filter { it.extension == "kt" }.count()

        assertTrue("файлов найдено $files — дерево не то", files > 200)
        for (root in roots) {
            assertTrue("корня $root нет", File(sources, root).isDirectory)
        }
    }

    /**
     * Зависимости идут внутрь. Нарушение называется файлом и импортом, чтобы его не пришлось
     * искать: «что-то где-то импортирует лишнее» — не отчёт.
     *
     * Красная проверка: позволить `feature` видеть `network` — падают ровно те файлы, которые
     * пошли бы в сеть мимо доменного порта.
     */
    @Test
    fun dependenciesPointInwards() {
        val broken = mutableListOf<String>()
        for ((root, allowed) in maySee) {
            for (file in File(sources, root).walkTopDown().filter { it.extension == "kt" }) {
                for (import in file.importedRoots()) {
                    if (import != root && import !in allowed) {
                        broken += "${file.relativeTo(sources)}: $root → $import"
                    }
                }
            }
        }

        assertEquals(emptyList<String>(), broken.distinct().sorted())
    }

    /**
     * Каталог называет понятие, а не вид файла (PLAN H1): `domain/pack/`, а не `domain/model/`.
     * Что это DTO, маппер или строка таблицы, видно по имени типа, а слой — по корню.
     */
    @Test
    fun directoriesNameConceptsNotMechanisms() {
        val mechanisms = setOf(
            "model", "calc", "sync", "mapper", "dto", "remote", "local", "entity", "dao",
            "repository", "util", "utils", "helper", "helpers", "common", "core", "misc"
        )
        val named = sources.walkTopDown()
            .filter { it.isDirectory && it.name in mechanisms }
            .map { it.relativeTo(sources).path }
            .toList()

        assertEquals(emptyList<String>(), named)
    }

    /** Корни, которые называет файл: `import com.kert0n.medapp.<корень>.…`. */
    private fun File.importedRoots(): List<String> = readLines()
        .mapNotNull { line -> IMPORT.find(line)?.groupValues?.get(1) }
        .filter { it in roots || it == "di" }

    private companion object {
        val IMPORT = Regex("""^import com\.kert0n\.medapp\.([a-z]+)\.""")
    }
}
