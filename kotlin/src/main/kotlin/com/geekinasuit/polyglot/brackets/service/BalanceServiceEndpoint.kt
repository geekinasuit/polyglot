package com.geekinasuit.polyglot.brackets.service

import bracketskt.BracketsNotBalancedException
import bracketskt.balancedBrackets
import com.geekinasuit.polyglot.brackets.service.protos.BalanceBracketsGrpc
import com.geekinasuit.polyglot.brackets.service.protos.BalanceRequest
import com.geekinasuit.polyglot.brackets.service.protos.BalanceResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.BindableService
import io.grpc.ServerServiceDefinition
import io.grpc.stub.StreamObserver

private val log = KotlinLogging.logger {}

class BalanceServiceEndpoint() : BalanceBracketsGrpc.AsyncService, BindableService {
  override fun balance(request: BalanceRequest, responseObserver: StreamObserver<BalanceResponse>) {
    val responseBuilder =
      BalanceResponse.newBuilder().apply {
        val balancedResult =
          try {
            log.info { "About to try balancing brackets: \"${request.statement}\"" }
            balancedBrackets(request.statement)
            log.info { "Balanced brackets completed without error." }
            setIsBalanced(true)
            setSucceeded(true)
          } catch (e: BracketsNotBalancedException) {
            setIsBalanced(false)
            setSucceeded(true)
            log.info { "Brackets were not balanced: ${e.message}" }
            setError(e.message)
          } catch (e: Exception) {
            setSucceeded(false)
            log.info { "Error balancing brackets: ${e.message}" }
            setError(e.message)
          }
      }
    responseObserver.onNext(responseBuilder.build())
    responseObserver.onCompleted()
  }

  override fun bindService(): ServerServiceDefinition = BalanceBracketsGrpc.bindService(this)
}
