package com.geekinasuit.polyglot.brackets.db

import com.geekinasuit.polyglot.brackets.db.jooq.Tables
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Smoke test verifying JOOQ codegen produced the expected types for the bracket_pair table. */
class JooqCodegenSmokeTest {

  @Test
  fun bracketPairTableIsNotNull() {
    assertThat(Tables.BRACKET_PAIR).isNotNull()
  }

  @Test
  fun openCharFieldIsAccessible() {
    assertThat(Tables.BRACKET_PAIR.OPEN_CHAR).isNotNull()
  }

  @Test
  fun closeCharFieldIsAccessible() {
    assertThat(Tables.BRACKET_PAIR.CLOSE_CHAR).isNotNull()
  }

  @Test
  fun enabledFieldIsAccessible() {
    assertThat(Tables.BRACKET_PAIR.ENABLED).isNotNull()
  }
}
