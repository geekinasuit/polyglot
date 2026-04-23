package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.polyglot.brackets.config.DatabaseConfig
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.geekinasuit.polyglot.brackets.config.ServiceConfig
import com.geekinasuit.polyglot.brackets.config.TelemetryConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ApplicationGraphTest {
  private val testConfig =
      ServiceAppConfig(
          service = ServiceConfig(host = "127.0.0.1", port = 0),
          telemetry = TelemetryConfig(),
          db = DatabaseConfig(),
      )

  @Test
  fun graphBuilds() {
    val graph = TestApplicationGraph.builder().config(testConfig).build()
    assertThat(graph).isNotNull()
  }

  @Test
  fun serverProvides() {
    val graph = TestApplicationGraph.builder().config(testConfig).build()
    val server = graph.server()
    assertThat(server).isNotNull()
  }

  @Test
  fun callScopeGraphProvides() {
    val graph = TestApplicationGraph.builder().config(testConfig).build()
    val callScope = graph.callScope()
    assertThat(callScope).isNotNull()
    val endpoint = callScope.balance()
    assertThat(endpoint).isNotNull()
  }
}
