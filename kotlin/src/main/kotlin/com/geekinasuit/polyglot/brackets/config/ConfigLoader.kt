package com.geekinasuit.polyglot.brackets.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceOrFileSource

/**
 * Builds a Hoplite config loader with layered sources (lowest → highest priority):
 * 1. YAML resource file at [resourcePath] (optional; missing file is tolerated)
 * 2. Environment variables
 *
 * CLI flag overrides are applied by callers after loading via data class copy().
 *
 * Extension seam: insert additional PropertySource implementations into this builder for:
 * - Secrets manager (e.g., AWS Secrets Manager, HashiCorp Vault)
 * - Feature flag overrides (e.g., LaunchDarkly)
 *
 * Sources added later in the builder chain take higher precedence.
 *
 * Note on env var naming: with useUnderscoresAsSeparator=true, single underscores act as path
 * separators. This works cleanly for single-word nested keys (e.g., SERVICE_HOST → service.host,
 * SERVICE_PORT → service.port). For multi-word camelCase fields, the CLI flags are the reliable
 * override mechanism.
 */
fun buildConfigLoader(resourcePath: String): ConfigLoaderBuilder =
    ConfigLoaderBuilder.default()
        .addResourceOrFileSource(resourcePath, optional = true)
        .addEnvironmentSource(useUnderscoresAsSeparator = true, allowUppercaseNames = true)

inline fun <reified T : Any> loadConfig(resourcePath: String): T =
    buildConfigLoader(resourcePath).build().loadConfigOrThrow()
