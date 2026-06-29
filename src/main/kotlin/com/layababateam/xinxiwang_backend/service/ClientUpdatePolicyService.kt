package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.AppLatestVersion
import com.layababateam.xinxiwang_backend.repository.AppLatestVersionRepository
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ClientUpdatePolicyService(
    private val mongoTemplate: MongoTemplate,
    private val appLatestVersionRepository: AppLatestVersionRepository,
) {
    fun listRules(): List<Map<String, Any?>> =
        mongoTemplate.findAll(Document::class.java, COLLECTION)
            .map { ClientUpdateRule.fromDocument(it).toResponseMap() }
            .sortedWith(compareBy({ it["platform"]?.toString() ?: "" }, { it["updatedAt"] as? Long ?: 0L }))

    fun upsertRule(request: ForceUpdateRuleRequest): Map<String, Any?> {
        val platform = ClientVersionRules.normalizePlatform(request.platform)
            ?: throw badRequest(platformErrorMessage())
        val lessThanVersion = nonBlank(request.lessThanVersion) ?: nonBlank(request.minVersion)
        val specificVersions = normalizeVersions(request.specificVersions)
        if (lessThanVersion == null && specificVersions.isEmpty()) {
            throw badRequest("请至少配置低于版本或指定版本列表中的一个触发条件")
        }

        val now = System.currentTimeMillis()
        val query = Query(Criteria.where("platform").`is`(platform))
        val update = Update()
            .set("platform", platform)
            .set("enabled", request.enabled)
            .set("specificVersions", specificVersions)
            .set("forceUpdate", request.forceUpdate)
            .set("inAppUpdate", request.inAppUpdate)
            .set("updatedAt", now)
            .setOnInsert("createdAt", now)

        if (lessThanVersion == null) {
            update.unset("lessThanVersion")
            update.unset("minVersion")
        } else {
            update.set("lessThanVersion", lessThanVersion)
            update.set("minVersion", lessThanVersion)
        }
        nonBlank(request.updateUrl)?.let { update.set("updateUrl", it) } ?: update.unset("updateUrl")

        mongoTemplate.upsert(query, update, COLLECTION)
        return requireNotNull(findRuleByPlatform(platform)).toResponseMap()
    }

    fun deleteRule(id: String): Boolean =
        mongoTemplate.remove(idQuery(id), COLLECTION).deletedCount > 0

    fun findRuleById(id: String): ClientUpdateRule? =
        mongoTemplate.findOne(idQuery(id), Document::class.java, COLLECTION)?.let(ClientUpdateRule::fromDocument)

    fun findRuleByPlatform(platform: String): ClientUpdateRule? {
        val normalized = ClientVersionRules.normalizePlatform(platform) ?: return null
        return mongoTemplate
            .findOne(Query(Criteria.where("platform").`is`(normalized)), Document::class.java, COLLECTION)
            ?.let(ClientUpdateRule::fromDocument)
    }

    fun forceDecision(platform: String, currentVersion: String): ClientUpdateDecision? {
        val normalizedPlatform = ClientVersionRules.normalizePlatform(platform) ?: return null
        val rule = findRuleByPlatform(normalizedPlatform) ?: return null
        val decision = decisionFromRule(normalizedPlatform, currentVersion, rule, latest = null)
        return decision.takeIf { it.hasUpdate && it.forceUpdate }
    }

    fun checkVersion(platform: String, currentVersion: String): ClientUpdateDecision {
        val normalizedPlatform = ClientVersionRules.normalizePlatform(platform) ?: platform.trim().lowercase()
        val latest = ClientVersionRules.normalizePlatform(platform)
            ?.let { appLatestVersionRepository.findByPlatform(it) }
        val rule = ClientVersionRules.normalizePlatform(platform)?.let(::findRuleByPlatform)

        if (rule != null) {
            return decisionFromRule(normalizedPlatform, currentVersion, rule, latest)
        }

        if (latest == null) {
            return ClientUpdateDecision(
                hasUpdate = false,
                currentVersion = currentVersion,
                platform = normalizedPlatform,
            )
        }

        val fullLatest = "${latest.latestVersion}+${latest.buildNumber}"
        val hasUpdate = ClientVersionRules.compareVersions(currentVersion, fullLatest) < 0
        val forceUpdate = hasUpdate &&
            latest.forceUpdate &&
            latest.minForceVersion?.let { ClientVersionRules.compareVersions(currentVersion, it) < 0 } == true

        return ClientUpdateDecision(
            hasUpdate = hasUpdate,
            forceUpdate = forceUpdate,
            inAppUpdate = true,
            updateUrl = latest.downloadUrl,
            latestVersion = latest.latestVersion,
            buildNumber = latest.buildNumber,
            downloadUrl = latest.downloadUrl,
            releaseNotes = latest.releaseNotes,
            currentVersion = currentVersion,
            platform = normalizedPlatform,
            minVersion = latest.minForceVersion,
            matchReason = if (hasUpdate) "latest_version" else null,
        )
    }

    fun forceUpdatePayload(decision: ClientUpdateDecision): Map<String, Any?> =
        mapOf(
            "type" to "force_update",
            "message" to "您的客户端版本过低（${decision.currentVersion}），请更新到最新版本。",
            "data" to mapOf(
                "updateUrl" to (decision.updateUrl ?: ""),
                "platform" to decision.platform,
                "currentVersion" to decision.currentVersion,
                "minVersion" to decision.minVersion,
                "lessThanVersion" to decision.lessThanVersion,
                "specificVersions" to decision.specificVersions,
                "forceUpdate" to decision.forceUpdate,
                "inAppUpdate" to decision.inAppUpdate,
                "matchReason" to decision.matchReason,
            ),
        )

    private fun decisionFromRule(
        platform: String,
        currentVersion: String,
        rule: ClientUpdateRule,
        latest: AppLatestVersion?,
    ): ClientUpdateDecision {
        val lessThanVersion = rule.lessThanVersion
        val lessThanMatched = lessThanVersion?.let {
            ClientVersionRules.compareVersions(currentVersion, it) < 0
        } ?: false
        val specificMatched = rule.specificVersions.any {
            ClientVersionRules.specificVersionMatches(currentVersion, it)
        }
        val hasUpdate = rule.enabled && (lessThanMatched || specificMatched)
        val resolvedUrl = resolveDownloadUrl(platform, rule.updateUrl, latest?.downloadUrl)

        return ClientUpdateDecision(
            hasUpdate = hasUpdate,
            forceUpdate = hasUpdate && rule.forceUpdate,
            inAppUpdate = if (hasUpdate) rule.inAppUpdate else true,
            updateUrl = if (hasUpdate) resolvedUrl else latest?.downloadUrl,
            latestVersion = latest?.latestVersion,
            buildNumber = latest?.buildNumber,
            downloadUrl = if (hasUpdate) resolvedUrl else latest?.downloadUrl,
            releaseNotes = latest?.releaseNotes,
            currentVersion = currentVersion,
            platform = platform,
            minVersion = lessThanVersion,
            lessThanVersion = lessThanVersion,
            specificVersions = rule.specificVersions,
            matchReason = when {
                lessThanMatched -> "less_than_version"
                specificMatched -> "specific_versions"
                else -> null
            },
        )
    }

    private fun resolveDownloadUrl(platform: String, ruleUrl: String?, latestUrl: String?): String =
        nonBlank(ruleUrl)
            ?: nonBlank(latestUrl)
            ?: ClientVersionRules.resolveUpdateUrl(platform, null)

    private fun idQuery(id: String): Query {
        val criteria = mutableListOf(Criteria.where("_id").`is`(id))
        if (ObjectId.isValid(id)) criteria.add(Criteria.where("_id").`is`(ObjectId(id)))
        return Query(Criteria().orOperator(*criteria.toTypedArray()))
    }

    private fun normalizeVersions(values: List<String>?): List<String> =
        values.orEmpty()
            .flatMap { it.split('\n', ',', ';', '，', '；') }
            .mapNotNull(::nonBlank)
            .distinct()

    private fun platformErrorMessage(): String =
        "平台必须是: ${ClientVersionRules.supportedPlatforms.joinToString(", ")}"

    private fun badRequest(message: String): ResponseStatusException =
        ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun nonBlank(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val COLLECTION = "client_version_rules"
    }
}

