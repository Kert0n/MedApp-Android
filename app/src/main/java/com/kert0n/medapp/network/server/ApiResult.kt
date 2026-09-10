package com.kert0n.medapp.network.server

/** Исход операции MedApp: объявленный ею успех или отказ, из которого следует решение. */
sealed interface ApiResult<out T> {

    data class Success<out T>(val value: T) : ApiResult<T>

    data class Failure(val failure: ApiFailure) : ApiResult<Nothing>
}
