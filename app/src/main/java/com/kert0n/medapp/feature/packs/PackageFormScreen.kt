package com.kert0n.medapp.feature.packs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.kert0n.medapp.domain.pack.PackageFacts
import com.kert0n.medapp.domain.pack.PackageSharedFacts
import com.kert0n.medapp.presentation.pack.PackageFormError
import com.kert0n.medapp.presentation.value.ExpiryDatePresentationError
import com.kert0n.medapp.presentation.value.MoneyPresentationError
import com.kert0n.medapp.presentation.value.QuantityPresentationError
import com.kert0n.medapp.ui.DateField
import com.kert0n.medapp.ui.ExpandableSection
import com.kert0n.medapp.ui.PickerField
import kotlin.uuid.Uuid

/**
 * Заведение (PLAN H3 №7) и правка (№8) упаковки. Сверху — четыре поля, без которых упаковки не
 * бывает: аптечка, название, количество и единица (PLAN C1). Всё остальное, что человек может
 * знать о коробке, лежит ниже в раскрываемом разделе — **все поля до одного** (ТЗ 4.1.1.1), но
 * заполнять их сразу он не обязан.
 *
 * В правке количество и аптечка показаны, но не правятся: количество меняют пересчёт и
 * утилизация (экран 9), место — перенос (экран 11). У обоих есть свой след в истории, а у правки
 * описания его нет и быть не должно (PLAN D7, F5).
 *
 * Кнопка сохранения не гаснет: погашенная не объясняет, чего не хватает, — отказ называет поле и
 * причину.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageFormScreen(
    medKitId: Uuid?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    packageId: Uuid? = null,
    viewModel: PackageFormViewModel = hiltViewModel()
) {
    LaunchedEffect(medKitId, packageId) { viewModel.open(medKitId, packageId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    // Пачку удалили, пока форму держали открытой: писать некуда, и держать форму незачем.
    LaunchedEffect(state.gone) { if (state.gone) onDone() }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEditing) R.string.pack_edit else R.string.pack_new))
                },
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
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isEditing) {
                // Показано, но не правится: у переноса и пересчёта свои экраны и свой след.
                Stored(
                    label = stringResource(R.string.pack_med_kit),
                    value = state.medKits.firstOrNull { it.id == form.medKitId }?.name
                        ?: stringResource(R.string.pack_med_kit_unknown)
                )
                Stored(
                    label = stringResource(R.string.pack_amount),
                    value = state.stored?.let {
                        stringResource(R.string.pack_left, it.quantity.amount, it.quantity.unit.name)
                    } ?: stringResource(R.string.state_loading)
                )
            } else {
                PickerField(
                    label = stringResource(R.string.pack_med_kit),
                    selected = state.medKits.firstOrNull { it.id == form.medKitId },
                    options = state.medKits,
                    optionText = { it.name },
                    onPick = { viewModel.edit(form.copy(medKitId = it.id)) },
                    isError = state.error?.field == PackageFormError.Field.MED_KIT,
                    emptyText = stringResource(R.string.pack_no_med_kits)
                )
            }
            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.edit(form.copy(name = it)) },
                label = { Text(stringResource(R.string.pack_name)) },
                isError = state.error?.field == PackageFormError.Field.NAME,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (!state.isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = form.amount,
                        onValueChange = { viewModel.edit(form.copy(amount = it)) },
                        label = { Text(stringResource(R.string.pack_amount)) },
                        isError = state.error?.field == PackageFormError.Field.AMOUNT,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    PickerField(
                        label = stringResource(R.string.pack_unit),
                        selected = state.units.firstOrNull { it.id == form.unit?.id },
                        options = state.units,
                        optionText = { it.name },
                        onPick = { viewModel.edit(form.copy(unit = it)) },
                        isError = state.error?.field == PackageFormError.Field.UNIT,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ExpandableSection(
                title = stringResource(R.string.pack_optional),
                expanded = state.optionalShown,
                onToggle = viewModel::toggleOptional
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PickerField(
                        label = stringResource(R.string.pack_form),
                        selected = state.forms.firstOrNull { it.id == form.form?.id },
                        options = state.forms,
                        optionText = { it.name },
                        onPick = { viewModel.edit(form.copy(form = it)) },
                        isError = state.error?.field == PackageFormError.Field.FORM
                    )
                    OutlinedTextField(
                        value = form.expiresOn,
                        onValueChange = { viewModel.edit(form.copy(expiresOn = it)) },
                        label = { Text(stringResource(R.string.pack_expires_on)) },
                        placeholder = { Text(stringResource(R.string.pack_expires_on_example)) },
                        isError = state.error?.field == PackageFormError.Field.EXPIRY,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.category,
                        onValueChange = { viewModel.edit(form.copy(category = it)) },
                        label = { Text(stringResource(R.string.pack_category)) },
                        isError = state.error?.field == PackageFormError.Field.CATEGORY,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.manufacturer,
                        onValueChange = { viewModel.edit(form.copy(manufacturer = it)) },
                        label = { Text(stringResource(R.string.pack_manufacturer)) },
                        isError = state.error?.field == PackageFormError.Field.MANUFACTURER,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.country,
                        onValueChange = { viewModel.edit(form.copy(country = it)) },
                        label = { Text(stringResource(R.string.pack_country)) },
                        isError = state.error?.field == PackageFormError.Field.COUNTRY,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = { viewModel.edit(form.copy(description = it)) },
                        label = { Text(stringResource(R.string.pack_description)) },
                        isError = state.error?.field == PackageFormError.Field.DESCRIPTION,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.defaultIntakeAmount,
                        onValueChange = { viewModel.edit(form.copy(defaultIntakeAmount = it)) },
                        label = { Text(stringResource(R.string.pack_intake_hint)) },
                        supportingText = { Text(stringResource(R.string.pack_intake_hint_explained)) },
                        isError = state.error?.field == PackageFormError.Field.HINT,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = { viewModel.edit(form.copy(note = it)) },
                        label = { Text(stringResource(R.string.pack_note)) },
                        isError = state.error?.field == PackageFormError.Field.NOTE,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = form.price,
                        onValueChange = { viewModel.edit(form.copy(price = it)) },
                        label = { Text(stringResource(R.string.pack_price)) },
                        isError = state.error?.field == PackageFormError.Field.PRICE,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DateField(
                        label = stringResource(R.string.pack_purchased_on),
                        value = form.purchasedOn,
                        onPick = { viewModel.edit(form.copy(purchasedOn = it)) }
                    )
                    DateField(
                        label = stringResource(R.string.pack_opened_on),
                        value = form.openedOn,
                        onPick = { viewModel.edit(form.copy(openedOn = it)) }
                    )
                }
            }

            state.error?.let { reason ->
                Text(
                    text = reason.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = { viewModel.save { onDone() } },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.action_save)) }
            TextButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/** Сведение, которое форма показывает, но не правит: его меняют другим действием и с другим следом. */
