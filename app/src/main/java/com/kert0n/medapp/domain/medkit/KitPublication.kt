package com.kert0n.medapp.domain.medkit

enum class KitPublication {
    LOCAL,        // на сервере не существует
    PUBLISHING,   // группа операций публикации ещё не завершена
    PUBLISHED     // существует на сервере
}
