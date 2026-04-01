package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import dagger.Module
import dagger.Provides
import io.grpc.opentelemetry.GrpcOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk

@Module
object FakeTelemetryModule {
  @Provides
  @ApplicationScope
  fun grpcOpenTelemetry(): GrpcOpenTelemetry =
      GrpcOpenTelemetry.newBuilder().sdk(OpenTelemetrySdk.builder().build()).build()

  @Provides @ApplicationScope fun openTelemetry(): OpenTelemetry = OpenTelemetry.noop()

  @Provides
  @ApplicationScope
  fun tracer(otel: OpenTelemetry): Tracer = otel.getTracer("brackets-service-test")
}
