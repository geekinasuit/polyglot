package com.geekinasuit.polyglot.brackets.service

import com.geekinasuit.polyglot.brackets.config.ConfigCommand
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.geekinasuit.polyglot.brackets.config.loadConfig
import com.geekinasuit.polyglot.brackets.telemetry.initTelemetry
import com.geekinasuit.polyglot.brackets.telemetry.merging
import com.geekinasuit.polyglot.brackets.telemetry.span
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.grpc.GrpcService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.BindableService
import io.grpc.ServerInterceptor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaServerTelemetry
import io.opentelemetry.sdk.resources.Resource
import java.net.InetSocketAddress

private val log = KotlinLogging.logger {}

class BracketsService : ConfigCommand<ServiceAppConfig>("brackets_service") {
  val host by
      option("--host").help("Bind host (overrides config)").applyTo {
        copy(service = service.copy(host = it))
      }
  val port by
      option("--port").int().help("Bind port (overrides config)").applyTo {
        copy(service = service.copy(port = it))
      }
  val environment by
      option("--environment").help("Deployment environment").applyTo {
        copy(service = service.copy(environment = it))
      }
  val instanceId by
      option("--instance-id").help("Unique instance identifier").applyTo {
        copy(service = service.copy(instanceId = it))
      }
  val otlpEndpoint by
      option("--otlp-endpoint")
          .help("OTLP gRPC endpoint for traces+metrics (e.g. http://localhost:4317)")
          .applyTo { copy(telemetry = telemetry.copy(otlpEndpoint = it)) }
  val logEndpoint by
      option("--log-endpoint")
          .help("OTLP gRPC endpoint for logs (defaults to --otlp-endpoint)")
          .applyTo { copy(telemetry = telemetry.copy(logEndpoint = it)) }

  override fun run() {
    assertAllOptionsAreBound()
    val config = resolveConfig(loadConfig("/service/application.yml"))

    val resource =
        Resource.getDefault().merging {
          put("service.name", "brackets-service")
          put("deployment.environment", config.service.environment)
          put("service.instance.id", config.service.instanceId)
        }
    initTelemetry(resource, config.telemetry)

    log.info {
      "brackets service starting: host=${config.service.host} port=${config.service.port} " +
          "environment=${config.service.environment} instanceId=${config.service.instanceId} " +
          "otlpEndpoint=${config.telemetry.otlpEndpoint ?: "(disabled)"}"
    }

    val otel = GlobalOpenTelemetry.get()
    val tracer = otel.getTracer("brackets-service")
    val startupSpan = tracer.span("server.startup")
    val startupScope = startupSpan.makeCurrent()

    try {
      val server =
          Server.builder()
              .http(InetSocketAddress(config.service.host, config.service.port))
              .decorator(ArmeriaServerTelemetry.create(otel).newDecorator())
              .service(wrapService(BalanceServiceEndpoint()))
              .serverListener(
                  object : com.linecorp.armeria.server.ServerListenerAdapter() {
                    override fun serverStarted(server: Server) {
                      log.info { "Server started: activePorts=${server.activePorts()}" }
                      startupScope.close()
                      startupSpan.end()
                    }

                    override fun serverStopping(server: Server) {
                      log.info { "Server stopping: graceful drain started" }
                    }

                    override fun serverStopped(server: Server) {
                      log.info { "Server stopped." }
                    }
                  }
              )
              .build()
      server.closeOnJvmShutdown()
      server.start().join()
    } catch (e: Exception) {
      startupScope.close()
      startupSpan.end()
      throw e
    }
  }
}

fun main(vararg args: String) = BracketsService().main(args)

fun wrapService(
    bindableService: BindableService,
    vararg interceptors: ServerInterceptor,
): GrpcService {
  return GrpcService.builder().addService(bindableService).intercept(*interceptors).build()
}
