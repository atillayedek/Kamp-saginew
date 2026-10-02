package com.kampusagi.android.domain.usecase

import com.kampusagi.android.domain.model.AppError

/** Outcome of a use case that validates form input before calling the backend. */
sealed interface FormResult<out T, out E> {
    data class Invalid<E>(val errors: Set<E>) : FormResult<Nothing, E>
    data class Failed(val error: AppError) : FormResult<Nothing, Nothing>
    data class Success<T>(val value: T) : FormResult<T, Nothing>
}
