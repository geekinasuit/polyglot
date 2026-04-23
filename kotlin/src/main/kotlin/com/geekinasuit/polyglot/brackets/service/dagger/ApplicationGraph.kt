package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.geekinasuit.polyglot.brackets.db.BracketsDbModule
import com.linecorp.armeria.server.Server
import dagger.BindsInstance
import dagger.Component
import io.opentelemetry.api.trace.Tracer

@ApplicationScope
@Component(
    modules =
        [
            ApplicationGraphModule::class,
            BracketsDbModule::class,
            DatabaseModule::class,
            GrpcHandlersModule::class,
            InterceptorsModule::class,
            TelemetryModule::class,
            ServerModule::class,
        ],
)
interface ApplicationGraph : GrpcCallScopeGraph.Supplier {
  fun server(): Server

  fun tracer(): Tracer

  @Component.Builder
  interface Builder {
    @BindsInstance fun config(config: ServiceAppConfig): Builder

    fun build(): ApplicationGraph
  }

  companion object {
    fun builder(): Builder = DaggerApplicationGraph.builder()
  }
}
