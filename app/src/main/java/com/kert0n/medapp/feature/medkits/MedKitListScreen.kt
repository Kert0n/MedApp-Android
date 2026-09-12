package com.kert0n.medapp.feature.medkits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kert0n.medapp.R
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO
import com.kert0n.medapp.ui.EmptyState
import com.kert0n.medapp.ui.LoadingState
import kotlin.uuid.Uuid

/**
 * Список аптечек (PLAN H3 №2). Пока ни одной не заведено, экран не притворяется списком: он
 * говорит, что такое аптечка, и предлагает завести первую.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedKitListScreen(
    onOpen: (Uuid) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedKitsViewModel = hiltViewModel()
) {
    val medKits by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.med_kits_title)) }) },
        floatingActionButton = {
            if (!medKits.isNullOrEmpty()) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(
                        painterResource(R.drawable.ic_tab_med_kits),
                        contentDescription = stringResource(R.string.med_kits_add)
                    )
                }
            }
        }
    ) { padding ->
        val kits = medKits
        when {
            // Первое чтение базы ещё не пришло: показывать «пусто» рано — это было бы неправдой.
            kits == null -> LoadingState(Modifier.padding(padding))
            kits.isEmpty() -> EmptyState(
                text = stringResource(R.string.med_kits_empty),
                modifier = Modifier.padding(padding),
                actionText = stringResource(R.string.med_kits_add),
                onAction = onAdd
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(kits, key = { it.id }) { kit -> MedKitRow(kit) { onOpen(kit.id) } }
            }
        }
    }
}

@Composable
private fun MedKitRow(medKit: MedKitPresentationDTO, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(medKit.name, style = MaterialTheme.typography.titleMedium)
        val subtitle = subtitleOf(medKit)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Место хранения и участие — то, чем одна аптечка отличается от другой в списке. */
@Composable
private fun subtitleOf(medKit: MedKitPresentationDTO): String? {
    val shared = if (medKit.isShared) {
        stringResource(R.string.med_kit_shared, medKit.participantCount)
    } else {
        null
    }
    return listOfNotNull(medKit.location, shared).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
