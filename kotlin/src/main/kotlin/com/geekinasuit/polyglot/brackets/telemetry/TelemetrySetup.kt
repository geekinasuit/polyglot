package com.geekinasuit.polyglot.brackets.telemetry

import com.geekinasuit.polyglot.brackets.config.TelemetryConfig
import com.geekinasuit.polyglot.brackets.config.effectiveLogEndpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.opentelemetry.GrpcOpenTelemetry
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.time.Duration

private val log = KotlinLogging.logger {}

fun initTelemetry(
  resource: Resource,
  config: TelemetryConfig,
): GrpcOpenTelemetry {
  val sdk =
    OpenTelemetrySdk.builder()
      .setTracerProvider(buildTracerProvider(resource, config))
      .setMeterProvider(buildMeterProvider(resource, config))
      .setLoggerProvider(buildLoggerProvider(resource, config))
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal()

  OpenTelemetryAppender.install(sdk)

  log.info {
    "OTel SDK initialized: otlpEndpoint=${config.otlpEndpoint ?: "(disabled)"} " +
      "loggingExport=${config.loggingExportEnabled}"
  }

  return GrpcOpenTelemetry.newBuilder().build()
}

private fun buildTracerProvider(resource: Resource, config: TelemetryConfig): SdkTracerProvider {
  val builder = SdkTracerProvider.builder().setResource(resource)
  val endpoint = config.otlpEndpoint
  if (config.tracingEnabled && endpoint != null) {
    val exporter = OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build()
    builder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
  }
  return builder.build()
}

private fun buildMeterProvider(resource: Resource, config: TelemetryConfig): SdkMeterProvider {
  val builder = SdkMeterProvider.builder().setResource(resource)
  val endpoint = config.otlpEndpoint
  if (config.metricsEnabled && endpoint != null) {
    val exporter = OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).build()
    val reader =
      PeriodicMetricReader.builder(exporter)
        .setInterval(Duration.ofSeconds(config.metricsExportIntervalSeconds))
        .build()
    builder.registerMetricReader(reader)
  }
  return builder.build()
}

private fun buildLoggerProvider(resource: Resource, config: TelemetryConfig): SdkLoggerProvider {
  val builder = SdkLoggerProvider.builder().setResource(resource)
  val endpoint = config.effectiveLogEndpoint
  if (config.loggingExportEnabled && endpoint != null) {
    val exporter = OtlpGrpcLogRecordExporter.builder().setEndpoint(endpoint).build()
    builder.addLogRecordProcessor(BatchLogRecordProcessor.builder(exporter).build())
  }
  return builder.build()
}
