package com.mygithub.lab.data.update

/**
 * 语义化版本号（SemVer）
 *
 * 支持 `1.2.3` 与 `v1.2.3` 两种写法，忽略 `-beta` 之类的预发布后缀。
 * 仅比较主/次/修订三个数字段，缺失的段按 0 处理。
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<Version> {

    override fun compareTo(other: Version): Int = when {
        major != other.major -> major - other.major
        minor != other.minor -> minor - other.minor
        else -> patch - other.patch
    }

    /** 用于展示，始终带 `v` 前缀（如 `v0.0.2`） */
    val display: String get() = "v$major.$minor.$patch"

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        /**
         * 宽松解析：从任意字符串中提取开头的版本号。
         * 无法识别时返回 null。
         */
        fun parse(raw: String?): Version? {
            val text = raw?.trim()?.removePrefix("v") ?: return null
            val match = PATTERN.find(text) ?: return null
            val (major, minor, patch) = match.destructured
            return Version(
                major = major.toIntOrNull() ?: return null,
                minor = minor.toIntOrNull() ?: 0,
                patch = patch.toIntOrNull() ?: 0
            )
        }
    }
}

/**
 * 一次可用的更新。
 *
 * 只有同时具备「APK 下载地址」与「SHA-256 校验和」才会被构造出来，
 * 因为无法校验的安装包没有安装价值。
 */
data class UpdateInfo(
    val version: Version,
    val releaseName: String,
    val releaseNotes: String,
    val releasePageUrl: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    /** 小写十六进制 SHA-256，取自 Release 说明 */
    val sha256: String
)

/**
 * 从 Release 说明中提取 SHA-256。
 *
 * 约定写法（大小写、空格、冒号均可变）：
 * ```
 * SHA256: 4b74f8c8c6961b132a15c6c64de43f5631f33306474292f0cca80da6847c700f
 * ```
 * 也接受裸哈希（说明里只写 64 位十六进制）。
 */
internal object ChecksumParser {
    private val LABELED = Regex(
        """(?:sha-?256|checksum|hash)\s*[:：=]?\s*([a-fA-F0-9]{64})""",
        RegexOption.IGNORE_CASE
    )
    private val BARE = Regex("""\b([a-fA-F0-9]{64})\b""")

    fun parse(releaseBody: String?): String? {
        val body = releaseBody ?: return null
        return (LABELED.find(body) ?: BARE.find(body))
            ?.groupValues?.get(1)
            ?.lowercase()
    }
}
