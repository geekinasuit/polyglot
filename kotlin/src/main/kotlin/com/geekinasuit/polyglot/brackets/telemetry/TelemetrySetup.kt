package com.geekinasuit.polyglot.brackets.telemetry

import com.geekinasuit.polyglot.brackets.config.TelemetryConfig
import com.geekinasuit.polyglot.brackets.config.effectiveLogEndpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.opentelemetry.GrpcOpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import java.time.Duration

private val log = KotlinLogging.logger {}

fun initTelemetry(
    resource: Resource,
    config: TelemetryConfig,
): GrpcOpenTelemetry {
  val sdk = openTelemetrySdk {
    setTracerProvider(buildTracerProvider(resource, config))
    setMeterProvider(buildMeterProvider(resource, config))
    setLoggerProvider(buildLoggerProvider(resource, config))
    setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
  }

  OpenTelemetryAppender.install(sdk)

  log.info {
    "OTel SDK initialized: otlpEndpoint=${config.otlpEndpoint ?: "(disabled)"} " +
        "loggingExport=${config.loggingExportEnabled}"
  }

  return GrpcOpenTelemetry.newBuilder().sdk(sdk).build()
}

private fun buildTracerProvider(resource: Resource, config: TelemetryConfig): SdkTracerProvider =
    sdkTracerProvider {
      setResource(resource)
      if (config.tracingEnabled && config.otlpEndpoint != null) {
        addSpanProcessor(batchSpanProcessor(otlpSpanExporter { setEndpoint(config.otlpEndpoint) }))
      }
    }

private fun buildMeterProvider(resource: Resource, config: TelemetryConfig): SdkMeterProvider =
    sdkMeterProvider {
      setResource(resource)
      if (config.metricsEnabled && config.otlpEndpoint != null) {
        registerMetricReader(
            periodicMetricReader(otlpMetricExporter { setEndpoint(config.otlpEndpoint) }) {
              setInterval(Duration.ofSeconds(config.metricsExportIntervalSeconds))
            }
        )
      }
    }

private fun buildLoggerProvider(resource: Resource, config: TelemetryConfig): SdkLoggerProvider =
    sdkLoggerProvider {
      setResource(resource)
      if (config.loggingExportEnabled && config.effectiveLogEndpoint != null) {
        addLogRecordProcessor(
            batchLogRecordProcessor(otlpLogExporter { setEndpoint(config.effectiveLogEndpoint) })
        )
      }
    }
