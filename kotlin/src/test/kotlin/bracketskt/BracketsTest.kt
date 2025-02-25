package bracketskt

import com.geekinasuit.polyglot.example.protos.Something
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class BracketsTest {

  @Test
  fun testEmptySuccess() { 
    balancedBrackets("")
  }

  @Test
  fun testSimpleBracketsSuccess() {
    balancedBrackets("()")
  }

  @Test
  fun testSimpleBracketsFailRemainingOpen() {
    val err = assertThrows(BracketsNotBalancedException::class.java) { balancedBrackets("(()") }
    assertThat(err).hasMessageThat().contains("opening brackets without closing brackets found: [(]")
  }

  @Test
  fun testSimpleBracketsFailExtraClosed() {
    val err = assertThrows(BracketsNotBalancedException::class.java) { balancedBrackets("(()))") }
    assertThat(err).hasMessageThat().contains("closing bracket ) with no opening bracket at char 5")

  }

  @Test
  fun testSimpleBracketsFailMismatched() {
    val err = assertThrows(BracketsNotBalancedException::class.java) { balancedBrackets("((])") }
    assertThat(err).hasMessageThat().contains("closing bracket ] at char 3 mismatched with last opening bracket (")
  }

  @Test
  fun testComplexTextSuccess() {
    balancedBrackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget (to nest).).")
  }

  @Test
  fun testComplexMixedTextSuccess() {
    balancedBrackets("This is a bit of (albeit ridiculous) explanatory text. Don't (forget [to {nest}].).")
  }

  @Test
  fun testComplexTextMismatched() {
    val err = assertThrows(BracketsNotBalancedException::class.java) {
      balancedBrackets("This is a bit of (albeit ridiculous] explanatory text. Don't (forget (to nest).).")
    }
    assertThat(err).hasMessageThat().contains("closing bracket ] at char 36 mismatched with last opening bracket (")
  }
  @Test
  fun testFoo() {
    val actual: Something = foo()
    assertThat(actual.id).isEqualTo(2)
    assertThat(actual.name).isEqualTo("foo")
    assertThat(actual.labelsList).containsExactly("a", "b").inOrder()
  }
}
