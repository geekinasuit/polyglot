package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import dagger.Module
import dagger.Provides

/** Satisfies the GrpcCallScopeGraph.Supplier binding by providing the ApplicationGraph itself. */
@Module
object ApplicationGraphModule {
  @Provides
  @ApplicationScope
  fun supplier(graph: ApplicationGraph): GrpcCallScopeGraph.Supplier = graph
}
