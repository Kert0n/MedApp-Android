package com.kert0n.medapp.feature.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kert0n.medapp.R
import com.kert0n.medapp.ui.EmptyState
import kotlin.uuid.Uuid

/**
 * Перенос упаковки (PLAN H3 №11). Выбор — только из местных аптечек, кроме нынешней: перенос в
 * общую требует связи (PLAN C3, E6), и экран говорит это строкой, а не прячет их молча.
 *
 * Переносить некуда — это не пустой список, а рассказ: заведите вторую аптечку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageTransferScreen(
    packageId: Uuid,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PackageTransferViewModel = hiltViewModel()
) {
    LaunchedEffect(packageId) { viewModel.open(packageId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.gone) { if (state.gone) onDone() }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_title)) },
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
        if (state.candidates.isEmpty()) {
            EmptyState(
                text = stringResource(
                    if (state.hasSharedOnes) R.string.transfer_only_shared
                    else R.string.transfer_nowhere
                ),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(state.candidates, key = { it.id }) { kit ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .selectable(
                                selected = state.chosen == kit.id,
                                onClick = { viewModel.choose(kit.id) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = state.chosen == kit.id, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(kit.name, style = MaterialTheme.typography.bodyLarge)
                            kit.location?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (state.hasSharedOnes) {
                Text(
                    stringResource(R.string.transfer_shared_need_network),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Button(
                onClick = { viewModel.transfer(onDone) },
                enabled = state.chosen != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.transfer_action)) }
        }
    }
}
