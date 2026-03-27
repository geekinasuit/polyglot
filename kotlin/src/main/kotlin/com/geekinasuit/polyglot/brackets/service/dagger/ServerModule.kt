package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.daggergrpc.armeria.wrapService
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.ServerListenerAdapter
import dagger.Module
import dagger.Provides
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.BindableService
import io.grpc.ServerInterceptor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaServerTelemetry
import java.net.InetSocketAddress

private val log = KotlinLogging.logger {}

@Module
object ServerModule {
  @Provides
  @ApplicationScope
  fun server(
      services: Set<@JvmSuppressWildcards BindableService>,
      interceptors: Set<@JvmSuppressWildcards ServerInterceptor>,
      otel: OpenTelemetry,
      config: ServiceAppConfig,
  ): Server =
      Server.builder()
          .http(InetSocketAddress(config.service.host, config.service.port))
          .decorator(ArmeriaServerTelemetry.create(otel).newDecorator())
          .apply { services.forEach { service(wrapService(it, *interceptors.toTypedArray())) } }
          .serverListener(
              object : ServerListenerAdapter() {
                override fun serverStarted(server: Server) {
                  log.info { "Server started: activePorts=${server.activePorts()}" }
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
}
