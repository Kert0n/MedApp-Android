package com.kert0n.medapp.feature.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kert0n.medapp.R
import com.kert0n.medapp.ui.EmptyState
import com.kert0n.medapp.ui.LoadingState
import kotlin.uuid.Uuid

/**
 * Содержимое аптечки (PLAN H3 №4): что в ней лежит и что с этим можно сделать. Пустая аптечка
 * говорит, что она пуста, и предлагает завести первую упаковку, — молчание неотличимо от
 * незагруженного.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedKitContentsScreen(
    medKitId: Uuid,
    onBack: () -> Unit,
    onOpen: (Uuid) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedKitContentsViewModel = hiltViewModel()
) {
    LaunchedEffect(medKitId) { viewModel.open(medKitId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.medKit?.name ?: stringResource(R.string.med_kit_contents)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!state.packs.isNullOrEmpty()) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.pack_add)
                    )
                }
            }
        }
    ) { padding ->
        val packs = state.packs
        when {
            packs == null -> LoadingState(Modifier.padding(padding))
            packs.isEmpty() -> EmptyState(
                text = stringResource(R.string.pack_none_here),
                modifier = Modifier.padding(padding),
                actionText = stringResource(R.string.pack_add),
                onAction = onAdd
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(packs, key = { it.id }) { pkg ->
                    PackageCard(pkg, onOpen = { onOpen(pkg.id) })
                }
            }
        }
    }
}