data class ClientUpdateRule(
    val id: String?,
    val platform: String,
    val minVersion: String,
    val lessThanVersion: String?,
    val specificVersions: List<String>,
    val enabled: Boolean,
    val forceUpdate: Boolean,
    val inAppUpdate: Boolean,
    val updateUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toResponseMap(): Map<String, Any?> =
        linkedMapOf(
            "id" to id,
            "platform" to platform,
            "minVersion" to minVersion,
            "lessThanVersion" to lessThanVersion,
            "specificVersions" to specificVersions,
            "enabled" to enabled,
            "forceUpdate" to forceUpdate,
            "inAppUpdate" to inAppUpdate,
            "updateUrl" to updateUrl,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
        )

    companion object {
        fun fromDocument(doc: Document): ClientUpdateRule {
            val lessThanVersion = doc.stringValue("lessThanVersion")
                ?: doc.stringValue("less_than_version")
                ?: doc.stringValue("minVersion")
            val now = System.currentTimeMillis()
            return ClientUpdateRule(
                id = doc.get("_id")?.toString(),
                platform = doc.stringValue("platform") ?: "unknown",
                minVersion = doc.stringValue("minVersion")
                    ?: doc.stringValue("min_version")
                    ?: lessThanVersion.orEmpty(),
                lessThanVersion = lessThanVersion,
                specificVersions = doc.stringList("specificVersions")
                    .ifEmpty { doc.stringList("specific_versions") },
                enabled = doc.booleanValue("enabled", default = true),
                forceUpdate = doc.booleanValue("forceUpdate", default = doc.booleanValue("force_update", default = true)),
                inAppUpdate = doc.booleanValue("inAppUpdate", default = doc.booleanValue("in_app_update", default = true)),
                updateUrl = doc.stringValue("updateUrl") ?: doc.stringValue("update_url"),
                createdAt = doc.longValue("createdAt") ?: now,
                updatedAt = doc.longValue("updatedAt") ?: now,
            )
        }
    }
}

