package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import dagger.Module
import dagger.Provides

/** Satisfies the GrpcCallScopeGraph.Supplier binding for the test component. */
@Module
object TestApplicationGraphModule {
  @Provides
  @ApplicationScope
  fun supplier(graph: TestApplicationGraph): GrpcCallScopeGraph.Supplier = graph
}
