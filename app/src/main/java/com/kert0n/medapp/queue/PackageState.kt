package com.kert0n.medapp.queue

import com.kert0n.medapp.network.pack.PackageSnapshotNetworkDTO

/**
 * Что известно о пачке на сервере после закрытия операции. «Пачки нет» выражено типом, а не
 * пустым снимком, и допустимо только там, где команда его ждала ([Expected.SNAPSHOT_OR_GONE],
 * [Expected.NOTHING] у удаления); у команды аптечки состояния пачки нет вовсе.
 */
sealed interface PackageState {

    data class Present(val snapshot: PackageSnapshotNetworkDTO) : PackageState

    /** Пачки на сервере больше нет: истина — ноль, локально она архивируется. */
    data object Gone : PackageState

    /** Команда пачки не касается. */
    data object None : PackageState
}
