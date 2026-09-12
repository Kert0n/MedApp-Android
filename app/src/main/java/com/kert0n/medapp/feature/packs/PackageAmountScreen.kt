package com.kert0n.medapp.feature.packs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kert0n.medapp.R
import com.kert0n.medapp.domain.stock.StockMovement
import com.kert0n.medapp.presentation.pack.PackageAmountError
import com.kert0n.medapp.presentation.value.QuantityPresentationError
import com.kert0n.medapp.ui.LoadingState
import com.kert0n.medapp.ui.PickerField
import kotlin.uuid.Uuid

/**
 * Пересчёт и утилизация (PLAN H3 №9). Человек приходит сюда с одним наблюдением — «в коробке не
 * столько, сколько записано», — а объясняет его по-разному: пересчитал или выбросил. Поэтому две
 * вкладки, а не два экрана.
 *
 * Пересчёт называет **новое количество целиком**, а не разницу: человек считает то, что видит, а
 * вычитание за него делает учёт (PLAN E1).
 *
 * Уходящее в ноль спрашивается: пачка уйдёт в архив, и это решение человека. История при этом
 * остаётся — видно, сколько было и куда делось (PLAN D7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageAmountScreen(
    packageId: Uuid,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PackageAmountViewModel = hiltViewModel()
) {
    LaunchedEffect(packageId) { viewModel.open(packageId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.gone) { if (state.gone) onDone() }
    val pkg = state.pkg
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.amount_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (pkg == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                for (tab in PackageAmountViewModel.Tab.entries) {
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.pick(tab) },
                        text = { Text(stringResource(tab.title)) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(
                        R.string.amount_now,
                        pkg.quantity.amount,
                        pkg.quantity.unit.name
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = state.form.amount,
                    onValueChange = { viewModel.edit(state.form.copy(amount = it)) },
                    label = { Text(stringResource(state.tab.amountLabel)) },
                    supportingText = { Text(stringResource(state.tab.explained)) },
                    isError = state.error != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.tab == PackageAmountViewModel.Tab.DISPOSAL) {
                    // Названия причин берутся до списка: выбор из готового списка — не место для
                    // чтения ресурсов по одному.
                    val reasons = StockMovement.Disposal.Reason.entries
                    val named = reasons.associateWith { stringResource(it.text) }
                    PickerField(
                        label = stringResource(R.string.amount_reason),
                        selected = state.form.reason,
                        options = reasons,
                        optionText = { named.getValue(it) },
                        onPick = { viewModel.edit(state.form.copy(reason = it)) }
                    )
                }
                OutlinedTextField(
                    value = state.form.note,
                    onValueChange = { viewModel.edit(state.form.copy(note = it)) },
                    label = { Text(stringResource(R.string.amount_note)) },
                    isError = state.error == PackageAmountError.NoteTooLong,
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { reason ->
                    Text(
                        text = reason.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Button(
                    onClick = { viewModel.submit(onDone) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                ) { Text(stringResource(R.string.amount_record)) }
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
    if (state.confirming) {
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.amount_zero_title)) },
            text = { Text(stringResource(R.string.amount_zero_explained)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirm(onDone) }) {
                    Text(stringResource(R.string.amount_zero_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@get:StringRes
private val PackageAmountViewModel.Tab.title: Int
    get() = when (this) {
        PackageAmountViewModel.Tab.RECOUNT -> R.string.amount_tab_recount
        PackageAmountViewModel.Tab.DISPOSAL -> R.string.amount_tab_disposal
    }

@get:StringRes
private val PackageAmountViewModel.Tab.amountLabel: Int
    get() = when (this) {
        PackageAmountViewModel.Tab.RECOUNT -> R.string.amount_recounted
        PackageAmountViewModel.Tab.DISPOSAL -> R.string.amount_disposed
    }

/** Чем пересчёт отличается от утилизации, сказано словами: числа у них выглядят одинаково. */
@get:StringRes
private val PackageAmountViewModel.Tab.explained: Int
    get() = when (this) {
        PackageAmountViewModel.Tab.RECOUNT -> R.string.amount_recount_explained
        PackageAmountViewModel.Tab.DISPOSAL -> R.string.amount_disposal_explained
    }

@get:StringRes
private val StockMovement.Disposal.Reason.text: Int
    get() = when (this) {
        StockMovement.Disposal.Reason.EXPIRED -> R.string.amount_reason_expired
        StockMovement.Disposal.Reason.DAMAGED -> R.string.amount_reason_damaged
        StockMovement.Disposal.Reason.OTHER -> R.string.amount_reason_other
    }

@Composable
private fun PackageAmountError.message(): String = when (this) {
    is PackageAmountError.Amount -> stringResource(reason.text)
    PackageAmountError.NothingToDispose -> stringResource(R.string.amount_nothing_to_dispose)
    PackageAmountError.NoteTooLong ->
        stringResource(R.string.amount_note_too_long, StockMovement.NOTE_MAX_LENGTH)
}

@get:StringRes
private val QuantityPresentationError.text: Int
    get() = when (this) {
        QuantityPresentationError.EMPTY -> R.string.quantity_empty
        QuantityPresentationError.TOO_LONG -> R.string.quantity_too_long
        QuantityPresentationError.NOT_A_DECIMAL -> R.string.quantity_not_a_decimal
        QuantityPresentationError.TOO_MANY_FRACTION_DIGITS -> R.string.quantity_too_many_fraction_digits
        QuantityPresentationError.TOO_MANY_INTEGER_DIGITS -> R.string.quantity_too_many_integer_digits
        QuantityPresentationError.OUT_OF_DOMAIN_RANGE -> R.string.quantity_out_of_range
        QuantityPresentationError.UNKNOWN_UNIT -> R.string.pack_unknown_in_vocabulary
    }
