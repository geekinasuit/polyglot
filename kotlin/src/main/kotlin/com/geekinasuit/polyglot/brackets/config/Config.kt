package com.geekinasuit.polyglot.brackets.config

/** Top-level config for the service binary. */
data class ServiceAppConfig(
    val service: ServiceConfig = ServiceConfig(),
    val telemetry: TelemetryConfig = TelemetryConfig(),
    val db: DatabaseConfig = DatabaseConfig(),
)

/** Top-level config for the client binary. */
data class ClientAppConfig(
    val client: ClientConfig = ClientConfig(),
    val telemetry: TelemetryConfig = TelemetryConfig(),
)

data class ServiceConfig(
    val host: String = "localhost",
    val port: Int = 8888,
    val environment: String = defaultEnvironment(),
    val instanceId: String = defaultInstanceId(),
)

data class ClientConfig(
    val host: String = "localhost",
    val port: Int = 8888,
    val environment: String = defaultEnvironment(),
    val instanceId: String = defaultInstanceId(),
)

data class DatabaseConfig(
    val jdbcUrl: String = "jdbc:h2:mem:brackets;DB_CLOSE_DELAY=-1",
)

data class TelemetryConfig(
    /** OTLP gRPC endpoint for traces and metrics. Null disables OTLP export. */
    val otlpEndpoint: String? = null,
    /**
     * OTLP gRPC endpoint for logs. Null falls back to [otlpEndpoint]. See [effectiveLogEndpoint].
     */
    val logEndpoint: String? = null,
    val metricsExportIntervalSeconds: Long = 60,
    val tracingEnabled: Boolean = true,
    val metricsEnabled: Boolean = true,
    /** Disabled by default — must be explicitly opted in per environment. */
    val loggingExportEnabled: Boolean = false,
)

/** Resolves the log OTLP endpoint, falling back to the main OTLP endpoint. */
val TelemetryConfig.effectiveLogEndpoint: String?
  get() = logEndpoint ?: otlpEndpoint

/**
 * Resolves the deployment environment from standard env vars, defaulting to "development". Checks
 * DEPLOYMENT_ENV then ENVIRONMENT.
 */
fun defaultEnvironment(): String =
    System.getenv("DEPLOYMENT_ENV") ?: System.getenv("ENVIRONMENT") ?: "development"

/**
 * Resolves a unique per-instance identifier using standard signals (first match wins):
 * - Kubernetes: $POD_NAME (inject via Downward API)
 * - Docker: $HOSTNAME when it looks like a container ID (12–64 hex chars)
 * - Dev: "$user@$hostname" — disambiguates concurrent local instances
 *
 * TODO: Become an @Inject constructor parameter when the Dagger ticket is implemented. See
 *   thoughts/shared/tickets/kotlin-dagger-grpc-di.md
 */
fun defaultInstanceId(): String {
  System.getenv("POD_NAME")?.let {
    return it
  }
  val hostname = System.getenv("HOSTNAME") ?: ""
  if (hostname.matches(Regex("[a-f0-9]{12,64}"))) return hostname
  val user = System.getProperty("user.name") ?: "unknown"
  val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("localhost")
  return "$user@$host"
}
