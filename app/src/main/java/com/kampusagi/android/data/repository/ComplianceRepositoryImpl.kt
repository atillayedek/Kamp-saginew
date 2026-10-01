package com.kampusagi.android.data.repository

import android.os.Build
import com.kampusagi.android.BuildConfig
import com.kampusagi.android.core.config.AppConfig
import com.kampusagi.android.data.remote.AccessLogDto
import com.kampusagi.android.data.remote.ConsentEventDto
import com.kampusagi.android.data.remote.ConsentStatusDto
import com.kampusagi.android.data.remote.DataExportDto
import com.kampusagi.android.data.remote.DataSubjectRequestDto
import com.kampusagi.android.data.remote.LegalDocumentDto
import com.kampusagi.android.data.remote.LegalDocumentInfoDto
import com.kampusagi.android.data.remote.ModerationDecisionDto
import com.kampusagi.android.data.remote.SupabaseProvider
import com.kampusagi.android.data.remote.UnknownStatusException
import com.kampusagi.android.data.remote.functionError
import com.kampusagi.android.data.remote.safeCall
import com.kampusagi.android.data.remote.toAppError
import com.kampusagi.android.domain.model.AccessLogEntry
import com.kampusagi.android.domain.model.AccountGate
import com.kampusagi.android.domain.model.AccountGateState
import com.kampusagi.android.domain.model.AppError
import com.kampusagi.android.domain.model.AppResult
import com.kampusagi.android.domain.model.ComplianceConfig
import com.kampusagi.android.domain.model.ConsentAction
import com.kampusagi.android.domain.model.ConsentEvent
import com.kampusagi.android.domain.model.ConsentStatus
import com.kampusagi.android.domain.model.DataExport
import com.kampusagi.android.domain.model.DataRequestStatus
import com.kampusagi.android.domain.model.DataRequestType
import com.kampusagi.android.domain.model.DataSubjectRequest
import com.kampusagi.android.domain.model.LegalDocument
import com.kampusagi.android.domain.model.LegalDocumentInfo
import com.kampusagi.android.domain.model.LegalKind
import com.kampusagi.android.domain.model.ModerationDecision
import com.kampusagi.android.domain.model.ReportReason
import com.kampusagi.android.domain.model.ReportTarget
import com.kampusagi.android.domain.repository.ComplianceRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class ComplianceRepositoryImpl @Inject constructor(
    private val provider: SupabaseProvider,
) : ComplianceRepository {

    private val gate = MutableStateFlow<AccountGateState?>(null)
    override val accountGate: StateFlow<AccountGateState?> = gate.asStateFlow()

    private val configLock = Mutex()
    private var cachedConfig: ComplianceConfig? = null
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun refreshAccountGate() {
        if (gate.value !is AccountGateState.Ready) gate.value = AccountGateState.Loading
        gate.value = when (val result = loadGate()) {
            is AppResult.Success -> AccountGateState.Ready(result.value)
            is AppResult.Failure -> AccountGateState.Failed(result.error)
        }
    }

    override fun clearAccountGate() {
        gate.value = null
    }

    private suspend fun loadGate(): AppResult<AccountGate> = call { client ->
        val pending = client.postgrest.rpc("pending_legal_documents", JsonObject(emptyMap()))
            .decodeList<LegalDocumentInfoDto>()
            .map { it.toDomain() }
        val raw = client.postgrest.rpc("my_account_deletion", JsonObject(emptyMap())).data.trim()
        val deletion = if (raw.isEmpty() || raw == "null") null else json.decodeFromString(String.serializer(), raw)
        AccountGate(pending = pending, deletionScheduledFor = deletion)
    }

    override suspend fun config(): AppResult<ComplianceConfig> = configLock.withLock {
        cachedConfig?.let { return@withLock AppResult.Success(it) }
        call { client ->
            val values = client.postgrest.rpc("public_compliance_config", JsonObject(emptyMap())).decodeAs<JsonObject>()
            fun int(key: String): Int = values[key]?.jsonPrimitive?.intOrNull ?: throw UnknownStatusException(key)
            fun text(key: String): String = values[key]?.jsonPrimitive?.contentOrNull ?: throw UnknownStatusException(key)
            ComplianceConfig(
                minAge = int("min_age"),
                documentRetentionDays = int("document_retention_days"),
                deletionGraceDays = int("deletion_grace_days"),
                registerLogNotice = text("register_log_notice"),
                loginLogNotice = text("login_log_notice"),
                // {key} placeholders are filled from the settings themselves, e.g. the retention days.
                documentUploadNotice = Regex("\\{([a-z_]+)\\}").replace(text("document_upload_notice")) { match ->
                    values[match.groupValues[1]]?.jsonPrimitive?.contentOrNull ?: match.value
                },
            ).also { cachedConfig = it }
        }
    }

    override suspend fun legalDocuments(): AppResult<List<LegalDocumentInfo>> = call { client ->
        client.postgrest.rpc("list_legal_documents", JsonObject(emptyMap()))
            .decodeList<LegalDocumentInfoDto>()
            .map { it.toDomain() }
    }

    override suspend fun legalDocument(docType: String, version: Int?): AppResult<LegalDocument> = call { client ->
        client.postgrest.rpc(
            "get_legal_document",
            buildJsonObject {
                put("p_doc_type", docType)
                put("p_version", version)
            },
        ).decodeList<LegalDocumentDto>().firstOrNull()?.let {
            LegalDocument(it.docType, kind(it.kind), it.version, it.title, it.content, it.sha256, it.publishedAt, it.isActive)
        } ?: throw NoSuchElementException(docType)
    }

    override suspend fun acknowledge(document: LegalDocumentInfo, channel: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "acknowledge_legal_document",
            buildJsonObject {
                put("p_doc_type", document.docType)
                put("p_version", document.version)
                put("p_channel", channel)
                put("p_app_version", BuildConfig.VERSION_NAME)
                put("p_platform", PLATFORM)
            },
        )
        Unit
    }

    override suspend fun consents(): AppResult<List<ConsentStatus>> = call { client ->
        client.postgrest.rpc("my_consents", JsonObject(emptyMap())).decodeList<ConsentStatusDto>().map {
            ConsentStatus(it.docType, it.title, it.activeVersion, it.granted, it.changedAt)
        }
    }

    override suspend fun setConsent(docType: String, granted: Boolean): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "set_consent",
            buildJsonObject {
                put("p_doc_type", docType)
                put("p_granted", granted)
                put("p_channel", "settings")
                put("p_app_version", BuildConfig.VERSION_NAME)
                put("p_platform", PLATFORM)
            },
        )
        Unit
    }

    override suspend fun consentHistory(): AppResult<List<ConsentEvent>> = call { client ->
        client.postgrest.rpc("my_consent_history", JsonObject(emptyMap())).decodeList<ConsentEventDto>().map {
            ConsentEvent(
                docType = it.docType,
                version = it.version,
                action = ConsentAction.entries.firstOrNull { a -> a.name.equals(it.action, ignoreCase = true) }
                    ?: throw UnknownStatusException(it.action),
                channel = it.channel,
                at = it.createdAt,
            )
        }
    }

    override suspend fun logSignIn(): AppResult<Unit> = logEvent("login")

    override suspend fun logSignOut(): AppResult<Unit> = logEvent("logout")

    private suspend fun logEvent(event: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "log_access_event",
            buildJsonObject {
                put("p_event", event)
                put("p_device_info", deviceInfo())
                put("p_app_version", BuildConfig.VERSION_NAME)
                put("p_platform", PLATFORM)
            },
        )
        Unit
    }

    override suspend fun logFailedSignIn(email: String): AppResult<Unit> = call { client ->
        // The database keeps only a SHA-256 of the address.
        client.postgrest.rpc(
            "log_failed_login",
            buildJsonObject {
                put("p_email", email.trim().lowercase(Locale.ROOT))
                put("p_device_info", deviceInfo())
                put("p_app_version", BuildConfig.VERSION_NAME)
                put("p_platform", PLATFORM)
            },
        )
        Unit
    }

    override suspend fun accessLogs(): AppResult<List<AccessLogEntry>> = call { client ->
        client.postgrest.rpc("my_access_logs", buildJsonObject { put("p_limit", 100) }).decodeList<AccessLogDto>().map {
            AccessLogEntry(it.event, it.ip, it.deviceInfo, it.occurredAt)
        }
    }

    override suspend fun submitRequest(type: DataRequestType, details: String): AppResult<String> = call { client ->
        client.postgrest.rpc(
            "submit_data_subject_request",
            buildJsonObject {
                put("p_type", type.name.lowercase(Locale.ROOT))
                put("p_details", details.trim())
            },
        ).decodeAs<String>()
    }

    override suspend fun myRequests(): AppResult<List<DataSubjectRequest>> = call { client ->
        client.postgrest.rpc("my_data_subject_requests", JsonObject(emptyMap())).decodeList<DataSubjectRequestDto>().map {
            DataSubjectRequest(
                requestNo = it.requestNo,
                type = DataRequestType.entries.firstOrNull { t -> t.name.equals(it.type, ignoreCase = true) }
                    ?: throw UnknownStatusException(it.type),
                details = it.details,
                status = DataRequestStatus.entries.firstOrNull { s -> s.name == it.status } ?: throw UnknownStatusException(it.status),
                receivedAt = it.receivedAt,
                dueAt = it.dueAt,
                response = it.responseSummary,
            )
        }
    }

    override suspend fun exportMyData(): AppResult<DataExport> = call { client ->
        val response = client.functions.invoke(function = AppConfig.EXPORT_MY_DATA_FUNCTION, body = JsonObject(emptyMap()))
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw FunctionFailure(functionError(response.status.value, body))
        json.decodeFromString(DataExportDto.serializer(), body).let { DataExport(it.htmlUrl, it.jsonUrl, it.expiresAt) }
    }

    override suspend fun cancelDeletion(): AppResult<Unit> = call { client ->
        client.postgrest.rpc("cancel_account_deletion", JsonObject(emptyMap()))
        Unit
    }

    override suspend fun setShowFullName(show: Boolean): AppResult<Unit> = call { client ->
        client.postgrest.rpc("set_show_full_name", JsonObject(mapOf("p_show" to JsonPrimitive(show))))
        Unit
    }

    override suspend fun moderationDecisions(): AppResult<List<ModerationDecision>> = call { client ->
        client.postgrest.rpc("my_moderation_decisions", JsonObject(emptyMap())).decodeList<ModerationDecisionDto>().map {
            ModerationDecision(
                reportId = it.reportId,
                target = ReportTarget.entries.firstOrNull { t -> t.name == it.targetKind } ?: throw UnknownStatusException(it.targetKind),
                reason = ReportReason.entries.firstOrNull { r -> r.name == it.reason } ?: ReportReason.OTHER,
                suspended = it.resolution == "SUSPEND_USER",
                excerpt = it.excerpt,
                decidedAt = it.resolvedAt,
                appealStatus = it.appealStatus,
                appealNote = it.appealNote,
            )
        }
    }

    override suspend fun appeal(reportId: String, body: String): AppResult<Unit> = call { client ->
        client.postgrest.rpc(
            "submit_moderation_appeal",
            buildJsonObject {
                put("p_report_id", reportId)
                put("p_body", body.trim())
            },
        )
        Unit
    }

    override suspend fun objectToMatch(requirementId: String, matchedRequirementId: String, reason: String?): AppResult<Unit> =
        call { client ->
            client.postgrest.rpc(
                "object_to_match",
                buildJsonObject {
                    put("p_requirement_id", requirementId)
                    put("p_matched_requirement_id", matchedRequirementId)
                    put("p_reason", reason?.trim()?.ifEmpty { null })
                },
            )
            Unit
        }

    override suspend fun sensitiveTerms(): AppResult<List<String>> = call { client ->
        client.postgrest.rpc("list_sensitive_terms", JsonObject(emptyMap())).decodeList<String>()
    }

    private fun LegalDocumentInfoDto.toDomain() = LegalDocumentInfo(docType, kind(kind), version, title, sha256)

    private fun kind(value: String): LegalKind =
        LegalKind.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: throw UnknownStatusException(value)

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): AppResult<T> {
        val client = provider.client ?: return AppResult.Failure(AppError.NOT_CONFIGURED)
        return safeCall { block(client) }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                AppResult.Failure(
                    when (it) {
                        is FunctionFailure -> it.error
                        is NoSuchElementException -> AppError.NOT_FOUND
                        else -> it.toAppError()
                    },
                )
            },
        )
    }

    private class FunctionFailure(val error: AppError) : IllegalStateException("function failed: $error")

    companion object {
        private const val PLATFORM = "android"

        /** Model and OS only: nothing that identifies the person. */
        fun deviceInfo(): String = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
}
