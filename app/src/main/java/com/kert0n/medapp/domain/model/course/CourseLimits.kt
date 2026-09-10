package com.kert0n.medapp.domain.model.course

/** Границы полей курса. Курс на сервер не уезжает вовсе (PLAN C0), длины задаёт продукт. */
const val COURSE_TITLE_MAX_LENGTH = 200

/** Заметка длиннее названия: сюда переписывают запись от врача и «что купить» (PLAN D5). */
const val COURSE_NOTE_MAX_LENGTH = 500
