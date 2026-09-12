package com.kert0n.medapp.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.kert0n.medapp.R

/**
 * Пять мест нижней навигации (PLAN H3). Набор закрыт и известен целиком, поэтому перечисление:
 * шестое место — это изменение продукта, а не данных.
 *
 * Значок и подпись — ссылки на ресурсы, а не готовые `ImageVector`: перечисление остаётся
 * обычным значением, которое можно построить где угодно, а не только внутри композиции. Красит
 * значок сам `Icon` по `LocalContentColor`, поэтому тёмная тема и выбранная вкладка получаются
 * без второго набора цветов.
 *
 * Порядок объявления — порядок на экране.
 */
enum class Destination(
    val route: Route,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int
) {
    MED_KITS(Route.MedKits, R.string.tab_med_kits, R.drawable.ic_tab_med_kits),
    PLAN(Route.Plan, R.string.tab_plan, R.drawable.ic_tab_plan),
    SCANNER(Route.Scanner, R.string.tab_scanner, R.drawable.ic_tab_scanner),
    ANALYTICS(Route.Analytics, R.string.tab_analytics, R.drawable.ic_tab_analytics),
    SETTINGS(Route.Settings, R.string.tab_settings, R.drawable.ic_tab_settings)
}
