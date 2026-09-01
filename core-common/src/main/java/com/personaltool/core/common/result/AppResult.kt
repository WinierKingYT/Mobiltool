package com.personaltool.core.common.result

sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val code: ErrorCode = ErrorCode.UNKNOWN
    ) : AppResult<Nothing>
    data object Loading : AppResult<Nothing>
}

enum class ErrorCode {
    UNKNOWN,
    NOT_FOUND,
    STORAGE_ERROR,
    UNSUPPORTED_DEVICE,
    PERMISSION_DENIED,
    CAPTURE_FAILED,
    EXTRACTION_FAILED,
    TRANSCRIPTION_FAILED,
    NETWORK_ERROR,
    VALIDATION_ERROR,
    SECURITY_VIOLATION
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
    is AppResult.Loading -> AppResult.Loading
}
