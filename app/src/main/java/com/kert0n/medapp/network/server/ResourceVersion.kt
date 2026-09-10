package com.kert0n.medapp.network.server

import kotlinx.serialization.Serializable

/**
 * Версия ресурса, на которой действует команда: она же предусловие запроса (PLAN B3).
 *
 * Величина, а не `Long`, по тому же доводу, что и редакция курса: «не бывает отрицательной»
 * стоит один раз, и подставить версию броней вместо версии пачки становится видно по имени
 * поля, а не по случайности. На проводе это просто число: отрицательное не разбирается.
 */
@Serializable
@JvmInline
value class ResourceVersion(val number: Long) : Comparable<ResourceVersion> {

    init {
        require(number >= 0) { "версия ресурса не бывает отрицательной: $number" }
    }

    override fun compareTo(other: ResourceVersion): Int = number.compareTo(other.number)

    override fun toString(): String = "версия $number"
}
