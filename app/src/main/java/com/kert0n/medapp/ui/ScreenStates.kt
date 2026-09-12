package com.kert0n.medapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.kert0n.medapp.R
import com.kert0n.medapp.presentation.LoadFailure

/**
 * Ожидание. Подписи нет, но она есть у экранного чтеца: кружок сам по себе ему ничего не говорит.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.state_loading)
    StateFrame(modifier) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

/**
 * Ничего не заведено. Это не отказ: показывать нечего, потому что человек ещё ничего не создал, —
 * и [actionText] предлагает создать, если экрану есть что предложить.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    StateFrame(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionText != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(actionText)
                }
            }
        }
    }
}

/**
 * Не вышло, и сказано почему. Повтор предлагается там, где он осмыслен: при [LoadFailure] без
 * повтора кнопки нет — нажимать на неё значило бы обещать человеку то, чего не будет.
 */
@Composable
fun ErrorMessage(
    reason: LoadFailure,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    StateFrame(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(reason.text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            if (onRetry != null && reason.isWorthRetrying) {
                Button(onClick = onRetry, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }
    }
}

/** Общая рамка всех трёх состояний: середина экрана и поля, одинаковые везде. */
@Composable
private fun StateFrame(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Текст причины — её свойство: экран не выбирает, какими словами называть отказ. */
@get:StringRes
private val LoadFailure.text: Int
    get() = when (this) {
        LoadFailure.NO_CONNECTION -> R.string.failure_no_connection
        LoadFailure.SERVER_UNAVAILABLE -> R.string.failure_server_unavailable
        LoadFailure.NOT_AUTHORIZED -> R.string.failure_not_authorized
    }

/** Повтор тем же осмыслен не всегда: отказ в пропуске им не лечится (PLAN G2). */
private val LoadFailure.isWorthRetrying: Boolean
    get() = this != LoadFailure.NOT_AUTHORIZED
