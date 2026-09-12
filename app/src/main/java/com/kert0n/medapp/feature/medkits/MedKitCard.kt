package com.kert0n.medapp.feature.medkits

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kert0n.medapp.R
import com.kert0n.medapp.presentation.medkit.MedKitPresentationDTO

/**
 * Аптечка в списке (PLAN H3 №2). Человек видит не название, а **что внутри**: сколько упаковок и
 * сколько просрочено, — потому что список нужен ему, чтобы решить, куда идти.
 *
 * Просрочка и признак общей несут не только цвет: рядом значок и слова. Цветом одним нельзя —
 * его не видят ни в темноте, ни при дальтонизме, ни экранным чтецом (H3).
 */
@Composable
fun MedKitCard(medKit: MedKitPresentationDTO, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // Приподнятая, а не залитая: залитая берёт самый плотный оттенок подложки и на зелёном фоне
    // читается серым пятном, из-за чего список выглядит выключенным.
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(medKit.name, style = MaterialTheme.typography.titleMedium)

            Text(
                text = if (medKit.isEmpty) {
                    stringResource(R.string.med_kit_empty_inside)
                } else {
                    stringResource(R.string.med_kit_packages, medKit.packages)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (medKit.hasExpired) {
                Marker(
                    icon = R.drawable.ic_expired,
                    text = stringResource(R.string.med_kit_expired, medKit.expired),
                    description = stringResource(R.string.med_kit_expired_description),
                    color = MaterialTheme.colorScheme.error
                )
            }

            medKit.location?.let { place ->
                Text(
                    place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (medKit.isShared) {
                Marker(
                    icon = R.drawable.ic_shared,
                    text = stringResource(R.string.med_kit_shared, medKit.participantCount),
                    description = stringResource(R.string.med_kit_shared_description),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

/** Значок со словами: цвет уточняет то, что уже сказано, а не заменяет сказанное. */
@Composable
private fun Marker(@DrawableRes icon: Int, text: String, description: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
