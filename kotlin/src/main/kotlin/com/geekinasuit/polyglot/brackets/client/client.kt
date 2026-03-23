package com.geekinasuit.polyglot.brackets.client

import com.geekinasuit.polyglot.brackets.config.ClientAppConfig
import com.geekinasuit.polyglot.brackets.config.ConfigCommand
import com.geekinasuit.polyglot.brackets.config.loadConfig
import com.geekinasuit.polyglot.brackets.service.protos.BalanceBracketsGrpcKt
import com.geekinasuit.polyglot.brackets.service.protos.BalanceRequest
import com.geekinasuit.polyglot.brackets.telemetry.attributes
import com.geekinasuit.polyglot.brackets.telemetry.counter
import com.geekinasuit.polyglot.brackets.telemetry.initTelemetry
import com.geekinasuit.polyglot.brackets.telemetry.merging
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.inputStream
import com.github.ajalt.clikt.parameters.types.int
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ManagedChannelBuilder
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.resources.Resource
import java.io.BufferedReader
import kotlinx.coroutines.runBlocking

private val log = KotlinLogging.logger {}

class BracketsClient : ConfigCommand<ClientAppConfig>("brackets_client") {
  val host by
      option("--host").help("Server host (overrides config)").applyTo {
        copy(client = client.copy(host = it))
      }
  val port by
      option("--port").int().help("Server port (overrides config)").applyTo {
        copy(client = client.copy(port = it))
      }
  val environment by
      option("--environment").help("Deployment environment").applyTo {
        copy(client = client.copy(environment = it))
      }
  val instanceId by
      option("--instance-id").help("Unique instance identifier").applyTo {
        copy(client = client.copy(instanceId = it))
      }
  val otlpEndpoint by
      option("--otlp-endpoint")
          .help("OTLP gRPC endpoint for traces+metrics (e.g. http://localhost:4317)")
          .applyTo { copy(telemetry = telemetry.copy(otlpEndpoint = it)) }
  val logEndpoint by
      option("--log-endpoint")
          .help("OTLP gRPC endpoint for logs (defaults to --otlp-endpoint)")
          .applyTo { copy(telemetry = telemetry.copy(logEndpoint = it)) }
  val text by argument().inputStream()

  override fun run(): Unit = runBlocking {
    assertAllOptionsAreBound()
    val config = resolveConfig(loadConfig("/client/application.yml"))

    val resource =
        Resource.getDefault().merging {
          put("service.name", "brackets-client")
          put("deployment.environment", config.client.environment)
          put("service.instance.id", config.client.instanceId)
        }
    val grpcOtel = initTelemetry(resource, config.telemetry)

    log.info {
      "brackets client starting: target=${config.client.host}:${config.client.port} " +
          "environment=${config.client.environment} instanceId=${config.client.instanceId} " +
          "otlpEndpoint=${config.telemetry.otlpEndpoint ?: "(disabled)"}"
    }

    val callCounter =
        GlobalOpenTelemetry.get().getMeter("brackets-client").counter(
            "brackets.client.call.count"
        ) {
          setDescription("Number of bracket balance calls made by the client")
        }

    val buffer = text.bufferedReader().use(BufferedReader::readText)
    text.close()

    log.info { "Sending statement: length=${buffer.length}" }

    println("About to check:\n========")
    println(buffer)
    println("========")

    val channel =
        ManagedChannelBuilder.forAddress(config.client.host, config.client.port)
            .usePlaintext()
            .also(grpcOtel::configureChannelBuilder)
            .build()
    val stub = BalanceBracketsGrpcKt.BalanceBracketsCoroutineStub(channel)
    val request = BalanceRequest.newBuilder().setStatement(buffer).build()

    val response =
        try {
          stub.balance(request)
        } catch (e: Exception) {
          callCounter.add(1, attributes { put("result", "connection_error") })
          System.err.println(
              "Error connecting to bracket balance service: ${e.cause?.message ?: e.message}"
          )
          throw ProgramResult(1)
        }

    val result =
        if (response.succeeded) if (response.isBalanced) "balanced" else "not_balanced" else "error"
    callCounter.add(1, attributes { put("result", result) })

    log.info { "Response: succeeded=${response.succeeded} isBalanced=${response.isBalanced}" }
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
