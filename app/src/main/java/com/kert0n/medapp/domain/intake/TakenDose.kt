package com.kert0n.medapp.domain.intake

import com.kert0n.medapp.domain.pack.PackageRef
import com.kert0n.medapp.domain.value.Dose
import java.time.Instant

/**
 * Состоявшийся приём: сколько, из какой пачки и когда — три поля, которые бывают только вместе.
 *
 * Обстоятельства события — [amount] с единицей, в которой считали тогда, и [at], который
 * называет человек, — записаны в факте и не перечитываются. [pkg] — ссылка на сегодняшнее: имя,
 * аптечка и нынешняя единица пачки читаются по ней и потому текущие (PLAN D6). Факт против
 * сегодняшней пачки не проверяется: он был допустим в момент записи, а проверяет это акт —
 * [Package.take]. Пачка может отличаться от плановой, и расход относится к фактической (D5).
 */
data class TakenDose(
    val pkg: PackageRef,
    val amount: Dose,
    val at: Instant
)
