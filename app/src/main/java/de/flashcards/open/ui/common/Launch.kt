package de.flashcards.open.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flashcards.open.Messenger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Starts a coroutine; errors reach the user as a snackbar instead of being swallowed. */
fun ViewModel.launchSafe(messenger: Messenger, block: suspend () -> Unit): Job = viewModelScope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        messenger.error(e)
    }
}
