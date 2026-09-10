package com.kert0n.medapp.domain.model.sync

import kotlin.uuid.Uuid

/**
 * Снять свою бронь с упаковки: источник исчерпан, курс завершён или отменён (PLAN D5).
 *
 * Если пачка уже уничтожена, каскад снял бронь до нас, и зависимое снятие закрывается по
 * проверенному отсутствию, а не считается неудачей (PLAN E2).
 */
data class ReleaseClaimIntent(val packageId: Uuid) : SyncIntent
