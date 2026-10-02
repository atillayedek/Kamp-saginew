package com.kampusagi.android.data.repository

import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UniversityDto
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.University
import com.kampusagi.android.domain.repository.UniversityRepository
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversityRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : UniversityRepository {

    override suspend fun getActiveUniversities(): AppResult<List<University>> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall {
            client.postgrest.from("universities")
                .select(Columns.list("id", "name", "city")) {
                    filter { eq("is_active", true) }
                    order("name", Order.ASCENDING)
                }
                .decodeList<UniversityDto>()
                .map { University(id = it.id, name = it.name, city = it.city) }
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = { AppResult.Failure(it.toAppError()) },
        )
    }
}
