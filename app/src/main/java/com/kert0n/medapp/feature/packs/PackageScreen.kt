package com.kert0n.medapp.feature.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kert0n.medapp.R
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.value.QuantityPresentationDTO
import com.kert0n.medapp.presentation.value.forHuman
import com.kert0n.medapp.presentation.value.toPresentationDTO
import com.kert0n.medapp.ui.EmptyState
import com.kert0n.medapp.ui.LoadingState
import com.kert0n.medapp.ui.theme.accents
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")

/**
 * Карточка упаковки (PLAN H3 №6). Сверху — то, ради чего её открывают: сколько есть. Ниже
 * карточками по смыслу: что это, сроки и цена, где лежит. Незаполненного не показывается вовсе —
 * пустая строка «производитель: —» занимает место и ничего не сообщает.
 *
 * «Доступно мне» и «свободно любому» показываются, только когда отличаются от остатка: три
 * одинаковых числа подряд человек читает как ошибку (PLAN D4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageScreen(
    packageId: Uuid,
    onBack: () -> Unit,
    onEdit: (Uuid) -> Unit,
    onChangeAmount: () -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PackageViewModel = hiltViewModel()
) {
    LaunchedEffect(packageId) { viewModel.open(packageId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pkg = state.pkg
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(pkg?.name ?: stringResource(R.string.pack_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.gone -> EmptyState(
                text = stringResource(R.string.pack_gone),
                modifier = Modifier.padding(padding)
            )
            pkg == null -> LoadingState(Modifier.padding(padding))
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HowMuchIsThere(pkg)
                WhatItIs(pkg)
                DatesAndPrice(pkg, state.today)
                WhereItLies(state.medKit?.name, pkg)
                Actions(
                    onEdit = { onEdit(pkg.medKitId) },
                    onChangeAmount = onChangeAmount,
                    onTransfer = onTransfer
                )
            }
        }
    }
}

/**
 * Сколько есть. Крупно — оценка остатка: то, из чего человек исходит, собираясь принять. Чужие
 * брони янтарным: они не беда и не просрочка, просто это лекарство заявлено не мной (PLAN D4).
 */
@Composable
private fun HowMuchIsThere(pkg: PackagePresentationDTO) {
    Section(stringResource(R.string.pack_how_much)) {
        Text(pkg.effective.text(), style = MaterialTheme.typography.headlineMedium)
        if (pkg.availableToMe != pkg.effective) {
            Fact(stringResource(R.string.pack_available_to_me), pkg.availableToMe.text())
        }
        if (pkg.freeForAnyone != pkg.availableToMe) {
            Fact(stringResource(R.string.pack_free_for_anyone), pkg.freeForAnyone.text())
        }
        if (pkg.hasReservedByOthers) {
            Marker(
                icon = R.drawable.ic_warning,
                text = stringResource(R.string.pack_reserved_by_others, pkg.reservedByOthers.text()),
                description = stringResource(R.string.pack_reserved_description),
                color = MaterialTheme.accents.reserved
            )
        }
        if (pkg.hasUnconfirmedChanges) {
            Text(
                stringResource(R.string.pack_unconfirmed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Что это за лекарство. Карточка не показывается вовсе, когда сказать о нём нечего. */
@Composable
private fun WhatItIs(pkg: PackagePresentationDTO) {
    val known = listOfNotNull(
        pkg.form?.let { stringResource(R.string.pack_form) to it.name },
        pkg.category?.let { stringResource(R.string.pack_category) to it },
        pkg.manufacturer?.let { stringResource(R.string.pack_manufacturer) to it },
        pkg.country?.let { stringResource(R.string.pack_country) to it },
        pkg.description?.let { stringResource(R.string.pack_description) to it }
    )
    if (known.isEmpty()) return
    Section(stringResource(R.string.pack_what_it_is)) {
        for ((label, value) in known) Fact(label, value)
    }
}

/** Сроки и цена. Просрочка — красным, значком и словом: цветом одним нельзя (PLAN H3). */
@Composable
private fun DatesAndPrice(pkg: PackagePresentationDTO, today: LocalDate) {
    val expired = pkg.expiresOn?.isExpiredOn(today) == true
    Section(stringResource(R.string.pack_dates)) {
        if (pkg.expiresOn == null) {
            Fact(stringResource(R.string.pack_expires_on), stringResource(R.string.pack_expiry_unknown))
        } else if (expired) {
            Marker(
                icon = R.drawable.ic_expired,
                text = stringResource(
                    R.string.pack_expired_on,
                    pkg.expiresOn.toPresentationDTO().text
                ),
                description = stringResource(R.string.med_kit_expired_description),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Fact(
                stringResource(R.string.pack_expires_on),
                stringResource(R.string.pack_expires_until, pkg.expiresOn.toPresentationDTO().text)
            )
        }
        pkg.defaultIntakeAmount?.let {
            Fact(stringResource(R.string.pack_intake_hint), it.text())
        }
        pkg.purchasedOn?.let { Fact(stringResource(R.string.pack_purchased_on), it.format(DAY)) }
        pkg.openedOn?.let { Fact(stringResource(R.string.pack_opened_on), it.format(DAY)) }
        pkg.price?.let { Fact(stringResource(R.string.pack_price), it.forHuman()) }
    }
}

/** Где лежит: аптечка и заметка человека — то, что помогает найти коробку руками. */
@Composable
private fun WhereItLies(medKitName: String?, pkg: PackagePresentationDTO) {
    Section(stringResource(R.string.pack_where)) {
        Fact(stringResource(R.string.pack_med_kit), medKitName ?: stringResource(R.string.pack_med_kit_unknown))
        pkg.note?.let { Fact(stringResource(R.string.pack_note), it) }
    }
}

/**
 * Что с упаковкой можно сделать. Правка меняет описание и только его: количество двигают
 * пересчёт и утилизация, место — перенос, и у каждого из них свой след в истории (PLAN D7).
 */
@Composable
private fun Actions(onEdit: () -> Unit, onChangeAmount: () -> Unit, onTransfer: () -> Unit) {
    Section(stringResource(R.string.pack_actions)) {
        Action(R.drawable.ic_calculate, R.string.pack_action_recount, onChangeAmount)
        Action(R.drawable.ic_move_down, R.string.pack_action_transfer, onTransfer)
        Action(R.drawable.ic_edit, R.string.pack_action_edit, onEdit)
    }
}

@Composable
private fun Action(icon: Int, text: Int, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Text(stringResource(text), modifier = Modifier.padding(start = 8.dp).weight(1f))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

/** Сведение: чему оно относится и что это. Подпись сверху — так её читает и экранный чтец. */
@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Значок со словами: цвет уточняет то, что уже сказано, а не заменяет сказанное (PLAN H3). */
@Composable
private fun Marker(icon: Int, text: String, description: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Количество словами: число и единица, как их показывают везде в приложении. */
@Composable
private fun QuantityPresentationDTO.text(): String =
    stringResource(R.string.pack_left, amount, unit.name)
