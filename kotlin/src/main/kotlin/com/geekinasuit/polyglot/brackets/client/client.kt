package com.geekinasuit.polyglot.brackets.client

import com.geekinasuit.polyglot.brackets.service.protos.BalanceBracketsGrpcKt
import com.geekinasuit.polyglot.brackets.service.protos.BalanceRequest
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.inputStream
import com.github.ajalt.clikt.parameters.types.int
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ManagedChannelBuilder
import java.io.BufferedReader
import kotlinx.coroutines.runBlocking

private const val DEFAULT_HOST = "localhost"
private const val DEFAULT_PORT = 8888
private val log = KotlinLogging.logger {}

class BracketsClient : CliktCommand(name = "brackets_client") {
  val host by option().default(DEFAULT_HOST).help("Service IP port number")
  val port by option().int().default(DEFAULT_PORT).help("Service IP port number")
  val text by argument().inputStream()

  override fun run(): Unit = runBlocking {
    println("brackets client running...")
    val buffer = text.bufferedReader().use(BufferedReader::readText)
    text.close()

    println("About to check:\n========")
    println(buffer)
    println("========")
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    val stub = BalanceBracketsGrpcKt.BalanceBracketsCoroutineStub(channel)
    val request = BalanceRequest.newBuilder().setStatement(buffer).build()
    val response =
      try {
        stub.balance(request)
      } catch (e: Exception) {
        System.err.println(
          "Error connecting to bracket balance service: ${e.cause?.message ?: e.message}"
        )
        throw ProgramResult(1)
      }
    log.info { response }
    when {
      !response.succeeded -> {
        System.err.println("Error balancing brackets: ${response.error}")
        throw ProgramResult(2)
      }
      response.isBalanced -> println("Brackets are balanced.")
      else -> println("Brackets are NOT balanced: ${response.error}")
    }
  }
}

fun main(vararg args: String) = BracketsClient().main(args)
