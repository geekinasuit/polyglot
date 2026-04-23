package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.geekinasuit.polyglot.brackets.db.BracketsDbModule
import com.linecorp.armeria.server.Server
import dagger.BindsInstance
import dagger.Component

@ApplicationScope
@Component(
    modules =
        [
            TestApplicationGraphModule::class,
            BracketsDbModule::class,
            TestDatabaseModule::class,
            GrpcHandlersModule::class,
            InterceptorsModule::class,
            FakeTelemetryModule::class,
            ServerModule::class,
        ],
)
interface TestApplicationGraph : GrpcCallScopeGraph.Supplier {
  fun server(): Server

  @Component.Builder
  interface Builder {
    @BindsInstance fun config(config: ServiceAppConfig): Builder

    fun build(): TestApplicationGraph
  }

  companion object {
    fun builder(): Builder = DaggerTestApplicationGraph.builder()
  }
}
