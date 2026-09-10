package com.kert0n.medapp.domain.model.pack

/** Границы полей упаковки. Серверные длины и локальные объявлены рядом: их проверяют вместе. */
const val PACKAGE_NAME_MAX_LENGTH = 300
const val PACKAGE_CATEGORY_MAX_LENGTH = 200
const val PACKAGE_MANUFACTURER_MAX_LENGTH = 300
const val PACKAGE_COUNTRY_MAX_LENGTH = 100
const val PACKAGE_DESCRIPTION_MAX_LENGTH = 4000
const val PACKAGE_NOTE_MAX_LENGTH = 200

/**
 * За сколько дней до конца срока пачка считается «истекающей скоро».
 *
 * Три дня — верхний из порогов напоминаний D8 (за 3 и за 1 день). Пороги там относятся к
 * источникам с выделением, а этот признак отвечает на вопрос строки списка «пора обратить
 * внимание», поэтому он один и берётся больший.
 */
const val PACKAGE_EXPIRES_SOON_DAYS = 3L