data class ClientUpdateDecision(
    val hasUpdate: Boolean,
    val forceUpdate: Boolean = false,
    val inAppUpdate: Boolean = true,
    val updateUrl: String? = null,
    val latestVersion: String? = null,
    val buildNumber: Int? = null,
    val downloadUrl: String? = null,
    val releaseNotes: String? = null,
    val currentVersion: String,
    val platform: String,
    val minVersion: String? = null,
    val lessThanVersion: String? = null,
    val specificVersions: List<String> = emptyList(),
    val matchReason: String? = null,
) {
    fun toResponseMap(): Map<String, Any?> =
        linkedMapOf(
            "hasUpdate" to hasUpdate,
            "forceUpdate" to forceUpdate,
            "inAppUpdate" to inAppUpdate,
            "updateUrl" to updateUrl,
            "latestVersion" to latestVersion,
            "buildNumber" to buildNumber,
            "downloadUrl" to downloadUrl,
            "releaseNotes" to releaseNotes,
            "currentVersion" to currentVersion,
            "platform" to platform,
            "minVersion" to minVersion,
            "lessThanVersion" to lessThanVersion,
            "specificVersions" to specificVersions,
            "matchReason" to matchReason,
        )
}

private fun Document.stringValue(name: String): String? =
    get(name)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

private fun Document.stringList(name: String): List<String> {
    val raw = get(name) ?: return emptyList()
    return when (raw) {
        is Iterable<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
        is String -> raw.split('\n', ',', ';', '，', '；').mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        else -> emptyList()
    }
}

private fun Document.booleanValue(name: String, default: Boolean): Boolean =
    when (val value = get(name)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        else -> default
    }

private fun Document.longValue(name: String): Long? =
    when (val value = get(name)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
