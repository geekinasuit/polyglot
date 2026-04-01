package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.config.ServiceAppConfig
import com.geekinasuit.polyglot.brackets.telemetry.initTelemetry
import com.geekinasuit.polyglot.brackets.telemetry.merging
import dagger.Module
import dagger.Provides
import io.grpc.opentelemetry.GrpcOpenTelemetry
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.resources.Resource

@Module
object TelemetryModule {
  @Provides
  @ApplicationScope
  fun grpcOpenTelemetry(config: ServiceAppConfig): GrpcOpenTelemetry {
    val resource =
        Resource.getDefault().merging {
          put("service.name", "brackets-service")
          put("deployment.environment", config.service.environment)
          put("service.instance.id", config.service.instanceId)
        }
    return initTelemetry(resource, config.telemetry)
  }

  /**
   * initTelemetry() registers the SDK as the global OpenTelemetry instance as a side effect. This
   * binding depends on grpcOpenTelemetry() to guarantee init has run before the global is read.
   */
  @Provides
  @ApplicationScope
  @Suppress("UNUSED_PARAMETER")
  fun openTelemetry(grpcOtel: GrpcOpenTelemetry): OpenTelemetry = GlobalOpenTelemetry.get()

  @Provides
  @ApplicationScope
  fun tracer(otel: OpenTelemetry): Tracer = otel.getTracer("brackets-service")
}
