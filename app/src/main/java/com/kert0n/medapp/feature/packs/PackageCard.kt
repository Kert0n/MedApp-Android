package com.kert0n.medapp.feature.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kert0n.medapp.R
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.value.toPresentationDTO

/**
 * Упаковка в списке (PLAN H3 №4). Человек ищет глазами не строку, а лекарство: сколько осталось и
 * до какого срока, — поэтому обе величины стоят в карточке, а не открываются нажатием.
 *
 * Срок показан той же записью, какой он напечатан на коробке: «до 03.2027».
 */
@Composable
fun PackageCard(pkg: PackagePresentationDTO, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(pkg.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.pack_left, pkg.quantity.amount, pkg.quantity.unit.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = pkg.expiresOn?.let {
                    stringResource(R.string.pack_expires_until, it.toPresentationDTO().text)
                } ?: stringResource(R.string.pack_expiry_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
