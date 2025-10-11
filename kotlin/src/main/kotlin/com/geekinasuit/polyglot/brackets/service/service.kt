package com.geekinasuit.polyglot.brackets.service

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.grpc.GrpcService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.BindableService
import io.grpc.ServerInterceptor

private const val DEFAULT_HOST = "localhost"
private const val DEFAULT_PORT = 8888
private val log = KotlinLogging.logger {}

class BracketsService : CliktCommand(name = "brackets_service") {
  val host by option().default(DEFAULT_HOST).help("Service Hostname")
  val port: Int by option().int().default(DEFAULT_PORT).help("Service IP port number")

  override fun run() {
    println("brackets service starting...")
    val server = Server.builder().http(port).service(wrapService(BalanceServiceEndpoint())).build()
    server.closeOnJvmShutdown().thenRun { log.info { "Server has been stopped." } }
    server.start().join()
  }
}

fun main(vararg args: String) = BracketsService().main(args)

fun wrapService(
  bindableService: BindableService,
  vararg interceptors: ServerInterceptor,
): GrpcService {
  return GrpcService.builder().addService(bindableService).intercept(*interceptors).build()
}
