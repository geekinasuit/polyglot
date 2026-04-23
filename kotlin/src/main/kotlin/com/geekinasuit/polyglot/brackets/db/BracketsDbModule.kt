package com.geekinasuit.polyglot.brackets.db

import com.geekinasuit.daggergrpc.api.ApplicationScope
import dagger.Binds
import dagger.Module

@Module
abstract class BracketsDbModule {
  @Binds
  @ApplicationScope
  abstract fun bindBracketPairRepository(impl: BracketPairRepositoryImpl): BracketPairRepository
}
