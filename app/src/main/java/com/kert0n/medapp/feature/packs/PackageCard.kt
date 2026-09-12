package com.kert0n.medapp.feature.packs

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
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
import com.kert0n.medapp.domain.pack.ExpiryDate
import com.kert0n.medapp.presentation.pack.PackagePresentationDTO
import com.kert0n.medapp.presentation.value.toPresentationDTO
import com.kert0n.medapp.ui.theme.accents
import java.time.LocalDate

/**
 * Упаковка в списке (PLAN H3 №4). Человек ищет глазами не строку, а лекарство: сколько осталось и
 * до какого срока, — поэтому обе величины стоят в карточке, а не открываются нажатием. Срок
 * показан той же записью, какой он напечатан на коробке: «до 03.2027». В списке всех лекарств
 * (экран 5) добавляется [medKitName] — иначе одинаковые названия из разных аптечек не различить.
 *
 * Просроченная упаковка выделена целиком и названа словом: она не исчезает из списка и не ждёт,
 * пока человек прочитает дату и сравнит её с сегодняшним днём (PLAN D3, H3). Цвет тут не
 * единственный носитель смысла — рядом значок и слово, потому что цвет не виден ни экранному
 * чтецу, ни при дальтонизме.
 */
@Composable
fun PackageCard(
    pkg: PackagePresentationDTO,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    medKitName: String? = null,
    today: LocalDate = LocalDate.MIN
) {
    val expired = pkg.expiresOn?.isExpiredOn(today) == true
    val soon = pkg.expiresOn?.expiresWithin(today, ExpiryDate.SOON_DAYS) == true
    ElevatedCard(
        onClick = onOpen,
        colors = if (expired) {
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        } else {
            CardDefaults.elevatedCardColors()
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(pkg.name, style = MaterialTheme.typography.titleMedium)
            Aside(
                stringResource(R.string.pack_left, pkg.quantity.amount, pkg.quantity.unit.name),
                onError = expired
            )
            medKitName?.let { Aside(it, onError = expired) }
            when {
                expired -> Marker(
                    icon = R.drawable.ic_expired,
                    text = stringResource(
                        R.string.pack_expired_on,
                        pkg.expiresOn.toPresentationDTO().text
                    ),
                    description = stringResource(R.string.med_kit_expired_description),
                    // На красной подложке красным писать нечем: цвет берётся у неё самой.
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                soon -> Marker(
                    icon = R.drawable.ic_warning,
                    text = stringResource(
                        R.string.pack_expires_soon,
                        pkg.expiresOn.toPresentationDTO().text
                    ),
                    description = stringResource(R.string.pack_expires_soon_description),
                    color = MaterialTheme.accents.reserved
                )
                pkg.expiresOn != null -> Aside(
                    stringResource(R.string.pack_expires_until, pkg.expiresOn.toPresentationDTO().text),
                    onError = false
                )
                else -> Aside(stringResource(R.string.pack_expiry_unknown), onError = false)
            }
        }
    }
}

/**
 * Второстепенное: то, что человек читает вторым взглядом, а не первым. На красной подложке
 * приглушать нечем — приглушённый серый на ней читается хуже обычного.
 */
@Composable
private fun Aside(text: String, onError: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (onError) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Значок со словами: цвет уточняет то, что уже сказано, а не заменяет сказанное. */
@Composable
private fun Marker(@DrawableRes icon: Int, text: String, description: String, color: Color) {
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
