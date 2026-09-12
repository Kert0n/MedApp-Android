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
 * значок сам `Icon` по `LocalContentColor`, поэтому второго набора цветов для тёмной темы нет.
 *
 * Значка два: у выбранного места он залит — так Material отличает, где человек стоит, не одним
 * лишь цветом. Залитого варианта может и не быть: QR-значки — геометрический узор, и заливать в
 * них нечего, поэтому [iconSelected] пуст, а не повторяет обычный файлом-двойником. Выгружены из
 * Material Symbols (`scripts/add-icon.mjs`).
 *
 * Порядок объявления — порядок на экране.
 */
enum class Destination(
    val route: Route,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val iconSelected: Int? = null
) {
    MED_KITS(
        Route.MedKits, R.string.tab_med_kits,
        R.drawable.ic_tab_med_kits, R.drawable.ic_tab_med_kits_filled
    ),
    PLAN(
        Route.Plan, R.string.tab_plan,
        R.drawable.ic_tab_plan, R.drawable.ic_tab_plan_filled
    ),
    // У `qr_code_scanner` залитого варианта в наборе нет: он совпадает с обычным.
    SCANNER(Route.Scanner, R.string.tab_scanner, R.drawable.ic_tab_scanner),
    ANALYTICS(
        Route.Analytics, R.string.tab_analytics,
        R.drawable.ic_tab_analytics, R.drawable.ic_tab_analytics_filled
    ),
    SETTINGS(
        Route.Settings, R.string.tab_settings,
        R.drawable.ic_tab_settings, R.drawable.ic_tab_settings_filled
    );

    /** Где человек стоит, видно и формой значка, а не только цветом (PLAN H3). */
    @DrawableRes
    fun icon(selected: Boolean): Int = if (selected) iconSelected ?: icon else icon
}