@Composable
private fun Stored(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Текст отказа — его собственное свойство: экран не подбирает слова сам. Слишком длинное поле
 * называет и себя, и свой предел: предел держит тип сведений, а не форма.
 */
@Composable
private fun PackageFormError.message(): String = when (this) {
    PackageFormError.MedKitMissing -> stringResource(R.string.pack_med_kit_missing)
    PackageFormError.UnitMissing -> stringResource(R.string.pack_unit_missing)
    PackageFormError.NameEmpty -> stringResource(R.string.pack_name_empty)
    is PackageFormError.TooLong ->
        stringResource(R.string.pack_too_long, stringResource(field.label), limit)
    is PackageFormError.Amount -> stringResource(reason.text)
    PackageFormError.AmountIsZero -> stringResource(R.string.pack_amount_is_zero)
    is PackageFormError.Hint -> stringResource(reason.text)
    PackageFormError.HintIsZero -> stringResource(R.string.pack_hint_is_zero)
    is PackageFormError.Expiry -> stringResource(reason.text)
    is PackageFormError.Price -> stringResource(reason.text)
    is PackageFormError.UnknownInVocabulary -> stringResource(R.string.pack_unknown_in_vocabulary)
}

/** Предел берётся у типа сведений, а не повторяется числом в тексте. Нетекстовых полей тут нет. */
private val PackageFormError.TooLong.limit: Int
    get() = when (field) {
        PackageFormError.Field.NAME -> PackageSharedFacts.NAME_MAX_LENGTH
        PackageFormError.Field.CATEGORY -> PackageSharedFacts.CATEGORY_MAX_LENGTH
        PackageFormError.Field.MANUFACTURER -> PackageSharedFacts.MANUFACTURER_MAX_LENGTH
        PackageFormError.Field.COUNTRY -> PackageSharedFacts.COUNTRY_MAX_LENGTH
        PackageFormError.Field.DESCRIPTION -> PackageSharedFacts.DESCRIPTION_MAX_LENGTH
        else -> PackageFacts.NOTE_MAX_LENGTH
    }

@get:StringRes
private val PackageFormError.Field.label: Int
    get() = when (this) {
        PackageFormError.Field.MED_KIT -> R.string.pack_med_kit
        PackageFormError.Field.NAME -> R.string.pack_name
        PackageFormError.Field.AMOUNT -> R.string.pack_amount
        PackageFormError.Field.UNIT -> R.string.pack_unit
        PackageFormError.Field.FORM -> R.string.pack_form
        PackageFormError.Field.CATEGORY -> R.string.pack_category
        PackageFormError.Field.MANUFACTURER -> R.string.pack_manufacturer
        PackageFormError.Field.COUNTRY -> R.string.pack_country
        PackageFormError.Field.DESCRIPTION -> R.string.pack_description
        PackageFormError.Field.EXPIRY -> R.string.pack_expires_on
        PackageFormError.Field.HINT -> R.string.pack_intake_hint
        PackageFormError.Field.NOTE -> R.string.pack_note
        PackageFormError.Field.PRICE -> R.string.pack_price
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

@get:StringRes
private val ExpiryDatePresentationError.text: Int
    get() = when (this) {
        ExpiryDatePresentationError.EMPTY -> R.string.expiry_empty
        ExpiryDatePresentationError.UNKNOWN_FORMAT -> R.string.expiry_unknown_format
        ExpiryDatePresentationError.IMPOSSIBLE_DATE -> R.string.expiry_impossible_date
    }

@get:StringRes
private val MoneyPresentationError.text: Int
    get() = when (this) {
        MoneyPresentationError.EMPTY -> R.string.price_empty
        MoneyPresentationError.TOO_LONG -> R.string.price_too_long
        MoneyPresentationError.NOT_A_DECIMAL -> R.string.price_not_a_decimal
        MoneyPresentationError.UNKNOWN_CURRENCY -> R.string.price_unknown_currency
        MoneyPresentationError.OUT_OF_CURRENCY_RANGE -> R.string.price_out_of_range
    }
