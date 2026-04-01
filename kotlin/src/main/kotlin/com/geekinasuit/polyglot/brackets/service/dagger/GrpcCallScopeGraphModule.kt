package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.GrpcCallContext
import dagger.Module

@Module(includes = [GrpcCallContext.Module::class]) object GrpcCallScopeGraphModule
