package com.geekinasuit.polyglot.brackets.service

import bracketskt.BracketsNotBalancedException
import bracketskt.balancedBrackets
import com.geekinasuit.daggergrpc.api.GrpcCallScope
import com.geekinasuit.daggergrpc.api.GrpcServiceHandler
import com.geekinasuit.polyglot.brackets.service.protos.BalanceBracketsGrpc
import com.geekinasuit.polyglot.brackets.service.protos.BalanceRequest
import com.geekinasuit.polyglot.brackets.service.protos.BalanceResponse
import com.geekinasuit.polyglot.brackets.telemetry.attributes
import com.geekinasuit.polyglot.brackets.telemetry.span
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.stub.StreamObserver
import io.opentelemetry.api.trace.StatusCode
import javax.inject.Inject

private val log = KotlinLogging.logger {}

@GrpcServiceHandler(BalanceBracketsGrpc::class)
@GrpcCallScope
class BalanceServiceEndpoint
@Inject
constructor(
    private val telemetry: BracketsServiceTelemetry,
    private val bracketPairs: Map<Char, Char>,
) : BalanceBracketsGrpc.AsyncService {

  override fun balance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
    val startMs = System.currentTimeMillis()
    val span =
        telemetry.tracer.span("balance-brackets") {
          setAttribute("statement.length", request.statement.length.toLong())
          setAttribute("statement.text", request.statement)
        }
    var result = "error"
    try {
      span.makeCurrent().use {
        val responseBuilder = BalanceResponse.newBuilder()
        result =
            if (bracketPairs.isEmpty()) {
              log.warn { "No bracket pairs configured; returning error." }
              responseBuilder.setSucceeded(false).setError("No bracket pairs configured")
              span.setStatus(StatusCode.ERROR, "No bracket pairs configured")
              "error"
            } else
                try {
                  log.info { "Balancing brackets: length=${request.statement.length}" }
                  balancedBrackets(request.statement, bracketPairs)
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
      telemetry.requestCounter.add(1, attrs)
      telemetry.latencyHistogram.record(durationMs, attrs)
    }
  }
}
