package com.kert0n.medapp.feature.medkits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.kert0n.medapp.ui.EmptyState
import com.kert0n.medapp.ui.SearchEntry
import com.kert0n.medapp.ui.LoadingState
import kotlin.uuid.Uuid

/**
 * Список аптечек (PLAN H3 №2). Пока ни одной не заведено, экран не притворяется списком: он
 * говорит, что такое аптечка, и предлагает завести первую.
 *
 * Поиск отсюда ведёт ко всем лекарствам сразу (экран 5): ища конкретное лекарство, человек не
 * помнит, в какой оно аптечке, — это одно чтение в двух областях, и вторая область здесь.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedKitListScreen(
    onOpen: (Uuid) -> Unit,
    onAdd: () -> Unit,
    onSearch: () -> Unit,
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
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Поиск отсюда ищет по всем аптечкам: когда человек ищет лекарство, он не помнит,
                // в какой оно коробке (PLAN H3, REQ-029).
                item {
                    SearchEntry(
                        hint = stringResource(R.string.search_medicines_everywhere),
                        onClick = onSearch
                    )
                }
                items(kits, key = { it.id }) { kit -> MedKitCard(kit, onOpen = { onOpen(kit.id) }) }
            }
        }
    }
}
