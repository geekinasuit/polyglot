package com.geekinasuit.polyglot.brackets.service.dagger

import com.geekinasuit.daggergrpc.api.GrpcCallContext
import com.geekinasuit.daggergrpc.api.GrpcCallScope
import com.geekinasuit.polyglot.brackets.db.BracketPairRepository
import dagger.Module
import dagger.Provides

@Module(includes = [GrpcCallContext.Module::class])
object GrpcCallScopeGraphModule {
  @Provides
  @GrpcCallScope
  fun bracketPairs(repo: BracketPairRepository): Map<Char, Char> = repo.loadEnabledPairs()
}
