package com.geekinasuit.polyglot.brackets.service

import com.geekinasuit.polyglot.brackets.config.ConfigCommand
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.geekinasuit.polyglot.brackets.config.loadConfig
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.grpc.GrpcService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.BindableService
import io.grpc.ServerInterceptor

private val log = KotlinLogging.logger {}

class BracketsService : ConfigCommand<ServiceAppConfig>("brackets_service") {
  val host by option("--host").help("Bind host (overrides config)")
    .applyTo { copy(service = service.copy(host = it)) }
  val port by option("--port").int().help("Bind port (overrides config)")
    .applyTo { copy(service = service.copy(port = it)) }
  val environment by option("--environment").help("Deployment environment")
    .applyTo { copy(service = service.copy(environment = it)) }
  val instanceId by option("--instance-id").help("Unique instance identifier")
    .applyTo { copy(service = service.copy(instanceId = it)) }
  val otlpEndpoint by option("--otlp-endpoint")
    .help("OTLP gRPC endpoint for traces+metrics (e.g. http://localhost:4317)")
    .applyTo { copy(telemetry = telemetry.copy(otlpEndpoint = it)) }
  val logEndpoint by option("--log-endpoint")
    .help("OTLP gRPC endpoint for logs (defaults to --otlp-endpoint)")
    .applyTo { copy(telemetry = telemetry.copy(logEndpoint = it)) }

  override fun run() {
    assertAllOptionsAreBound()
    val config = resolveConfig(loadConfig("/service/application.yml"))

    log.info {
      "brackets service starting: host=${config.service.host} port=${config.service.port} " +
        "environment=${config.service.environment} instanceId=${config.service.instanceId} " +
        "otlpEndpoint=${config.telemetry.otlpEndpoint ?: "(disabled)"}"
    }

    val server =
      Server.builder().http(config.service.port).service(wrapService(BalanceServiceEndpoint())).build()
    server.closeOnJvmShutdown().thenRun { log.info { "Server has been stopped." } }
    server.start().join()
  }
}

fun main(vararg args: String) = BracketsService().main(args)

fun wrapService(
  bindableService: BindableService,
  vararg interceptors: ServerInterceptor,
): GrpcService {
  return GrpcService.builder().addService(bindableService).intercept(*interceptors).build()
}
