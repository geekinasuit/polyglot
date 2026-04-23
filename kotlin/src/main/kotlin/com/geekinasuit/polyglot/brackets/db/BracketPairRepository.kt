package com.geekinasuit.polyglot.brackets.db

import com.geekinasuit.daggergrpc.api.ApplicationScope
import com.geekinasuit.polyglot.brackets.db.jooq.Tables.BRACKET_PAIR
import javax.inject.Inject
import org.jooq.DSLContext

interface BracketPairRepository {
  fun loadEnabledPairs(): Map<Char, Char>
}

@ApplicationScope
class BracketPairRepositoryImpl @Inject constructor(private val dsl: DSLContext) :
    BracketPairRepository {
  override fun loadEnabledPairs(): Map<Char, Char> =
      dsl
          .selectFrom(BRACKET_PAIR)
          .where(BRACKET_PAIR.ENABLED.isTrue)
          .fetch()
          .associate { record -> record[BRACKET_PAIR.CLOSE_CHAR]!![0] to record[BRACKET_PAIR.OPEN_CHAR]!![0] }
}
