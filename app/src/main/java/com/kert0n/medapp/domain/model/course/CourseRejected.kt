package com.kert0n.medapp.domain.model.course

/**
 * Отказ курса как значение внутри `Result`.
 *
 * Подключение источника и активация возвращают `Result` (PLAN D5): отказ здесь — обычный ответ
 * модели на действие человека, а не сбой программы. Причина остаётся [reason], перечислением, и
 * текста для человека в исключении нет: сообщение экран берёт из `R.string.*`.
 */
class CourseRejected(val reason: CourseRejection) : IllegalStateException(reason.name)
