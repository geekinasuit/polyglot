package com.geekinasuit.polyglot.brackets.telemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongCounterBuilder
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.LongHistogramBuilder
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessorBuilder
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReaderBuilder
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder
import io.opentelemetry.sdk.trace.export.SpanExporter

// ── Attributes / Resource ────────────────────────────────────────────────────

/** Creates [Attributes] with [block] configuring the [AttributesBuilder]. */
fun attributes(block: AttributesBuilder.() -> Unit): Attributes =
    Attributes.builder().apply(block).build()

/** Creates a [Resource] with [block] configuring its [AttributesBuilder]. */
fun resource(block: AttributesBuilder.() -> Unit): Resource = Resource.create(attributes(block))

/** Merges additional attributes into this [Resource], configured by [block]. */
fun Resource.merging(block: AttributesBuilder.() -> Unit): Resource = merge(resource(block))

// ── SDK providers ────────────────────────────────────────────────────────────

/**
 * Builds an [OpenTelemetrySdk] and registers it as the global, with [block] configuring the
 * builder.
 */
fun openTelemetrySdk(block: OpenTelemetrySdkBuilder.() -> Unit): OpenTelemetrySdk =
    OpenTelemetrySdk.builder().apply(block).buildAndRegisterGlobal()

/** Builds an [SdkTracerProvider] with [block] configuring the [SdkTracerProviderBuilder]. */
fun sdkTracerProvider(block: SdkTracerProviderBuilder.() -> Unit): SdkTracerProvider =
    SdkTracerProvider.builder().apply(block).build()

/** Builds an [SdkMeterProvider] with [block] configuring the [SdkMeterProviderBuilder]. */
fun sdkMeterProvider(block: SdkMeterProviderBuilder.() -> Unit): SdkMeterProvider =
    SdkMeterProvider.builder().apply(block).build()

/** Builds an [SdkLoggerProvider] with [block] configuring the [SdkLoggerProviderBuilder]. */
fun sdkLoggerProvider(block: SdkLoggerProviderBuilder.() -> Unit): SdkLoggerProvider =
    SdkLoggerProvider.builder().apply(block).build()

// ── Exporters ────────────────────────────────────────────────────────────────

/** Builds an [OtlpGrpcSpanExporter] with [block] configuring the builder. */
fun otlpSpanExporter(block: OtlpGrpcSpanExporterBuilder.() -> Unit): OtlpGrpcSpanExporter =
    OtlpGrpcSpanExporter.builder().apply(block).build()

/** Builds an [OtlpGrpcMetricExporter] with [block] configuring the builder. */
fun otlpMetricExporter(block: OtlpGrpcMetricExporterBuilder.() -> Unit): OtlpGrpcMetricExporter =
    OtlpGrpcMetricExporter.builder().apply(block).build()

/** Builds an [OtlpGrpcLogRecordExporter] with [block] configuring the builder. */
fun otlpLogExporter(block: OtlpGrpcLogRecordExporterBuilder.() -> Unit): OtlpGrpcLogRecordExporter =
    OtlpGrpcLogRecordExporter.builder().apply(block).build()

// ── Processors / readers ─────────────────────────────────────────────────────

/**
 * Builds a [BatchSpanProcessor] for [exporter] with [block] configuring the
 * [BatchSpanProcessorBuilder].
 */
fun batchSpanProcessor(
    exporter: SpanExporter,
    block: BatchSpanProcessorBuilder.() -> Unit = {},
): BatchSpanProcessor = BatchSpanProcessor.builder(exporter).apply(block).build()

/**
 * Builds a [PeriodicMetricReader] for [exporter] with [block] configuring the
 * [PeriodicMetricReaderBuilder].
 */
fun periodicMetricReader(
    exporter: MetricExporter,
    block: PeriodicMetricReaderBuilder.() -> Unit = {},
): PeriodicMetricReader = PeriodicMetricReader.builder(exporter).apply(block).build()

/**
 * Builds a [BatchLogRecordProcessor] for [exporter] with [block] configuring the
 * [BatchLogRecordProcessorBuilder].
 */
fun batchLogRecordProcessor(
    exporter: LogRecordExporter,
    block: BatchLogRecordProcessorBuilder.() -> Unit = {},
): BatchLogRecordProcessor = BatchLogRecordProcessor.builder(exporter).apply(block).build()

// ── Tracer / Meter ───────────────────────────────────────────────────────────

/** Builds and starts a [Span] with [block] configuring the [SpanBuilder]. */
fun Tracer.span(name: String, block: SpanBuilder.() -> Unit = {}): Span =
    spanBuilder(name).apply(block).startSpan()

/** Builds a [LongCounter] with [block] configuring the [LongCounterBuilder]. */
fun Meter.counter(name: String, block: LongCounterBuilder.() -> Unit = {}): LongCounter =
    counterBuilder(name).apply(block).build()

/** Builds a long-valued [LongHistogram] with [block] configuring the [LongHistogramBuilder]. */
fun Meter.longHistogram(name: String, block: LongHistogramBuilder.() -> Unit = {}): LongHistogram =
    histogramBuilder(name).ofLongs().apply(block).build()
