package com.geekinasuit.polyglot.brackets.service

import com.geekinasuit.polyglot.brackets.service.protos.BalanceRequest
import com.geekinasuit.polyglot.brackets.service.protos.BalanceResponse
import com.google.common.truth.Truth.assertThat
import io.grpc.stub.StreamObserver
import io.opentelemetry.api.OpenTelemetry
import org.junit.Test

class BalanceServiceEndpointTest {
  private val telemetry = BracketsServiceTelemetry(OpenTelemetry.noop())
  private val defaultPairs = mapOf(')' to '(', ']' to '[', '}' to '{')

  private fun endpoint(pairs: Map<Char, Char> = defaultPairs) =
      BalanceServiceEndpoint(telemetry, pairs)

  private fun balance(
      statement: String,
      pairs: Map<Char, Char> = defaultPairs,
  ): BalanceResponse {
    var response: BalanceResponse? = null
    endpoint(pairs)
        .balance(
            BalanceRequest.newBuilder().setStatement(statement).build(),
            object : StreamObserver<BalanceResponse> {
              override fun onNext(value: BalanceResponse) {
                response = value
              }

              override fun onError(t: Throwable) = Unit

              override fun onCompleted() = Unit
            },
        )
    return checkNotNull(response)
  }

  @Test
  fun balancedInput_succeeds() {
    val response = balance("(hello [world])")
    assertThat(response.succeeded).isTrue()
    assertThat(response.isBalanced).isTrue()
  }

  @Test
  fun unbalancedInput_returnsNotBalanced() {
    val response = balance("(hello [world)")
    assertThat(response.succeeded).isTrue()
    assertThat(response.isBalanced).isFalse()
    assertThat(response.error).isNotEmpty()
  }

  @Test
  fun emptyPairs_returnsError() {
    val response = balance("(hello)", emptyMap())
    assertThat(response.succeeded).isFalse()
    assertThat(response.error).contains("No bracket pairs configured")
  }

  @Test
  fun emptyStatement_succeeds() {
    val response = balance("")
    assertThat(response.succeeded).isTrue()
    assertThat(response.isBalanced).isTrue()
  }
}
