package com.kampusagi.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LegalDocumentInfoDto(
    @SerialName("doc_type") val docType: String,
    val kind: String,
    val version: Int,
    val title: String,
    @SerialName("content_sha256") val sha256: String,
)

@Serializable
data class LegalDocumentDto(
    @SerialName("doc_type") val docType: String,
    val kind: String,
    val version: Int,
    val title: String,
    val content: String,
    @SerialName("content_sha256") val sha256: String,
    @SerialName("published_at") val publishedAt: String,
    @SerialName("is_active") val isActive: Boolean,
)

@Serializable
data class ConsentStatusDto(
    @SerialName("doc_type") val docType: String,
    val title: String,
    @SerialName("active_version") val activeVersion: Int,
    val granted: Boolean,
    @SerialName("changed_at") val changedAt: String? = null,
)

@Serializable
data class ConsentEventDto(
    @SerialName("document_type") val docType: String,
    @SerialName("document_version") val version: Int? = null,
    val action: String,
    val channel: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DataSubjectRequestDto(
    @SerialName("request_no") val requestNo: String,
    val type: String,
    val details: String,
    val status: String,
    @SerialName("received_at") val receivedAt: String,
    @SerialName("due_at") val dueAt: String,
    @SerialName("response_summary") val responseSummary: String? = null,
)

@Serializable
data class AccessLogDto(
    val event: String,
    val ip: String? = null,
    @SerialName("device_info") val deviceInfo: String? = null,
    @SerialName("occurred_at") val occurredAt: String,
)

@Serializable
data class DataExportDto(
    @SerialName("json_url") val jsonUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class DeletionScheduleDto(@SerialName("scheduled_for") val scheduledFor: String? = null)

@Serializable
data class ModerationDecisionDto(
    @SerialName("report_id") val reportId: String,
    @SerialName("target_kind") val targetKind: String,
    val reason: String,
    val resolution: String,
    val excerpt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("appeal_status") val appealStatus: String? = null,
    @SerialName("appeal_note") val appealNote: String? = null,
)
