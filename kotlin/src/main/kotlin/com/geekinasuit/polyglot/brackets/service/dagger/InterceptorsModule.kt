package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.GrpcCallContext
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import io.grpc.ServerInterceptor

/**
 * Declares server-level gRPC interceptors. Add @Provides @IntoSet methods here for each
 * interceptor. The Set<ServerInterceptor> is consumed by ServerModule.server().
 */
@Module
object InterceptorsModule {
  @Provides @IntoSet fun callContextInterceptor(): ServerInterceptor = GrpcCallContext.Interceptor()
}
