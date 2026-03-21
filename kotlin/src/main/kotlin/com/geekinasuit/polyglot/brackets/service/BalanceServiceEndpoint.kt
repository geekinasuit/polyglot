package com.geekinasuit.polyglot.brackets.service

import bracketskt.BracketsNotBalancedException
import bracketskt.balancedBrackets
import com.geekinasuit.polyglot.brackets.service.protos.BalanceBracketsGrpc
import com.geekinasuit.polyglot.brackets.service.protos.BalanceRequest
import com.geekinasuit.polyglot.brackets.service.protos.BalanceResponse
import com.geekinasuit.polyglot.brackets.telemetry.attributes
import com.geekinasuit.polyglot.brackets.telemetry.counter
import com.geekinasuit.polyglot.brackets.telemetry.longHistogram
import com.geekinasuit.polyglot.brackets.telemetry.span
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.stub.StreamObserver
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

private val log = KotlinLogging.logger {}

class BalanceServiceEndpoint : BalanceBracketsGrpc.AsyncService, BindableService {
  // TODO: make @Inject constructor params when Dagger DI ticket is implemented
  private val tracer: Tracer = GlobalOpenTelemetry.get().getTracer("brackets-service")
  private val meter = GlobalOpenTelemetry.get().getMeter("brackets-service")
  private val requestCounter: LongCounter =
    meter.counter("brackets.server.request.count") { setDescription("Number of bracket balance requests") }
  private val latencyHistogram: LongHistogram =
    meter.longHistogram("brackets.server.request.duration.ms") { setDescription("Request duration in milliseconds") }

  override fun balance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
    val startMs = System.currentTimeMillis()
    val span = tracer.span("balance-brackets") {
      setAttribute("statement.length", request.statement.length.toLong())
      setAttribute("statement.text", request.statement)
    }
    var result = "error"
    try {
      span.makeCurrent().use {
        val responseBuilder = BalanceResponse.newBuilder()
        result =
          try {
            log.info { "Balancing brackets: length=${request.statement.length}" }
            balancedBrackets(request.statement)
            log.info { "Brackets are balanced." }
            responseBuilder.setIsBalanced(true).setSucceeded(true)
            "balanced"
          } catch (e: BracketsNotBalancedException) {
            log.info { "Brackets not balanced: ${e.message}" }
            responseBuilder.setIsBalanced(false).setSucceeded(true).setError(e.message)
            "not_balanced"
          } catch (e: Exception) {
            log.info { "Error balancing brackets: ${e.message}" }
            responseBuilder.setSucceeded(false).setError(e.message)
            span.setStatus(StatusCode.ERROR, e.message ?: "unknown error")
            "error"
          }
        span.setAttribute("result", result)
        responseObserver.onNext(responseBuilder.build())
        responseObserver.onCompleted()
      }
    } finally {
      span.end()
      val durationMs = System.currentTimeMillis() - startMs
      val attrs = attributes { put("result", result) }
      requestCounter.add(1, attrs)
      latencyHistogram.record(durationMs, attrs)
    }
  }

  override fun bindService(): ServerServiceDefinition = BalanceBracketsGrpc.bindService(this)
}
