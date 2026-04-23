package com.geekinasuit.polyglot.brackets.service

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.telemetry.counter
import com.geekinasuit.polyglot.brackets.telemetry.longHistogram
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.trace.Tracer
import javax.inject.Inject

/** Holds application-scoped OpenTelemetry instruments for the brackets service. */
@ApplicationScope
class BracketsServiceTelemetry @Inject constructor(otel: OpenTelemetry) {
  val tracer: Tracer = otel.getTracer("brackets-service")
  private val meter = otel.getMeter("brackets-service")
  val requestCounter: LongCounter =
      meter.counter("brackets.server.request.count") {
        setDescription("Number of bracket balance requests")
      }
  val latencyHistogram: LongHistogram =
      meter.longHistogram("brackets.server.request.duration.ms") {
        setDescription("Request duration in milliseconds")
      }
}
