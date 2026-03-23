package com.geekinasuit.polyglot.brackets.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TelemetryConfigTest {
  @Test
  fun `effectiveLogEndpoint returns logEndpoint when explicitly set`() {
    val config =
        TelemetryConfig(otlpEndpoint = "http://otlp:4317", logEndpoint = "http://logs:4317")
    assertThat(config.effectiveLogEndpoint).isEqualTo("http://logs:4317")
  }

  @Test
  fun `effectiveLogEndpoint falls back to otlpEndpoint when logEndpoint is null`() {
    val config = TelemetryConfig(otlpEndpoint = "http://otlp:4317", logEndpoint = null)
    assertThat(config.effectiveLogEndpoint).isEqualTo("http://otlp:4317")
  }

  @Test
  fun `effectiveLogEndpoint is null when both endpoints are null`() {
    val config = TelemetryConfig(otlpEndpoint = null, logEndpoint = null)
    assertThat(config.effectiveLogEndpoint).isNull()
  }

  @Test
  fun `default TelemetryConfig enables tracing and metrics but not log export`() {
    val config = TelemetryConfig()
    assertThat(config.tracingEnabled).isTrue()
    assertThat(config.metricsEnabled).isTrue()
    assertThat(config.loggingExportEnabled).isFalse()
  }

  @Test
  fun `default TelemetryConfig has no OTLP endpoints configured`() {
    val config = TelemetryConfig()
    assertThat(config.otlpEndpoint).isNull()
    assertThat(config.effectiveLogEndpoint).isNull()
  }

  @Test
  fun `default metrics export interval is 60 seconds`() {
    assertThat(TelemetryConfig().metricsExportIntervalSeconds).isEqualTo(60L)
  }
}
