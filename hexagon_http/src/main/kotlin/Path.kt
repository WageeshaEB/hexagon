package com.hexagonkt.http

import com.hexagonkt.helpers.filter
import com.hexagonkt.helpers.findGroups
import com.hexagonkt.logging.Logger

/**
 * A path definition. It parses path patterns and extract values for parameters.
 *
 * Differences with Sinatra:
 *
 *   * No splats (you can use named parameters though)
 *   * Delimiter is {var} to conform with [RFC 6570](https://tools.ietf.org/html/rfc6570)
 */
data class Path(val pattern: String) {

    private val logger: Logger = Logger(this::class)

    private companion object {
        internal const val PARAMETER_PREFIX = "{"
        internal const val PARAMETER_SUFFIX = "}"

        internal const val WILDCARD = "*"

        internal val WILDCARD_REGEX = Regex("\\$WILDCARD")
        internal val PARAMETER_REGEX = Regex("\\$PARAMETER_PREFIX\\w+$PARAMETER_SUFFIX")
        internal val PLACEHOLDER_REGEX =
            Regex("\\$WILDCARD|\\$PARAMETER_PREFIX\\w+$PARAMETER_SUFFIX")
    }

    init {
        val validPrefix = pattern.startsWith("/") || pattern.startsWith("*")
        require(validPrefix) { "'$pattern' must start with '/' or '*'" }
        require(!pattern.contains(":")) { "Variables have {var} format. Path cannot have ':' $pattern" }
    }

    val hasWildcards by lazy { WILDCARD_REGEX in pattern }
    val hasParameters by lazy { PARAMETER_REGEX in pattern }

    val parameterIndex: List<String> by lazy {
        if (hasParameters)
            PLACEHOLDER_REGEX.findAll(pattern)
                .map {
                    if (it.value == WILDCARD) ""
                    else it.value.removePrefix(PARAMETER_PREFIX).removeSuffix(PARAMETER_SUFFIX)
                }
                .toList()
        else
            emptyList()
    }

    val regex: Regex? by lazy {
        when (Pair(hasWildcards, hasParameters)) {
            Pair(first = true, second = true) ->
                Regex(pattern.replace(WILDCARD, "(.*?)").replace(PARAMETER_REGEX, "(.+?)") + "$")
            Pair(first = true, second = false) ->
                Regex(pattern.replace(WILDCARD, "(.*?)") + "$")
            Pair(first = false, second = true) ->
                Regex(pattern.replace(PARAMETER_REGEX, "(.+?)") + "$")
            else -> null
        }
    }

    val segments by lazy { pattern.split(PLACEHOLDER_REGEX) }

    fun matches(requestUrl: String) = regex?.matches(requestUrl) ?: (pattern == requestUrl)

    fun extractParameters(requestUrl: String): Map<String, String> {
        require(matches(requestUrl)) { "URL '$requestUrl' does not match path" }

        fun parameters(re: Regex) = re
            .findGroups(requestUrl)
            .mapIndexed { idx, (value) -> parameterIndex[idx] to value }
            .filter { (first) -> first != "" }
            .toMap()

        val re = regex
        return if (hasParameters && re != null) parameters(re) else emptyMap()
    }

    fun create(vararg parameters: Pair<String, Any>) =
        if (hasWildcards || parameters.size != parameterIndex.size) {
            val expectedParams = parameterIndex.size
            val paramCount = parameters.size
            logger.error { "Path has wildcards or different parameters: $expectedParams/$paramCount" }
            error("Path has wildcards or different parameters: $expectedParams/$paramCount")
        }
        else {
            val map = parameters.map { it.first to it.second.toString() }
            pattern.filter(PARAMETER_PREFIX, PARAMETER_SUFFIX, *map.toTypedArray())
        }
}
