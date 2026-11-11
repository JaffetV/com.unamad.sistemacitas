package com.example.comunamadsistemacitas.presentation

import kotlinx.coroutines.CancellationException

/** runCatching suspend-safe: cancellation is control flow and must reach viewModelScope. */
suspend fun <T> suspendResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
