package com.kert0n.medapp.feature.medkits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kert0n.medapp.R
import com.kert0n.medapp.domain.medkit.MedKit
import com.kert0n.medapp.presentation.medkit.MedKitFormError
import kotlin.uuid.Uuid

/**
 * Заведение и правка аптечки (PLAN H3 №3). Обязательно одно название; место хранения человек
 * указывает, если оно ему нужно.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedKitFormScreen(
    medKitId: Uuid?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedKitFormViewModel = hiltViewModel()
) {
    LaunchedEffect(medKitId) { viewModel.open(medKitId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.med_kit_edit else R.string.med_kit_new
                        )
                    )
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
            OutlinedTextField(
                value = state.form.name,
                onValueChange = { viewModel.edit(state.form.copy(name = it)) },
                label = { Text(stringResource(R.string.med_kit_name)) },
                isError = state.error == MedKitFormError.NAME_EMPTY ||
                    state.error == MedKitFormError.NAME_TOO_LONG,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.form.location,
                onValueChange = { viewModel.edit(state.form.copy(location = it)) },
                label = { Text(stringResource(R.string.med_kit_location)) },
                isError = state.error == MedKitFormError.LOCATION_TOO_LONG,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let { reason ->
                Text(
                    text = stringResource(reason.text, reason.limit),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = { viewModel.save(onDone) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.action_save)) }
            TextButton(
                onClick = onDone,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/** Текст причины — её свойство, как и у отказов загрузки: экран не подбирает слова сам. */
private val MedKitFormError.text: Int
    get() = when (this) {
        MedKitFormError.NAME_EMPTY -> R.string.med_kit_name_empty
        MedKitFormError.NAME_TOO_LONG -> R.string.med_kit_name_too_long
        MedKitFormError.LOCATION_TOO_LONG -> R.string.med_kit_location_too_long
    }

/** Предел берётся у типа, который его держит, а не повторяется числом в тексте. */
private val MedKitFormError.limit: Int
    get() = when (this) {
        MedKitFormError.LOCATION_TOO_LONG -> MedKit.LOCATION_MAX_LENGTH
        else -> MedKit.NAME_MAX_LENGTH
    }
